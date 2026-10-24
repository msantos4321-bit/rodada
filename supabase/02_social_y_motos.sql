-- Rodada (2): fotos de las rodadas del grupo, foto de la moto y fichas de motos.
-- Se puede volver a correr sin romper nada.

-- ---------- Fotos (Supabase Storage) ----------
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('rodada', 'rodada', true, 6291456, array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do update set public = true, file_size_limit = 6291456, allowed_mime_types = array['image/jpeg', 'image/png', 'image/webp'];

-- La app sube fotos con la llave pública solo dentro de rodada/fotos/ y rodada/motos/
drop policy if exists "rodada subir fotos" on storage.objects;
create policy "rodada subir fotos" on storage.objects for insert to anon
  with check (bucket_id = 'rodada' and (storage.foldername(name))[1] in ('fotos', 'motos'));

alter table rd_members add column if not exists photo text not null default '';

create or replace function rd_member_photo(p_me uuid, p_url text) returns text
language sql volatile security definer set search_path = public as $$
  update rd_members set photo = left(coalesce(p_url, ''), 400) where id = p_me;
  select 'ok';
$$;

-- El grupo ahora incluye la foto de la moto de cada quien
create or replace function rd__group_json(p_group uuid) returns jsonb
language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'id', g.id, 'code', g.code, 'name', g.name, 'home_lat', g.home_lat, 'home_lon', g.home_lon,
    'members', coalesce((
      select jsonb_agg(jsonb_build_object('id', m.id, 'name', m.name, 'color', m.color, 'bike', m.bike, 'role', gm.role, 'photo', m.photo) order by gm.joined_at)
      from rd_group_members gm join rd_members m on m.id = gm.member_id where gm.group_id = g.id), '[]'::jsonb))
  from rd_groups g where g.id = p_group
$$;

-- ---------- Publicaciones ----------
create table if not exists rd_posts (
  id bigserial primary key,
  group_id uuid references rd_groups(id) on delete cascade,
  member_id uuid references rd_members(id) on delete set null,
  body text not null default '',
  photos jsonb not null default '[]',
  ride jsonb,
  created_at timestamptz not null default now()
);
create index if not exists rd_posts_group on rd_posts (group_id, id desc);

create table if not exists rd_likes (
  post_id bigint references rd_posts(id) on delete cascade,
  member_id uuid references rd_members(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (post_id, member_id)
);

create table if not exists rd_comments (
  id bigserial primary key,
  post_id bigint references rd_posts(id) on delete cascade,
  member_id uuid references rd_members(id) on delete set null,
  body text not null,
  created_at timestamptz not null default now()
);
create index if not exists rd_comments_post on rd_comments (post_id, id);

alter table rd_posts enable row level security;
alter table rd_likes enable row level security;
alter table rd_comments enable row level security;

create or replace function rd_post_add(p_me uuid, p_group uuid, p_body text, p_photos jsonb, p_ride jsonb) returns bigint
language plpgsql volatile security definer set search_path = public as $$
declare i bigint; n text; k int := coalesce(jsonb_array_length(p_photos), 0);
begin
  if not rd__in(p_me, p_group) then return -1; end if;
  if (select count(*) from rd_posts where member_id = p_me and created_at > now() - interval '10 minutes') > 15 then return -2; end if;
  insert into rd_posts (group_id, member_id, body, photos, ride)
  values (p_group, p_me, left(coalesce(p_body, ''), 2000), coalesce(p_photos, '[]'::jsonb), p_ride) returning id into i;
  select name into n from rd_members where id = p_me;
  insert into rd_messages (group_id, member_id, kind, body, data) values (p_group, p_me, 'post',
    coalesce(n, 'Alguien') || case when k = 1 then ' compartió una foto' when k > 1 then ' compartió ' || k || ' fotos' else ' publicó' end
    || coalesce(' de «' || (p_ride->>'title') || '»', ''), jsonb_build_object('post', i));
  return i;
end $$;

create or replace function rd__post_json(p bigint, p_me uuid) returns jsonb
language sql stable security definer set search_path = public as $$
  select jsonb_build_object('id', x.id, 'member', x.member_id, 'name', coalesce(m.name, ''), 'color', coalesce(m.color, '#6E6E73'),
    'photo', coalesce(m.photo, ''), 'body', x.body, 'photos', x.photos, 'ride', x.ride, 'at', x.created_at,
    'likes', (select count(*) from rd_likes l where l.post_id = x.id),
    'liked', exists (select 1 from rd_likes l where l.post_id = x.id and l.member_id = p_me),
    'likers', coalesce((select jsonb_agg(u.name) from rd_likes l join rd_members u on u.id = l.member_id where l.post_id = x.id), '[]'::jsonb),
    'comments', (select count(*) from rd_comments c where c.post_id = x.id),
    'last', coalesce((select jsonb_agg(jsonb_build_object('name', coalesce(u.name, ''), 'body', c.body) order by c.id)
       from (select * from rd_comments c2 where c2.post_id = x.id order by c2.id desc limit 2) c left join rd_members u on u.id = c.member_id), '[]'::jsonb))
  from rd_posts x left join rd_members m on m.id = x.member_id where x.id = p
$$;

create or replace function rd_feed(p_me uuid, p_group uuid, p_before bigint default null) returns jsonb
language sql stable security definer set search_path = public as $$
  select case when not rd__in(p_me, p_group) then '[]'::jsonb else coalesce((
    select jsonb_agg(rd__post_json(id, p_me) order by id desc) from (
      select id from rd_posts where group_id = p_group and (p_before is null or id < p_before) order by id desc limit 20) s), '[]'::jsonb) end
$$;

create or replace function rd_like(p_me uuid, p_post bigint) returns jsonb
language plpgsql volatile security definer set search_path = public as $$
declare g uuid;
begin
  select group_id into g from rd_posts where id = p_post;
  if g is null or not rd__in(p_me, g) then return null; end if;
  if exists (select 1 from rd_likes where post_id = p_post and member_id = p_me) then
    delete from rd_likes where post_id = p_post and member_id = p_me;
  else
    insert into rd_likes (post_id, member_id) values (p_post, p_me);
  end if;
  return rd__post_json(p_post, p_me);
end $$;

create or replace function rd_comment_add(p_me uuid, p_post bigint, p_body text) returns jsonb
language plpgsql volatile security definer set search_path = public as $$
declare g uuid; owner uuid; n text;
begin
  select group_id, member_id into g, owner from rd_posts where id = p_post;
  if g is null or not rd__in(p_me, g) then return null; end if;
  insert into rd_comments (post_id, member_id, body) values (p_post, p_me, left(p_body, 1000));
  select name into n from rd_members where id = p_me;
  if owner is distinct from p_me then
    insert into rd_messages (group_id, member_id, kind, body, data) values (g, p_me, 'comment',
      coalesce(n, 'Alguien') || ' comentó: ' || left(p_body, 120), jsonb_build_object('post', p_post));
  end if;
  return rd__post_json(p_post, p_me);
end $$;

create or replace function rd_comments(p_me uuid, p_post bigint) returns jsonb
language sql stable security definer set search_path = public as $$
  select case when not exists (select 1 from rd_posts x where x.id = p_post and rd__in(p_me, x.group_id)) then '[]'::jsonb else coalesce((
    select jsonb_agg(jsonb_build_object('id', c.id, 'member', c.member_id, 'name', coalesce(u.name, ''), 'color', coalesce(u.color, '#6E6E73'),
      'body', c.body, 'at', c.created_at) order by c.id)
    from rd_comments c left join rd_members u on u.id = c.member_id where c.post_id = p_post), '[]'::jsonb) end
$$;

create or replace function rd_post_delete(p_me uuid, p_post bigint) returns text
language sql volatile security definer set search_path = public as $$
  delete from rd_posts where id = p_post and member_id = p_me;
  select 'ok';
$$;

-- ---------- Fichas de motos ----------
create table if not exists rd_bike_specs (
  id serial primary key,
  brand text not null,
  model text not null,
  aliases text not null default '',
  years text not null default '',
  kind text not null default '',
  engine text not null default '',
  power text not null default '',
  weight text not null default '',
  fuel_l numeric,
  oil text not null default '',
  oil_l text not null default '',
  tire_f text not null default '',
  tire_r text not null default '',
  psi_f int,
  psi_r int,
  plug text not null default '',
  chain text not null default '',
  oil_km int,
  valves_km int,
  tips text not null default '',
  mods text not null default '',
  verified boolean not null default false,
  unique (brand, model)
);
alter table rd_bike_specs enable row level security;

-- Pedidos de ficha para modelos que no están (se completan después)
create table if not exists rd_spec_requests (
  id bigserial primary key,
  member_id uuid,
  query text not null,
  created_at timestamptz not null default now()
);
alter table rd_spec_requests enable row level security;

create or replace function rd__spec_json(s rd_bike_specs) returns jsonb
language sql immutable as $$
  select jsonb_build_object('id', s.id, 'brand', s.brand, 'model', s.model, 'years', s.years, 'kind', s.kind, 'engine', s.engine,
    'power', s.power, 'weight', s.weight, 'fuel_l', s.fuel_l, 'oil', s.oil, 'oil_l', s.oil_l, 'tire_f', s.tire_f, 'tire_r', s.tire_r,
    'psi_f', s.psi_f, 'psi_r', s.psi_r, 'plug', s.plug, 'chain', s.chain, 'oil_km', s.oil_km, 'valves_km', s.valves_km,
    'tips', s.tips, 'mods', s.mods, 'verified', s.verified)
$$;

create or replace function rd_specs_search(p_me uuid, p_q text) returns jsonb
language sql stable security definer set search_path = public as $$
  with q as (select lower(regexp_replace(coalesce(p_q, ''), '[^a-zA-Z0-9áéíóúñ ]', ' ', 'g')) t),
  w as (select unnest(string_to_array(trim((select t from q)), ' ')) word),
  scored as (
    select s, (select count(*) from w where length(word) > 0 and lower(s.brand || ' ' || s.model || ' ' || s.aliases) like '%' || word || '%') hits,
      (select count(*) from w where length(word) > 0) total
    from rd_bike_specs s)
  select coalesce(jsonb_agg(rd__spec_json(s) order by hits desc, (s).model), '[]'::jsonb)
  from (select * from scored where hits > 0 and hits >= greatest(1, total - 1) order by hits desc limit 8) x
$$;

create or replace function rd_spec_get(p_me uuid, p_id int) returns jsonb
language sql stable security definer set search_path = public as $$
  select rd__spec_json(s) from rd_bike_specs s where s.id = p_id
$$;

create or replace function rd_spec_request(p_me uuid, p_q text) returns text
language sql volatile security definer set search_path = public as $$
  insert into rd_spec_requests (member_id, query) values (p_me, left(p_q, 120));
  select 'ok';
$$;

grant execute on function rd_member_photo, rd_post_add, rd_feed, rd_like, rd_comment_add, rd_comments, rd_post_delete,
  rd_specs_search, rd_spec_get, rd_spec_request to anon;
