-- My Plan Control Center registration fix
-- Run this once in Supabase SQL Editor.
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

grant execute on function public.myplan_register_app_user(uuid,text,text,text,text)
to anon, authenticated;

grant execute on function public.myplan_register_device(text,uuid,text,text,text,text,text,text)
to anon, authenticated;
