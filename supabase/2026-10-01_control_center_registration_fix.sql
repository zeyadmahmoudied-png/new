-- My Plan Control Center / Android client-safe Supabase bridge
-- Run this migration once in Supabase SQL Editor.

create or replace function public.myplan_register_app_user(
  p_user_id uuid,
  p_local_user_id text,
  p_email text default null,
  p_display_name text default null,
  p_app_version text default null
) returns boolean
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.app_users(
    id, local_user_id, email, display_name, status, app_version, last_seen_at
  )
  values(
    p_user_id, p_local_user_id, nullif(p_email,''), nullif(p_display_name,''),
    'active', nullif(p_app_version,''), now()
  )
  on conflict(id) do update set
    local_user_id = excluded.local_user_id,
    email = coalesce(excluded.email, public.app_users.email),
    display_name = coalesce(excluded.display_name, public.app_users.display_name),
    status = 'active',
    app_version = coalesce(excluded.app_version, public.app_users.app_version),
    last_seen_at = now();
  return true;
end;
$$;

create or replace function public.myplan_register_device(
  p_installation_id text,
  p_user_id uuid,
  p_device_name text,
  p_manufacturer text,
  p_model text,
  p_android_version text,
  p_app_version text,
  p_fcm_token text default null
) returns boolean
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.devices(
    installation_id,user_id,device_name,manufacturer,model,
    android_version,app_version,fcm_token,last_seen_at
  )
  values(
    p_installation_id,p_user_id,p_device_name,p_manufacturer,p_model,
    p_android_version,p_app_version,p_fcm_token,now()
  )
  on conflict(installation_id) do update set
    user_id=excluded.user_id,
    device_name=excluded.device_name,
    manufacturer=excluded.manufacturer,
    model=excluded.model,
    android_version=excluded.android_version,
    app_version=excluded.app_version,
    fcm_token=coalesce(excluded.fcm_token,public.devices.fcm_token),
    last_seen_at=now();
  return true;
end;
$$;

-- Android uses a local account identity, not Supabase Auth.
-- This RPC exposes only grants matching the exact local identity/device supplied by the app.
create or replace function public.myplan_get_premium_grants(
  p_user_id uuid default null,
  p_installation_id text default null
) returns setof public.premium_grants
language sql
security definer
set search_path = public
as $$
  select g.*
  from public.premium_grants g
  where (p_user_id is not null and g.user_id = p_user_id)
     or (p_installation_id is not null and g.installation_id = p_installation_id);
$$;

grant execute on function public.myplan_register_app_user(uuid,text,text,text,text) to anon, authenticated;
grant execute on function public.myplan_register_device(text,uuid,text,text,text,text,text,text) to anon, authenticated;
grant execute on function public.myplan_get_premium_grants(uuid,text) to anon, authenticated;
