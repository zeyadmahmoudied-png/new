-- My Plan mobile identity fix
-- Run this once in the Supabase SQL Editor for the live project.
-- It creates the profile row for the authenticated Supabase user instead of
-- relying on the old local/fake user ID.

CREATE OR REPLACE FUNCTION public.update_my_profile(
  p_display_name text DEFAULT NULL
)
RETURNS public.profiles
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  result_profile public.profiles;
  current_email text;
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'not authenticated';
  END IF;

  current_email := NULLIF(auth.jwt() ->> 'email', '');

  INSERT INTO public.profiles (
    id,
    email,
    display_name,
    updated_at
  )
  VALUES (
    auth.uid(),
    current_email,
    NULLIF(trim(p_display_name), ''),
    now()
  )
  ON CONFLICT (id)
  DO UPDATE SET
    email = COALESCE(EXCLUDED.email, public.profiles.email),
    display_name = EXCLUDED.display_name,
    updated_at = now()
  RETURNING * INTO result_profile;

  RETURN result_profile;
END;
$$;

GRANT EXECUTE ON FUNCTION public.update_my_profile(text) TO authenticated;
