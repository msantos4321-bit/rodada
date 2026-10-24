-- Rodada: grupo de amigos (chat, ruta de la semana, ¿quién va?, mapa en vivo, peligros, lugares, kilómetros del mes).
-- Vive en el mismo proyecto de Supabase que CineCasa y Vista, con tablas propias que empiezan con rd_.
-- Las tablas no se leen directo: todo pasa por funciones rd_* (igual que cc_* en CineCasa).
-- Se puede volver a correr sin romper nada.

create extension if not exists pgcrypto;

create table if not exists rd_members (
  id uuid primary key,
  name text not null default '',
  color text not null default '#D9480F',
  bike text not null default '',
  created_at timestamptz not null default now(),
  seen_at timestamptz not null default now()
);

create table if not exists rd_groups (
  id uuid primary key default gen_random_uuid(),
  code text unique not null,
  name text not null,
  home_lat double precision,
  home_lon double precision,
  created_by uuid references rd_members(id) on delete set null,
  created_at timestamptz not null default now()
);

create table if not exists rd_group_members (
  group_id uuid references rd_groups(id) on delete cascade,
  member_id uuid references rd_members(id) on delete cascade,
  role text not null default 'miembro',
  joined_at timestamptz not null default now(),
  primary key (group_id, member_id)
);

create table if not exists rd_messages (
  id bigserial primary key,
  group_id uuid references rd_groups(id) on delete cascade,
  member_id uuid references rd_members(id) on delete set null,
  kind text not null default 'text',
  body text not null default '',
  data jsonb,
  created_at timestamptz not null default now()
);
create index if not exists rd_messages_group on rd_messages (group_id, id desc);

create table if not exists rd_catalog (
  id serial primary key,
  name text not null unique,
  kind text not null default 'curvas',
  km int not null default 0,
  description text not null default '',
  waypoints jsonb not null,
  needs_border boolean not null default false,
  lat double precision,
  lon double precision
);

create table if not exists rd_weekly (
  group_id uuid references rd_groups(id) on delete cascade,
  week text not null,
  route jsonb not null,
  start_at timestamptz,
  meet text not null default '',
  meet_lat double precision,
  meet_lon double precision,
  set_by uuid,
  primary key (group_id, week)
);

create table if not exists rd_rsvp (
  group_id uuid references rd_groups(id) on delete cascade,
  week text not null,
  member_id uuid references rd_members(id) on delete cascade,
  answer text not null,
  updated_at timestamptz not null default now(),
  primary key (group_id, week, member_id)
);

create table if not exists rd_live (
  group_id uuid references rd_groups(id) on delete cascade,
  member_id uuid references rd_members(id) on delete cascade,
  lat double precision not null,
  lon double precision not null,
  speed real not null default 0,
  heading real not null default 0,
  battery int not null default -1,
  status text not null default 'rodando',
  updated_at timestamptz not null default now(),
  primary key (group_id, member_id)
);

create table if not exists rd_hazards (
  id bigserial primary key,
  group_id uuid references rd_groups(id) on delete cascade,
  member_id uuid references rd_members(id) on delete set null,
  kind text not null,
  lat double precision not null,
  lon double precision not null,
  note text not null default '',
  created_at timestamptz not null default now(),
  expires_at timestamptz not null default now() + interval '7 days'
);

create table if not exists rd_places (
  id bigserial primary key,
  group_id uuid references rd_groups(id) on delete cascade,
  member_id uuid references rd_members(id) on delete set null,
  kind text not null,
  name text not null,
  lat double precision not null,
  lon double precision not null,
  note text not null default '',
  created_at timestamptz not null default now()
);

create table if not exists rd_km (
  group_id uuid references rd_groups(id) on delete cascade,
  member_id uuid references rd_members(id) on delete cascade,
  month text not null,
  km numeric not null default 0,
  rides int not null default 0,
  primary key (group_id, member_id, month)
);

alter table rd_members enable row level security;
alter table rd_groups enable row level security;
alter table rd_group_members enable row level security;
alter table rd_messages enable row level security;
alter table rd_catalog enable row level security;
alter table rd_weekly enable row level security;
alter table rd_rsvp enable row level security;
alter table rd_live enable row level security;
alter table rd_hazards enable row level security;
alter table rd_places enable row level security;
alter table rd_km enable row level security;

-- ---------- Ayudantes ----------
-- Distancia aproximada en km entre dos puntos
create or replace function rd__dist(a_lat double precision, a_lon double precision, b_lat double precision, b_lon double precision) returns double precision
language sql immutable as $$
  select case when a_lat is null or b_lat is null then 0 else
    6371 * 2 * asin(sqrt(power(sin(radians(b_lat - a_lat) / 2), 2) + cos(radians(a_lat)) * cos(radians(b_lat)) * power(sin(radians(b_lon - a_lon) / 2), 2))) end
$$;

create or replace function rd__in(p_me uuid, p_group uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from rd_group_members where group_id = p_group and member_id = p_me)
$$;

create or replace function rd__week(p_t timestamptz default now()) returns text
language sql stable as $$
  select to_char(p_t at time zone 'America/Tijuana', 'IYYY-IW')
$$;

create or replace function rd__new_code() returns text
language plpgsql volatile as $$
declare c text; abc text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
begin
  loop
    c := '';
    for i in 1..6 loop c := c || substr(abc, 1 + floor(random() * length(abc))::int, 1); end loop;
    exit when not exists (select 1 from rd_groups where code = c);
  end loop;
  return c;
end $$;

create or replace function rd__group_json(p_group uuid) returns jsonb
language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'id', g.id, 'code', g.code, 'name', g.name, 'home_lat', g.home_lat, 'home_lon', g.home_lon,
    'members', coalesce((
      select jsonb_agg(jsonb_build_object('id', m.id, 'name', m.name, 'color', m.color, 'bike', m.bike, 'role', gm.role) order by gm.joined_at)
      from rd_group_members gm join rd_members m on m.id = gm.member_id where gm.group_id = g.id), '[]'::jsonb))
  from rd_groups g where g.id = p_group
$$;

-- ---------- Perfil ----------
create or replace function rd_register(p_me uuid, p_name text, p_color text, p_bike text) returns text
language sql volatile security definer set search_path = public as $$
  insert into rd_members (id, name, color, bike) values (p_me, left(coalesce(p_name, ''), 40), coalesce(nullif(p_color, ''), '#D9480F'), left(coalesce(p_bike, ''), 60))
  on conflict (id) do update set name = excluded.name, color = excluded.color, bike = excluded.bike, seen_at = now();
  select 'ok';
$$;

-- ---------- Grupos ----------
create or replace function rd_group_create(p_me uuid, p_name text, p_lat double precision, p_lon double precision) returns jsonb
language plpgsql volatile security definer set search_path = public as $$
declare g uuid;
begin
  if not exists (select 1 from rd_members where id = p_me) then return jsonb_build_object('error', 'no_profile'); end if;
  if (select count(*) from rd_group_members where member_id = p_me) >= 5 then return jsonb_build_object('error', 'too_many'); end if;
  insert into rd_groups (code, name, home_lat, home_lon, created_by) values (rd__new_code(), left(p_name, 50), p_lat, p_lon, p_me) returning id into g;
  insert into rd_group_members (group_id, member_id, role) values (g, p_me, 'admin');
  insert into rd_messages (group_id, member_id, kind, body) values (g, p_me, 'system', 'Grupo creado');
  return rd__group_json(g);
end $$;

create or replace function rd_group_join(p_me uuid, p_code text) returns jsonb
language plpgsql volatile security definer set search_path = public as $$
declare g uuid; n text;
begin
  if not exists (select 1 from rd_members where id = p_me) then return jsonb_build_object('error', 'no_profile'); end if;
  select id into g from rd_groups where code = upper(replace(trim(p_code), ' ', ''));
  if g is null then return jsonb_build_object('error', 'unknown'); end if;
  if (select count(*) from rd_group_members where group_id = g) >= 60 then return jsonb_build_object('error', 'full'); end if;
  if not rd__in(p_me, g) then
    insert into rd_group_members (group_id, member_id) values (g, p_me);
    select name into n from rd_members where id = p_me;
    insert into rd_messages (group_id, member_id, kind, body) values (g, p_me, 'system', coalesce(n, 'Alguien') || ' se unió al grupo');
  end if;
  return rd__group_json(g);
end $$;

create or replace function rd_group_leave(p_me uuid, p_group uuid) returns text
language sql volatile security definer set search_path = public as $$
  delete from rd_group_members where group_id = p_group and member_id = p_me;
  delete from rd_live where group_id = p_group and member_id = p_me;
  select 'ok';
$$;

create or replace function rd_groups_mine(p_me uuid) returns jsonb
language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(rd__group_json(gm.group_id) order by gm.joined_at), '[]'::jsonb)
  from rd_group_members gm where gm.member_id = p_me
$$;

create or replace function rd_group_home(p_me uuid, p_group uuid, p_lat double precision, p_lon double precision) returns text
language plpgsql volatile security definer set search_path = public as $$
begin
  if not rd__in(p_me, p_group) then return 'not_member'; end if;
  update rd_groups set home_lat = p_lat, home_lon = p_lon where id = p_group;
  return 'ok';
end $$;

-- ---------- Chat ----------
create or replace function rd_msg_send(p_me uuid, p_group uuid, p_kind text, p_body text, p_data jsonb default null) returns bigint
language plpgsql volatile security definer set search_path = public as $$
declare i bigint;
begin
  if not rd__in(p_me, p_group) then return -1; end if;
  if (select count(*) from rd_messages where member_id = p_me and created_at > now() - interval '1 minute') > 30 then return -2; end if;
  insert into rd_messages (group_id, member_id, kind, body, data)
  values (p_group, p_me, coalesce(nullif(p_kind, ''), 'text'), left(coalesce(p_body, ''), 1000), p_data) returning id into i;
  return i;
end $$;

create or replace function rd_msg_list(p_me uuid, p_group uuid, p_after bigint default 0) returns jsonb
language sql stable security definer set search_path = public as $$
  select case when not rd__in(p_me, p_group) then '[]'::jsonb else coalesce((
    select jsonb_agg(x order by (x->>'id')::bigint) from (
      select jsonb_build_object('id', m.id, 'member', m.member_id, 'name', coalesce(u.name, ''), 'color', coalesce(u.color, '#6E6E73'),
        'kind', m.kind, 'body', m.body, 'data', m.data, 'at', m.created_at) x
      from rd_messages m left join rd_members u on u.id = m.member_id
      where m.group_id = p_group and m.id > coalesce(p_after, 0)
      order by m.id desc limit 150) s), '[]'::jsonb) end
$$;

-- Mensajes nuevos de todos mis grupos (para avisos en el celular)
create or replace function rd_inbox(p_me uuid, p_after bigint) returns jsonb
language sql volatile security definer set search_path = public as $$
  update rd_members set seen_at = now() where id = p_me;
  select coalesce(jsonb_agg(jsonb_build_object('id', m.id, 'group', m.group_id, 'group_name', g.name, 'name', coalesce(u.name, ''),
    'kind', m.kind, 'body', m.body, 'data', m.data, 'at', m.created_at) order by m.id), '[]'::jsonb)
  from rd_messages m
  join rd_group_members gm on gm.group_id = m.group_id and gm.member_id = p_me
  join rd_groups g on g.id = m.group_id
  left join rd_members u on u.id = m.member_id
  where m.id > coalesce(p_after, 0) and m.member_id is distinct from p_me and m.created_at > now() - interval '3 days';
$$;

-- ---------- Ruta de la semana ----------
-- Si nadie ha elegido ruta esta semana, Rodada propone una del catálogo cerca del grupo,
-- distinta a las de las últimas semanas, y la misma para todos.
create or replace function rd_weekly_get(p_me uuid, p_group uuid) returns jsonb
language plpgsql volatile security definer set search_path = public as $$
declare w text := rd__week(); r rd_weekly; c rd_catalog; g rd_groups; recent text[]; sat timestamptz;
begin
  if not rd__in(p_me, p_group) then return jsonb_build_object('error', 'not_member'); end if;
  select * into r from rd_weekly where group_id = p_group and week = w;
  if not found then
    select * into g from rd_groups where id = p_group;
    select coalesce(array_agg(route->>'name'), '{}') into recent from (
      select route from rd_weekly where group_id = p_group order by week desc limit 8) s;
    select * into c from rd_catalog
      where not (name = any(recent))
        and (g.home_lat is null or rd__dist(lat, lon, g.home_lat, g.home_lon) < 260)
      order by md5(p_group::text || w || name) limit 1;
    if not found then
      select * into c from rd_catalog order by md5(p_group::text || w || name) limit 1;
    end if;
    -- Próximo sábado a las 8:00 (hora de Tijuana)
    sat := ((date_trunc('week', now() at time zone 'America/Tijuana') + interval '5 days 8 hours') at time zone 'America/Tijuana');
    if sat < now() then sat := sat + interval '7 days'; end if;
    insert into rd_weekly (group_id, week, route, start_at, meet)
    values (p_group, w, jsonb_build_object('name', c.name, 'kind', c.kind, 'km', c.km, 'description', c.description,
      'waypoints', c.waypoints, 'needs_border', c.needs_border, 'source', 'rodada'), sat, '')
    on conflict (group_id, week) do nothing;
    select * into r from rd_weekly where group_id = p_group and week = w;
  end if;
  return jsonb_build_object('week', r.week, 'route', r.route, 'start_at', r.start_at, 'meet', r.meet,
    'meet_lat', r.meet_lat, 'meet_lon', r.meet_lon,
    'mine', (select answer from rd_rsvp where group_id = p_group and week = w and member_id = p_me),
    'answers', coalesce((select jsonb_agg(jsonb_build_object('member', x.member_id, 'name', m.name, 'color', m.color, 'answer', x.answer))
       from rd_rsvp x join rd_members m on m.id = x.member_id where x.group_id = p_group and x.week = w), '[]'::jsonb));
end $$;

-- Cambiar la ruta, la hora o el punto de salida de esta semana
create or replace function rd_weekly_set(p_me uuid, p_group uuid, p_route jsonb, p_start timestamptz, p_meet text, p_meet_lat double precision, p_meet_lon double precision) returns text
language plpgsql volatile security definer set search_path = public as $$
declare w text := rd__week(); n text; msg text;
begin
  if not rd__in(p_me, p_group) then return 'not_member'; end if;
  perform rd_weekly_get(p_me, p_group);
  update rd_weekly set
    route = coalesce(p_route, route), start_at = coalesce(p_start, start_at),
    meet = coalesce(p_meet, meet), meet_lat = coalesce(p_meet_lat, meet_lat), meet_lon = coalesce(p_meet_lon, meet_lon), set_by = p_me
  where group_id = p_group and week = w;
  select name into n from rd_members where id = p_me;
  msg := coalesce(n, 'Alguien') || case
    when p_route is not null then ' propuso otra ruta: ' || coalesce(p_route->>'name', '')
    when p_start is not null then ' cambió la hora de salida'
    else ' cambió el punto de salida: ' || coalesce(p_meet, '') end;
  insert into rd_messages (group_id, member_id, kind, body, data) values (p_group, p_me, 'weekly', msg, null);
  return 'ok';
end $$;

create or replace function rd_rsvp_set(p_me uuid, p_group uuid, p_answer text) returns text
language plpgsql volatile security definer set search_path = public as $$
declare w text := rd__week(); n text; prev text;
begin
  if not rd__in(p_me, p_group) then return 'not_member'; end if;
  select answer into prev from rd_rsvp where group_id = p_group and week = w and member_id = p_me;
  insert into rd_rsvp (group_id, week, member_id, answer) values (p_group, w, p_me, p_answer)
  on conflict (group_id, week, member_id) do update set answer = excluded.answer, updated_at = now();
  if prev is distinct from p_answer then
    select name into n from rd_members where id = p_me;
    insert into rd_messages (group_id, member_id, kind, body) values (p_group, p_me, 'rsvp',
      coalesce(n, 'Alguien') || case p_answer when 'voy' then ' va a la rodada' when 'quiza' then ' tal vez va' else ' no puede ir' end);
  end if;
  return 'ok';
end $$;

create or replace function rd_catalog_list(p_me uuid, p_lat double precision, p_lon double precision) returns jsonb
language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(jsonb_build_object('id', id, 'name', name, 'kind', kind, 'km', km, 'description', description,
    'waypoints', waypoints, 'needs_border', needs_border,
    'dist', case when p_lat is null then null else round(rd__dist(lat, lon, p_lat, p_lon)::numeric) end)
    order by case when p_lat is null then 0 else rd__dist(lat, lon, p_lat, p_lon) end), '[]'::jsonb)
  from rd_catalog
$$;

-- ---------- Mapa en vivo ----------
create or replace function rd_live_update(p_me uuid, p_group uuid, p_lat double precision, p_lon double precision,
  p_speed real, p_heading real, p_battery int, p_status text) returns text
language plpgsql volatile security definer set search_path = public as $$
begin
  if not rd__in(p_me, p_group) then return 'not_member'; end if;
  insert into rd_live (group_id, member_id, lat, lon, speed, heading, battery, status, updated_at)
  values (p_group, p_me, p_lat, p_lon, p_speed, p_heading, p_battery, coalesce(p_status, 'rodando'), now())
  on conflict (group_id, member_id) do update set lat = excluded.lat, lon = excluded.lon, speed = excluded.speed,
    heading = excluded.heading, battery = excluded.battery, status = excluded.status, updated_at = now();
  return 'ok';
end $$;

create or replace function rd_live_stop(p_me uuid, p_group uuid) returns text
language sql volatile security definer set search_path = public as $$
  delete from rd_live where group_id = p_group and member_id = p_me;
  select 'ok';
$$;

create or replace function rd_live_list(p_me uuid, p_group uuid) returns jsonb
language sql stable security definer set search_path = public as $$
  select case when not rd__in(p_me, p_group) then '[]'::jsonb else coalesce((
    select jsonb_agg(jsonb_build_object('member', l.member_id, 'name', m.name, 'color', m.color, 'lat', l.lat, 'lon', l.lon,
      'speed', l.speed, 'heading', l.heading, 'battery', l.battery, 'status', l.status,
      'age', extract(epoch from now() - l.updated_at)::int))
    from rd_live l join rd_members m on m.id = l.member_id
    where l.group_id = p_group and l.updated_at > now() - interval '45 minutes'), '[]'::jsonb) end
$$;

-- ---------- Peligros y lugares ----------
create or replace function rd_hazard_add(p_me uuid, p_group uuid, p_kind text, p_lat double precision, p_lon double precision, p_note text) returns bigint
language plpgsql volatile security definer set search_path = public as $$
declare i bigint; n text;
begin
  if not rd__in(p_me, p_group) then return -1; end if;
  insert into rd_hazards (group_id, member_id, kind, lat, lon, note, expires_at)
  values (p_group, p_me, p_kind, p_lat, p_lon, left(coalesce(p_note, ''), 200),
    now() + case when p_kind in ('reten', 'accidente', 'trafico') then interval '6 hours' else interval '14 days' end)
  returning id into i;
  select name into n from rd_members where id = p_me;
  insert into rd_messages (group_id, member_id, kind, body, data) values (p_group, p_me, 'hazard',
    coalesce(n, 'Alguien') || ' marcó un peligro: ' || p_kind, jsonb_build_object('lat', p_lat, 'lon', p_lon, 'kind', p_kind));
  return i;
end $$;

create or replace function rd_hazard_list(p_me uuid, p_group uuid) returns jsonb
language sql stable security definer set search_path = public as $$
  select case when not rd__in(p_me, p_group) then '[]'::jsonb else coalesce((
    select jsonb_agg(jsonb_build_object('id', h.id, 'kind', h.kind, 'lat', h.lat, 'lon', h.lon, 'note', h.note,
      'name', coalesce(m.name, ''), 'at', h.created_at))
    from rd_hazards h left join rd_members m on m.id = h.member_id
    where h.group_id = p_group and h.expires_at > now()), '[]'::jsonb) end
$$;

create or replace function rd_hazard_clear(p_me uuid, p_id bigint) returns text
language sql volatile security definer set search_path = public as $$
  update rd_hazards set expires_at = now() where id = p_id and rd__in(p_me, group_id);
  select 'ok';
$$;

create or replace function rd_place_add(p_me uuid, p_group uuid, p_kind text, p_name text, p_lat double precision, p_lon double precision, p_note text) returns bigint
language plpgsql volatile security definer set search_path = public as $$
declare i bigint;
begin
  if not rd__in(p_me, p_group) then return -1; end if;
  insert into rd_places (group_id, member_id, kind, name, lat, lon, note)
  values (p_group, p_me, p_kind, left(p_name, 80), p_lat, p_lon, left(coalesce(p_note, ''), 300)) returning id into i;
  return i;
end $$;

create or replace function rd_place_list(p_me uuid, p_group uuid) returns jsonb
language sql stable security definer set search_path = public as $$
  select case when not rd__in(p_me, p_group) then '[]'::jsonb else coalesce((
    select jsonb_agg(jsonb_build_object('id', p.id, 'kind', p.kind, 'name', p.name, 'lat', p.lat, 'lon', p.lon, 'note', p.note,
      'by', coalesce(m.name, '')) order by p.created_at desc)
    from rd_places p left join rd_members m on m.id = p.member_id where p.group_id = p_group), '[]'::jsonb) end
$$;

create or replace function rd_place_delete(p_me uuid, p_id bigint) returns text
language sql volatile security definer set search_path = public as $$
  delete from rd_places where id = p_id and rd__in(p_me, group_id);
  select 'ok';
$$;

-- ---------- Kilómetros del mes ----------
create or replace function rd_km_add(p_me uuid, p_km numeric) returns text
language sql volatile security definer set search_path = public as $$
  insert into rd_km (group_id, member_id, month, km, rides)
  select gm.group_id, p_me, to_char(now() at time zone 'America/Tijuana', 'YYYY-MM'), greatest(0, least(p_km, 2000)), 1
  from rd_group_members gm where gm.member_id = p_me
  on conflict (group_id, member_id, month) do update set km = rd_km.km + excluded.km, rides = rd_km.rides + 1;
  select 'ok';
$$;

create or replace function rd_km_board(p_me uuid, p_group uuid) returns jsonb
language sql stable security definer set search_path = public as $$
  select case when not rd__in(p_me, p_group) then '[]'::jsonb else coalesce((
    select jsonb_agg(jsonb_build_object('member', m.id, 'name', m.name, 'color', m.color, 'km', round(coalesce(k.km, 0)), 'rides', coalesce(k.rides, 0))
      order by coalesce(k.km, 0) desc)
    from rd_group_members gm join rd_members m on m.id = gm.member_id
    left join rd_km k on k.group_id = gm.group_id and k.member_id = gm.member_id and k.month = to_char(now() at time zone 'America/Tijuana', 'YYYY-MM')
    where gm.group_id = p_group), '[]'::jsonb) end
$$;


-- Las funciones rd_* se pueden llamar con la llave pública (las tablas no)
grant execute on function rd_register, rd_group_create, rd_group_join, rd_group_leave, rd_groups_mine, rd_group_home,
  rd_msg_send, rd_msg_list, rd_inbox, rd_weekly_get, rd_weekly_set, rd_rsvp_set, rd_catalog_list,
  rd_live_update, rd_live_stop, rd_live_list, rd_hazard_add, rd_hazard_list, rd_hazard_clear,
  rd_place_add, rd_place_list, rd_place_delete, rd_km_add, rd_km_board to anon;

-- Rodada en el Centro de control (apagado: la app no pide código mientras sea para el grupo de amigos)
update biz_settings set data = jsonb_set(data, '{apps,rodada}', jsonb_build_object(
  'name', 'Rodada', 'prefix', 'RO', 'auto_trial', 'off', 'trial_days', 14, 'trial_devices', 1,
  'download_url', 'https://github.com/msantos4321-bit/rodada/releases/latest/download/Rodada.apk'), true)
where id = 1 and data ? 'apps' and not (data->'apps' ? 'rodada');

-- ---------- Catálogo de rutas (Baja California y San Diego) ----------
-- Puntos de paso [lat, lon]; la ruta sale del punto de reunión del grupo, pasa por ellos y regresa.
insert into rd_catalog (name, kind, km, description, waypoints, needs_border, lat, lon) values
 ('La Rumorosa por Tecate', 'curvas', 230, 'Subida por la libre a Tecate y El Hongo hasta el mirador de La Rumorosa. Curvas de montaña y paisaje de piedras gigantes.',
  '[[32.5764,-116.6258],[32.5070,-116.2830],[32.5517,-116.0533]]', false, 32.5764, -116.6258),
 ('Ruta del Vino por Tecate', 'curvas', 200, 'Tecate, Valle de las Palmas y Valle de Guadalupe; regreso por la costa. Viñedos y curvas suaves.',
  '[[32.5764,-116.6258],[32.3667,-116.6167],[32.0974,-116.5730],[32.0950,-116.8636]]', false, 32.5764, -116.6258),
 ('Valle de Guadalupe por la costa', 'curvas', 210, 'Baja por la costa hasta La Misión, entra al Valle de Guadalupe y regresa por Tecate.',
  '[[32.0950,-116.8636],[32.0974,-116.5730],[32.5764,-116.6258]]', false, 32.0950, -116.8636),
 ('Escénica a Ensenada y La Bufadora', 'costa', 250, 'Carretera escénica junto al mar hasta Ensenada y la península de Punta Banda hasta La Bufadora.',
  '[[32.2497,-116.9431],[31.8667,-116.6167],[31.7189,-116.5703],[31.7228,-116.7190]]', false, 32.2497, -116.9431),
 ('Rosarito, Popotla y Puerto Nuevo', 'costa', 90, 'Paseo corto por la costa para comer langosta o mariscos.',
  '[[32.3661,-117.0618],[32.2925,-117.0312],[32.2497,-116.9431]]', false, 32.3661, -117.0618),
 ('Valle de las Palmas por la libre', 'curvas', 150, 'Vuelta de medio día por caminos rurales entre Tijuana, Valle de las Palmas y Tecate.',
  '[[32.3667,-116.6167],[32.5764,-116.6258]]', false, 32.3667, -116.6167),
 ('Dunas de Cantamar', 'terraceria', 110, 'Tramo de arena y terracería ligera junto al mar. Para motos de doble propósito.',
  '[[32.2295,-116.9264]]', false, 32.2295, -116.9264),
 ('Ojos Negros y Laguna Hanson', 'terraceria', 420, 'Día completo: Ensenada, Ojos Negros y la sierra de Juárez por terracería hasta Laguna Hanson. Lleva gasolina extra.',
  '[[31.8667,-116.6167],[31.9136,-116.2650],[32.0444,-115.9083]]', false, 31.8667, -116.6167),
 ('Santo Tomás y sus viñedos', 'carretera', 330, 'Al sur de Ensenada por la carretera transpeninsular hasta el valle de Santo Tomás.',
  '[[31.8667,-116.6167],[31.5583,-116.4128]]', false, 31.8667, -116.6167),
 ('San Felipe por Valle de la Trinidad', 'viaje', 520, 'Viaje de fin de semana: Ensenada, la sierra y el desierto hasta el Mar de Cortés.',
  '[[31.8667,-116.6167],[31.4040,-115.7170],[31.0242,-114.8411]]', false, 31.8667, -116.6167),
 ('Mexicali por La Rumorosa', 'viaje', 380, 'Cruce de la sierra y bajada por la Cuesta de La Rumorosa hasta Mexicali. Mucho calor en verano.',
  '[[32.5764,-116.6258],[32.5517,-116.0533],[32.6245,-115.4523]]', false, 32.5764, -116.6258),
 ('Julian, California', 'curvas', 230, 'Cruzando por Otay: carretera 94 y montañas hasta Julian (pay de manzana). Requiere pasaporte o visa.',
  '[[32.5497,-116.9389],[33.0786,-116.6021]]', true, 32.5497, -116.9389),
 ('Palomar Mountain, California', 'curvas', 300, 'Las curvas más famosas de San Diego hasta el observatorio de Palomar. Requiere pasaporte o visa.',
  '[[32.5497,-116.9389],[33.3563,-116.8650]]', true, 32.5497, -116.9389)
on conflict (name) do nothing;
