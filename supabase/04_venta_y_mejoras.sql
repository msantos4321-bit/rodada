-- Rodada (4): CFMOTO, interruptor de venta, reportes de fallas, administrar grupo, borrar mis datos y «Sígueme».
-- Se puede volver a correr sin romper nada.

-- ---------- Fichas CFMOTO (fuentes: cfmoto.ca, mcnews.com.au, 1000ps.com, maintenanceschedule.com) ----------
insert into rd_bike_specs (brand, model, aliases, years, kind, engine, power, weight, fuel_l, oil, oil_l, tire_f, tire_r, psi_f, psi_r, plug, chain, oil_km, valves_km, tips, mods) values
('CFMOTO', '250NK', '250 nk 250nk cf moto cfmoto 250', '2019–2026', 'Naked', '249 cc, 1 cilindro', '27.5 hp', '151 kg', 12.5, '10W-40 (JASO MA2)', '', '110/70 R17', '140/60 R17', null, null, '', '', 5000, null, 'Usa aceite para moto (JASO MA2), nunca de auto: el embrague patina. Revisa tensión y lubrica la cadena cada 600–1,000 km. Revisa la presión en frío, antes de rodar. CFMOTO pide el primer servicio a los 1,000 km y cambio de aceite cada 5,000 km en EE. UU.; respétalo para no perder la garantía (3 años en varios países).', 'Sliders de motor, protector de radiador, contrapesos de manubrio, parabrisas pequeño, amortiguador trasero mejorado.'),
('CFMOTO', '300NK', '300 nk 300nk cf moto cfmoto 300', '2022–2026', 'Naked', '292 cc, 1 cilindro', '28 hp', '151 kg', 12.5, '10W-40 (JASO MA2)', '', '110/70 R17', '140/60 R17', null, null, '', '', 5000, null, 'Usa aceite para moto (JASO MA2), nunca de auto: el embrague patina. Revisa tensión y lubrica la cadena cada 600–1,000 km. Revisa la presión en frío, antes de rodar. CFMOTO pide el primer servicio a los 1,000 km y cambio de aceite cada 5,000 km en EE. UU.; respétalo para no perder la garantía (3 años en varios países).', 'Sliders de motor, protector de radiador, contrapesos de manubrio, parabrisas pequeño, amortiguador trasero mejorado.'),
('CFMOTO', '300SR', '300 sr 300sr cf moto cfmoto', '2021–2026', 'Deportiva ligera', '292 cc, 1 cilindro', '29 hp', '', null, '10W-40 (JASO MA2)', '', '110/70 R17', '140/60 R17', null, null, '', '', 5000, null, 'Usa aceite para moto (JASO MA2), nunca de auto: el embrague patina. Revisa tensión y lubrica la cadena cada 600–1,000 km. Revisa la presión en frío, antes de rodar. CFMOTO pide el primer servicio a los 1,000 km y cambio de aceite cada 5,000 km en EE. UU.; respétalo para no perder la garantía (3 años en varios países).', 'Sliders y protector de tanque, mangueras de freno de malla, pastillas sinterizadas, parabrisas alto para carretera.'),
('CFMOTO', '450NK', '450 nk 450nk cf moto cfmoto', '2023–2026', 'Naked', '449.5 cc, 2 cilindros', '47 hp', '', 14, '10W-40 (JASO MA2)', '', '110/70 R17', '150/60 R17', null, null, '', '', 5000, null, 'Usa aceite para moto (JASO MA2), nunca de auto: el embrague patina. Revisa tensión y lubrica la cadena cada 600–1,000 km. Revisa la presión en frío, antes de rodar. CFMOTO pide el primer servicio a los 1,000 km y cambio de aceite cada 5,000 km en EE. UU.; respétalo para no perder la garantía (3 años en varios países).', 'Sliders de motor, protector de radiador, contrapesos de manubrio, parabrisas pequeño, amortiguador trasero mejorado.'),
('CFMOTO', '450SR / 450SR S', '450 sr 450sr 450srs cf moto cfmoto', '2023–2026', 'Deportiva', '449.5 cc, 2 cilindros', '47 hp', '', 14, '10W-40 (JASO MA2)', '', '110/70 R17', '150/60 R17', null, null, '', '', 5000, null, 'Usa aceite para moto (JASO MA2), nunca de auto: el embrague patina. Revisa tensión y lubrica la cadena cada 600–1,000 km. Revisa la presión en frío, antes de rodar. CFMOTO pide el primer servicio a los 1,000 km y cambio de aceite cada 5,000 km en EE. UU.; respétalo para no perder la garantía (3 años en varios países).', 'Sliders y protector de tanque, mangueras de freno de malla, pastillas sinterizadas, parabrisas alto para carretera.'),
('CFMOTO', '450MT', '450 mt 450mt ibex 450 cf moto cfmoto', '2024–2026', 'Adventure ligera', '449 cc, 2 cilindros', '44 hp', '175 kg (seco)', 17.5, '10W-40 (JASO MA2)', '', '90/90-21', '140/70 R18', 33, 33, 'BN8RTI', '', 5000, 40000, 'Para terracería baja la presión (aprox. 20–25 psi) y regrésala al volver al asfalto. Revisa rayos/rines y apriete de tornillería después de rodadas de tierra. CFMOTO pide el primer servicio a los 1,000 km y cambio de aceite cada 5,000 km en EE. UU.; respétalo para no perder la garantía (3 años en varios países).', 'Defensas de motor y cubre cárter, protectores de manos, maletas laterales, llantas mixtas 70/30, estribos anchos, toma USB.'),
('CFMOTO', '450CL-C', '450 cl-c 450clc 450 clc cf moto cfmoto', '2024–2026', 'Cruiser', '449 cc, 2 cilindros', '40 hp', '181 kg', 12, '10W-40 (JASO MA2)', '', '130/90-16', '150/80-16', null, null, '', '', 5000, null, 'CFMOTO pide el primer servicio a los 1,000 km y cambio de aceite cada 5,000 km en EE. UU.; respétalo para no perder la garantía (3 años en varios países).', 'Respaldo para pasajero, alforjas, parabrisas desmontable, defensas, luces auxiliares.'),
('CFMOTO', '650MT', '650 mt 650mt cf moto cfmoto', '2019–2024', 'Turismo', '649 cc, 2 cilindros', '56 hp', '218 kg', 18, '', '', '120/70 R17', '160/60 R17', null, null, '', '', 5000, null, 'Usa aceite para moto (JASO MA2), nunca de auto: el embrague patina. Revisa tensión y lubrica la cadena cada 600–1,000 km. Revisa la presión en frío, antes de rodar. CFMOTO pide el primer servicio a los 1,000 km y cambio de aceite cada 5,000 km en EE. UU.; respétalo para no perder la garantía (3 años en varios países).', 'Defensas de motor y cubre cárter, protectores de manos, maletas laterales, llantas mixtas 70/30, estribos anchos, toma USB.'),
('CFMOTO', '700CL-X', '700 cl-x 700clx clx cf moto cfmoto heritage sport adventure', '2021–2026', 'Neo-retro', '693 cc, 2 cilindros', '70 hp', '196 kg', 13, '', '', '110/80 R18', '180/55 R17', null, null, '', '', 5000, null, 'Usa aceite para moto (JASO MA2), nunca de auto: el embrague patina. Revisa tensión y lubrica la cadena cada 600–1,000 km. Revisa la presión en frío, antes de rodar. CFMOTO pide el primer servicio a los 1,000 km y cambio de aceite cada 5,000 km en EE. UU.; respétalo para no perder la garantía (3 años en varios países).', 'Respaldo para pasajero, alforjas, parabrisas desmontable, defensas, luces auxiliares.'),
('CFMOTO', '800MT', '800 mt 800mt sport touring explore cf moto cfmoto', '2022–2026', 'Adventure', '799 cc, 2 cilindros', '95 hp', '225 kg', 19, '', '', '110/80 R19', '150/70 R17', null, null, '', '', 5000, null, 'Para terracería baja la presión (aprox. 20–25 psi) y regrésala al volver al asfalto. Revisa rayos/rines y apriete de tornillería después de rodadas de tierra. CFMOTO pide el primer servicio a los 1,000 km y cambio de aceite cada 5,000 km en EE. UU.; respétalo para no perder la garantía (3 años en varios países).', 'Defensas de motor y cubre cárter, protectores de manos, maletas laterales, llantas mixtas 70/30, estribos anchos, toma USB.'),
('CFMOTO', '800MT-X / Ibex 800', '800 mt-x 800mtx mtx ibex 800 cf moto cfmoto', '2025–2026', 'Adventure (terracería)', '799 cc, 2 cilindros', '95 hp', '', 22.5, '10W-50 (JASO MA2)', '2.8 L con filtro', '90/90-21', '150/70 R18', 35, 35, 'NGK LMAR9AI-10', '', 5000, 30000, 'Para terracería baja la presión (aprox. 20–25 psi) y regrésala al volver al asfalto. Revisa rayos/rines y apriete de tornillería después de rodadas de tierra. CFMOTO pide el primer servicio a los 1,000 km y cambio de aceite cada 5,000 km en EE. UU.; respétalo para no perder la garantía (3 años en varios países).', 'Defensas de motor y cubre cárter, protectores de manos, maletas laterales, llantas mixtas 70/30, estribos anchos, toma USB.'),
('CFMOTO', '800NK', '800 nk 800nk sport advanced cf moto cfmoto', '2023–2026', 'Naked', '799 cc, 2 cilindros', '99 hp', '186 kg', 15, '', '', '', '180/55 R17', null, null, '', '', 5000, null, 'Usa aceite para moto (JASO MA2), nunca de auto: el embrague patina. Revisa tensión y lubrica la cadena cada 600–1,000 km. Revisa la presión en frío, antes de rodar. CFMOTO pide el primer servicio a los 1,000 km y cambio de aceite cada 5,000 km en EE. UU.; respétalo para no perder la garantía (3 años en varios países).', 'Sliders de motor, protector de radiador, contrapesos de manubrio, parabrisas pequeño, amortiguador trasero mejorado.')
on conflict (brand, model) do nothing;

-- ---------- Interruptor de venta ----------
-- Se controla en biz_settings → apps → rodada (desde el Centro de control o aquí):
--   require_code: true  = la app pide código o prueba gratis
--   free_until:   'AAAA-MM-DD' = los celulares que instalaron Rodada antes de esa fecha siguen gratis (los amigos)
--   notice:       texto opcional que aparece en el inicio de la app
create or replace function rd_app_config() returns jsonb
language sql stable security definer set search_path = public as $$
  select jsonb_build_object('require_code', false, 'free_until', '', 'notice', '')
    || coalesce((select (data->'apps'->'rodada') - 'download_url' - 'prefix' from biz_settings where id = 1), '{}'::jsonb)
$$;

-- ---------- Reportes de fallas ----------
create table if not exists rd_crashes (
  id bigserial primary key,
  member_id uuid,
  version text,
  device text,
  trace text,
  created_at timestamptz not null default now()
);
alter table rd_crashes enable row level security;

create or replace function rd_crash_add(p_me uuid, p_version text, p_device text, p_trace text) returns text
language plpgsql volatile security definer set search_path = public as $$
begin
  if (select count(*) from rd_crashes where member_id = p_me and created_at > now() - interval '1 hour') > 20 then return 'busy'; end if;
  insert into rd_crashes (member_id, version, device, trace) values (p_me, left(p_version, 20), left(p_device, 80), left(p_trace, 8000));
  return 'ok';
end $$;

-- ---------- Administrar el grupo (solo quien lo creó o un admin) ----------
create or replace function rd__admin(p_me uuid, p_group uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from rd_group_members where group_id = p_group and member_id = p_me and role = 'admin')
$$;

create or replace function rd_group_rename(p_me uuid, p_group uuid, p_name text) returns text
language plpgsql volatile security definer set search_path = public as $$
begin
  if not rd__admin(p_me, p_group) then return 'not_admin'; end if;
  update rd_groups set name = left(trim(p_name), 50) where id = p_group;
  insert into rd_messages (group_id, member_id, kind, body) values (p_group, p_me, 'system', 'El grupo ahora se llama «' || left(trim(p_name), 50) || '»');
  return 'ok';
end $$;

create or replace function rd_group_kick(p_me uuid, p_group uuid, p_member uuid) returns text
language plpgsql volatile security definer set search_path = public as $$
begin
  if not rd__admin(p_me, p_group) or p_member = p_me then return 'not_admin'; end if;
  delete from rd_group_members where group_id = p_group and member_id = p_member;
  delete from rd_live where group_id = p_group and member_id = p_member;
  return 'ok';
end $$;

create or replace function rd_group_admin(p_me uuid, p_group uuid, p_member uuid) returns text
language plpgsql volatile security definer set search_path = public as $$
begin
  if not rd__admin(p_me, p_group) then return 'not_admin'; end if;
  update rd_group_members set role = 'admin' where group_id = p_group and member_id = p_member;
  return 'ok';
end $$;

create or replace function rd_group_newcode(p_me uuid, p_group uuid) returns text
language plpgsql volatile security definer set search_path = public as $$
declare c text;
begin
  if not rd__admin(p_me, p_group) then return 'not_admin'; end if;
  c := rd__new_code();
  update rd_groups set code = c where id = p_group;
  return c;
end $$;

-- ---------- Borrar mis datos del grupo ----------
create or replace function rd_delete_me(p_me uuid) returns text
language plpgsql volatile security definer set search_path = public as $$
begin
  delete from rd_posts where member_id = p_me;
  delete from rd_comments where member_id = p_me;
  delete from rd_messages where member_id = p_me;
  delete from rd_hazards where member_id = p_me;
  delete from rd_places where member_id = p_me;
  delete from rd_share where member_id = p_me;
  delete from rd_members where id = p_me;
  return 'ok';
end $$;

-- ---------- «Sígueme»: la familia ve tu viaje en una página, sin instalar nada ----------
create table if not exists rd_share (
  token text primary key,
  member_id uuid references rd_members(id) on delete cascade,
  name text not null default '',
  dest text not null default '',
  lat double precision,
  lon double precision,
  speed real not null default 0,
  eta_min int,
  remaining_km real,
  status text not null default 'rodando',
  track jsonb not null default '[]',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  expires_at timestamptz not null default now() + interval '12 hours'
);
alter table rd_share enable row level security;

create or replace function rd_share_start(p_me uuid, p_name text, p_dest text, p_hours int) returns text
language plpgsql volatile security definer set search_path = public as $$
declare t text := substr(md5(random()::text || clock_timestamp()::text || p_me::text), 1, 16);
begin
  insert into rd_members (id, name) values (p_me, left(coalesce(p_name, ''), 40)) on conflict (id) do nothing;
  insert into rd_share (token, member_id, name, dest, expires_at)
  values (t, p_me, left(coalesce(p_name, ''), 40), left(coalesce(p_dest, ''), 80), now() + make_interval(hours => greatest(1, least(coalesce(p_hours, 12), 48))));
  return t;
end $$;

create or replace function rd_share_update(p_me uuid, p_token text, p_lat double precision, p_lon double precision, p_speed real,
  p_eta int, p_remaining real, p_status text) returns text
language sql volatile security definer set search_path = public as $$
  update rd_share set lat = p_lat, lon = p_lon, speed = coalesce(p_speed, 0), eta_min = p_eta, remaining_km = p_remaining,
    status = coalesce(p_status, status), updated_at = now(),
    track = case when p_lat is null then track else (case when jsonb_array_length(track) > 1500 then track - 0 else track end) || jsonb_build_array(jsonb_build_array(round(p_lat::numeric, 5), round(p_lon::numeric, 5))) end
  where token = p_token and member_id = p_me;
  select 'ok';
$$;

create or replace function rd_share_get(p_token text) returns jsonb
language sql stable security definer set search_path = public as $$
  select jsonb_build_object('name', name, 'dest', dest, 'lat', lat, 'lon', lon, 'speed', speed, 'eta_min', eta_min,
    'remaining_km', remaining_km, 'status', case when expires_at < now() then 'terminado' else status end,
    'age', extract(epoch from now() - updated_at)::int, 'track', track)
  from rd_share where token = p_token
$$;

grant execute on function rd_app_config, rd_crash_add, rd_group_rename, rd_group_kick, rd_group_admin, rd_group_newcode,
  rd_delete_me, rd_share_start, rd_share_update, rd_share_get to anon;
