-- Static SQL regression checks for the CloudBase RPC boundary.
-- Run against a disposable CloudBase PostgreSQL database after applying V1:
--   psql "$CLOUDBASE_DB_URL" -v ON_ERROR_STOP=1 -f tests/social_security_regression.sql
BEGIN;

DO $$
DECLARE
  definition text;
BEGIN
  SELECT pg_get_functiondef(p.oid) INTO definition
    FROM pg_proc p
    JOIN pg_namespace n ON n.oid = p.pronamespace
   WHERE n.nspname = 'public' AND p.proname = 'fd_update_profile'
     AND pg_get_function_identity_arguments(p.oid) = 'p_nickname text, p_bio text';
  IF definition IS NULL OR definition NOT LIKE '%auth.uid()%' THEN
    RAISE EXCEPTION 'fd_update_profile must authenticate with auth.uid()';
  END IF;

  SELECT pg_get_functiondef(p.oid) INTO definition
    FROM pg_proc p
    JOIN pg_namespace n ON n.oid = p.pronamespace
   WHERE n.nspname = 'public' AND p.proname = 'fd_create_post'
     AND pg_get_function_identity_arguments(p.oid) = 'p_content text, p_health_summary jsonb, p_client_request_id uuid';
  IF definition IS NULL
     OR definition NOT LIKE '%unsupported health summary field%'
     OR definition NOT LIKE '%achievement%' THEN
    RAISE EXCEPTION 'fd_create_post must enforce the health summary whitelist';
  END IF;

  SELECT pg_get_functiondef(p.oid) INTO definition
    FROM pg_proc p
    JOIN pg_namespace n ON n.oid = p.pronamespace
   WHERE n.nspname = 'public' AND p.proname = 'fd_feed';
  IF definition IS NULL OR definition NOT LIKE '%p.visibility = ''friends''%' THEN
    RAISE EXCEPTION 'fd_feed must filter friends visibility';
  END IF;

  SELECT pg_get_functiondef(p.oid) INTO definition
    FROM pg_proc p
    JOIN pg_namespace n ON n.oid = p.pronamespace
   WHERE n.nspname = 'public' AND p.proname = 'fd_send_friend_request';
  IF definition IS NULL
     OR definition NOT LIKE '%user_blocks%'
     OR definition NOT LIKE '%friend_relations.status = ''rejected''%' THEN
    RAISE EXCEPTION 'friend requests must enforce blocks and rejected re-requests';
  END IF;
END $$;

DO $$
BEGIN
  IF has_table_privilege('authenticated', 'public.posts', 'INSERT')
     OR has_table_privilege('authenticated', 'public.posts', 'UPDATE')
     OR has_table_privilege('authenticated', 'public.posts', 'DELETE') THEN
    RAISE EXCEPTION 'authenticated must not write posts directly';
  END IF;
  IF has_table_privilege('authenticated', 'public.profiles', 'UPDATE') THEN
    RAISE EXCEPTION 'authenticated must not update profiles directly';
  END IF;
  IF NOT has_function_privilege('authenticated',
        'public.fd_create_post(text,jsonb,uuid)', 'EXECUTE') THEN
    RAISE EXCEPTION 'authenticated must be able to execute fd_create_post';
  END IF;
END $$;

-- Behavioral cases to run with two authenticated CloudBase identities:
-- 1. Calling fd_update_profile with another user's data cannot target that user.
-- 2. fd_create_post with {"calories":450}, negative numbers, or an oversized
--    achievement raises an exception.
-- 3. A private post is absent from fd_feed for an accepted friend.
-- 4. Either direction of user_blocks makes fd_send_friend_request fail.
-- 5. A rejected friend relation becomes pending on a new request.

ROLLBACK;
