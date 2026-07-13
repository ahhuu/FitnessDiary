-- FitnessDiary CloudBase PostgreSQL schema. This migration intentionally stores
-- only account/social data and user-confirmed health summaries.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS profiles (
    user_id TEXT PRIMARY KEY,
    friend_code VARCHAR(16) NOT NULL UNIQUE,
    nickname VARCHAR(40) NOT NULL DEFAULT '健身伙伴',
    avatar_object_key TEXT,
    bio VARCHAR(160) NOT NULL DEFAULT '一起坚持，慢慢变好',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS friend_relations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_low_id TEXT NOT NULL,
    user_high_id TEXT NOT NULL,
    requester_id TEXT NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('pending', 'accepted', 'rejected', 'blocked')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (user_low_id < user_high_id),
    CHECK (requester_id = user_low_id OR requester_id = user_high_id),
    UNIQUE (user_low_id, user_high_id)
);

CREATE INDEX IF NOT EXISTS friend_relations_pending_recipient_idx
    ON friend_relations (status, user_low_id, user_high_id);

CREATE TABLE IF NOT EXISTS posts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id TEXT NOT NULL REFERENCES profiles(user_id) ON DELETE CASCADE,
    content VARCHAR(1000) NOT NULL DEFAULT '',
    image_object_keys JSONB NOT NULL DEFAULT '[]'::jsonb,
    health_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    visibility VARCHAR(16) NOT NULL DEFAULT 'friends' CHECK (visibility IN ('friends', 'private')),
    client_request_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    UNIQUE (author_id, client_request_id)
);

CREATE INDEX IF NOT EXISTS posts_feed_idx ON posts (author_id, created_at DESC) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS post_likes (
    post_id UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL REFERENCES profiles(user_id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (post_id, user_id)
);

CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_id TEXT NOT NULL REFERENCES profiles(user_id) ON DELETE CASCADE,
    actor_id TEXT REFERENCES profiles(user_id) ON DELETE SET NULL,
    type VARCHAR(32) NOT NULL CHECK (type IN ('friend_request', 'friend_accepted', 'post_liked')),
    target_id UUID,
    event_key VARCHAR(128) NOT NULL UNIQUE,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS notifications_recipient_idx ON notifications (recipient_id, created_at DESC);

CREATE TABLE IF NOT EXISTS user_blocks (
    blocker_id TEXT NOT NULL REFERENCES profiles(user_id) ON DELETE CASCADE,
    blocked_id TEXT NOT NULL REFERENCES profiles(user_id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (blocker_id, blocked_id),
    CHECK (blocker_id <> blocked_id)
);

CREATE TABLE IF NOT EXISTS content_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id TEXT NOT NULL REFERENCES profiles(user_id) ON DELETE CASCADE,
    target_type VARCHAR(16) NOT NULL CHECK (target_type IN ('user', 'post')),
    target_id TEXT NOT NULL,
    reason VARCHAR(64) NOT NULL,
    detail VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS idempotency_keys (
    user_id TEXT NOT NULL,
    operation VARCHAR(80) NOT NULL,
    key UUID NOT NULL,
    response_status SMALLINT NOT NULL,
    response_body JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, operation, key)
);

-- CloudBase PG is accessed directly through PostgREST/RPC. Never supply a user
-- id from Android: auth.uid() comes from the verified CloudBase JWT.
ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE friend_relations ENABLE ROW LEVEL SECURITY;
ALTER TABLE posts ENABLE ROW LEVEL SECURITY;
ALTER TABLE post_likes ENABLE ROW LEVEL SECURITY;
ALTER TABLE notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_blocks ENABLE ROW LEVEL SECURITY;
ALTER TABLE content_reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE idempotency_keys ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE profiles, friend_relations, posts, post_likes, notifications,
    user_blocks, content_reports, idempotency_keys FROM authenticated;
GRANT USAGE ON SCHEMA public TO authenticated;

CREATE POLICY posts_read_owner_or_friend ON posts FOR SELECT TO authenticated
  USING (author_id = (SELECT auth.uid()) OR (
    visibility = 'friends' AND EXISTS (
      SELECT 1 FROM friend_relations fr
      WHERE fr.status = 'accepted'
        AND ((fr.user_low_id = (SELECT auth.uid()) AND fr.user_high_id = posts.author_id)
          OR (fr.user_high_id = (SELECT auth.uid()) AND fr.user_low_id = posts.author_id))
    )
  ));
CREATE POLICY likes_read_visible_posts ON post_likes FOR SELECT TO authenticated
  USING (EXISTS (SELECT 1 FROM posts p WHERE p.id = post_id));
CREATE POLICY notifications_read_self ON notifications FOR SELECT TO authenticated
  USING (recipient_id = (SELECT auth.uid()));

CREATE OR REPLACE FUNCTION fd_my_profile()
RETURNS profiles LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE p profiles;
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'authentication required'; END IF;
  INSERT INTO profiles(user_id, friend_code)
    VALUES (auth.uid(), upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 10)))
    ON CONFLICT (user_id) DO NOTHING;
  SELECT * INTO p FROM profiles WHERE user_id = auth.uid();
  RETURN p;
END; $$;

CREATE OR REPLACE FUNCTION fd_update_profile(p_nickname text, p_bio text)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE nickname_value text := btrim(coalesce(p_nickname, ''));
DECLARE bio_value text := btrim(coalesce(p_bio, ''));
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'authentication required'; END IF;
  IF nickname_value = '' OR length(nickname_value) > 40 THEN
    RAISE EXCEPTION 'invalid nickname';
  END IF;
  IF length(bio_value) > 160 THEN RAISE EXCEPTION 'bio too long'; END IF;
  PERFORM fd_my_profile();
  UPDATE profiles
     SET nickname = nickname_value, bio = bio_value, updated_at = now()
   WHERE user_id = auth.uid();
END; $$;

CREATE OR REPLACE FUNCTION fd_send_friend_request(p_friend_code text)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE me text := auth.uid(); other text;
BEGIN
  IF me IS NULL THEN RAISE EXCEPTION 'authentication required'; END IF;
  PERFORM fd_my_profile();
  SELECT user_id INTO other FROM profiles WHERE friend_code = upper(trim(p_friend_code));
  IF other IS NULL OR other = me THEN RAISE EXCEPTION 'invalid friend code'; END IF;
  IF EXISTS (SELECT 1 FROM user_blocks b
             WHERE (b.blocker_id = me AND b.blocked_id = other)
                OR (b.blocker_id = other AND b.blocked_id = me)) THEN
    RAISE EXCEPTION 'user is blocked';
  END IF;
  INSERT INTO friend_relations(user_low_id,user_high_id,requester_id,status)
  VALUES (least(me,other), greatest(me,other), me, 'pending')
  ON CONFLICT (user_low_id,user_high_id) DO UPDATE
     SET requester_id = EXCLUDED.requester_id, status = 'pending', updated_at = now()
   WHERE friend_relations.status = 'rejected';
END; $$;

CREATE OR REPLACE FUNCTION fd_respond_friend_request(p_request_id uuid, p_accept boolean)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE me text := auth.uid(); rel friend_relations;
BEGIN
  IF me IS NULL THEN RAISE EXCEPTION 'authentication required'; END IF;
  SELECT * INTO rel FROM friend_relations WHERE id = p_request_id FOR UPDATE;
  IF NOT FOUND OR rel.status <> 'pending' OR rel.requester_id = me
     OR (rel.user_low_id <> me AND rel.user_high_id <> me) THEN
    RAISE EXCEPTION 'friend request not found';
  END IF;
  UPDATE friend_relations SET status = CASE WHEN p_accept THEN 'accepted' ELSE 'rejected' END,
      updated_at = now() WHERE id = p_request_id;
END; $$;

CREATE OR REPLACE FUNCTION fd_list_friends()
RETURNS TABLE(user_id text, nickname text, friend_code text, bio text)
LANGUAGE sql SECURITY DEFINER SET search_path = public AS $$
  SELECT p.user_id, p.nickname, p.friend_code, p.bio
  FROM friend_relations fr JOIN profiles p ON p.user_id =
       CASE WHEN fr.user_low_id = auth.uid() THEN fr.user_high_id ELSE fr.user_low_id END
  WHERE auth.uid() IS NOT NULL AND fr.status = 'accepted'
    AND (fr.user_low_id = auth.uid() OR fr.user_high_id = auth.uid());
$$;

CREATE OR REPLACE FUNCTION fd_list_requests()
RETURNS TABLE(request_id uuid, user_id text, nickname text, friend_code text, bio text)
LANGUAGE sql SECURITY DEFINER SET search_path = public AS $$
  SELECT fr.id, p.user_id, p.nickname, p.friend_code, p.bio
  FROM friend_relations fr JOIN profiles p ON p.user_id = fr.requester_id
  WHERE auth.uid() IS NOT NULL AND fr.status = 'pending' AND fr.requester_id <> auth.uid()
    AND (fr.user_low_id = auth.uid() OR fr.user_high_id = auth.uid());
$$;

CREATE OR REPLACE FUNCTION fd_create_post(p_content text, p_health_summary jsonb, p_client_request_id uuid)
RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE post_id uuid;
DECLARE summary jsonb := coalesce(p_health_summary, '{}'::jsonb);
DECLARE summary_key text;
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'authentication required'; END IF;
  IF jsonb_typeof(summary) <> 'object' THEN RAISE EXCEPTION 'invalid health summary'; END IF;
  FOR summary_key IN SELECT jsonb_object_keys(summary) LOOP
    IF summary_key NOT IN ('workoutMinutes', 'checkInDays', 'steps', 'achievement') THEN
      RAISE EXCEPTION 'unsupported health summary field: %', summary_key;
    END IF;
  END LOOP;
  IF summary ? 'workoutMinutes' THEN
    IF jsonb_typeof(summary->'workoutMinutes') <> 'number'
       OR (summary->>'workoutMinutes')::numeric < 0
       OR (summary->>'workoutMinutes')::numeric > 1000000
       OR (summary->>'workoutMinutes')::numeric % 1 <> 0 THEN
      RAISE EXCEPTION 'invalid workoutMinutes';
    END IF;
  END IF;
  IF summary ? 'checkInDays' THEN
    IF jsonb_typeof(summary->'checkInDays') <> 'number'
       OR (summary->>'checkInDays')::numeric < 0
       OR (summary->>'checkInDays')::numeric > 1000000
       OR (summary->>'checkInDays')::numeric % 1 <> 0 THEN
      RAISE EXCEPTION 'invalid checkInDays';
    END IF;
  END IF;
  IF summary ? 'steps' THEN
    IF jsonb_typeof(summary->'steps') <> 'number'
       OR (summary->>'steps')::numeric < 0
       OR (summary->>'steps')::numeric > 1000000
       OR (summary->>'steps')::numeric % 1 <> 0 THEN
      RAISE EXCEPTION 'invalid steps';
    END IF;
  END IF;
  IF summary ? 'achievement' THEN
    IF jsonb_typeof(summary->'achievement') <> 'string'
       OR btrim(summary->>'achievement') = ''
       OR length(btrim(summary->>'achievement')) > 80 THEN
      RAISE EXCEPTION 'invalid achievement';
    END IF;
  END IF;
  PERFORM fd_my_profile();
  INSERT INTO posts(author_id, content, health_summary, client_request_id)
  VALUES (auth.uid(), left(coalesce(p_content,''),1000), summary, p_client_request_id)
  ON CONFLICT (author_id, client_request_id) DO UPDATE SET content = EXCLUDED.content
  RETURNING id INTO post_id;
  RETURN post_id;
END; $$;

CREATE OR REPLACE FUNCTION fd_toggle_like(p_post_id uuid, p_liked boolean)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'authentication required'; END IF;
  IF p_liked THEN
    IF NOT EXISTS (SELECT 1 FROM posts p WHERE p.id=p_post_id AND p.deleted_at IS NULL AND
      (p.author_id=auth.uid() OR EXISTS (SELECT 1 FROM friend_relations fr WHERE fr.status='accepted' AND
        ((fr.user_low_id=auth.uid() AND fr.user_high_id=p.author_id) OR
         (fr.user_high_id=auth.uid() AND fr.user_low_id=p.author_id))))) THEN
      RAISE EXCEPTION 'post not visible';
    END IF;
    INSERT INTO post_likes(post_id,user_id) VALUES (p_post_id,auth.uid()) ON CONFLICT DO NOTHING;
  ELSE
    DELETE FROM post_likes WHERE post_id=p_post_id AND user_id=auth.uid();
  END IF;
END; $$;

CREATE OR REPLACE FUNCTION fd_feed(p_limit integer DEFAULT 20)
RETURNS TABLE(post_id uuid, author_id text, nickname text, content text, health_summary jsonb,
              created_at timestamptz, like_count bigint, liked boolean, owned_by_current_user boolean)
LANGUAGE sql SECURITY DEFINER SET search_path = public AS $$
  SELECT p.id,p.author_id,pr.nickname,p.content,p.health_summary,p.created_at,
    count(pl.user_id), bool_or(pl.user_id=auth.uid()), p.author_id=auth.uid()
  FROM posts p JOIN profiles pr ON pr.user_id=p.author_id
    LEFT JOIN post_likes pl ON pl.post_id=p.id
  WHERE auth.uid() IS NOT NULL AND p.deleted_at IS NULL
    AND (p.author_id=auth.uid() OR (p.visibility = 'friends' AND EXISTS (
      SELECT 1 FROM friend_relations fr WHERE fr.status='accepted' AND
        ((fr.user_low_id=auth.uid() AND fr.user_high_id=p.author_id) OR
         (fr.user_high_id=auth.uid() AND fr.user_low_id=p.author_id)))))
    AND NOT EXISTS (SELECT 1 FROM user_blocks b WHERE
      (b.blocker_id=auth.uid() AND b.blocked_id=p.author_id) OR
      (b.blocker_id=p.author_id AND b.blocked_id=auth.uid()))
  GROUP BY p.id,pr.nickname ORDER BY p.created_at DESC LIMIT greatest(1,least(p_limit,50));
$$;

CREATE OR REPLACE FUNCTION fd_find_profile(p_friend_code text)
RETURNS TABLE(user_id text, nickname text, friend_code text, bio text)
LANGUAGE sql SECURITY DEFINER SET search_path = public AS $$
  SELECT p.user_id,p.nickname,p.friend_code,p.bio FROM profiles p
  WHERE auth.uid() IS NOT NULL AND p.friend_code=upper(trim(p_friend_code)) AND p.user_id<>auth.uid()
    AND NOT EXISTS (SELECT 1 FROM user_blocks b WHERE
      (b.blocker_id=auth.uid() AND b.blocked_id=p.user_id) OR
      (b.blocker_id=p.user_id AND b.blocked_id=auth.uid()));
$$;

CREATE OR REPLACE FUNCTION fd_remove_friend(p_user_id text)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'authentication required'; END IF;
  DELETE FROM friend_relations WHERE status='accepted' AND
    ((user_low_id=least(auth.uid(),p_user_id)) AND (user_high_id=greatest(auth.uid(),p_user_id)));
END; $$;

CREATE OR REPLACE FUNCTION fd_delete_post(p_post_id uuid)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'authentication required'; END IF;
  UPDATE posts SET deleted_at=now() WHERE id=p_post_id AND author_id=auth.uid();
END; $$;

CREATE OR REPLACE FUNCTION fd_block_user(p_user_id text)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF auth.uid() IS NULL OR p_user_id=auth.uid() THEN RAISE EXCEPTION 'invalid user'; END IF;
  INSERT INTO user_blocks(blocker_id,blocked_id) VALUES(auth.uid(),p_user_id) ON CONFLICT DO NOTHING;
  DELETE FROM friend_relations WHERE user_low_id=least(auth.uid(),p_user_id)
    AND user_high_id=greatest(auth.uid(),p_user_id);
END; $$;

CREATE OR REPLACE FUNCTION fd_report(p_target_type text,p_target_id text,p_reason text)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF auth.uid() IS NULL OR p_target_type NOT IN ('user','post') THEN RAISE EXCEPTION 'invalid report'; END IF;
  INSERT INTO content_reports(reporter_id,target_type,target_id,reason)
  VALUES(auth.uid(),p_target_type,p_target_id,left(p_reason,64));
END; $$;

-- RPC is the only app-facing write boundary. PostgREST must not expose the
-- SECURITY DEFINER functions to anonymous callers or inherit PUBLIC execute.
REVOKE ALL ON FUNCTION fd_my_profile() FROM PUBLIC, authenticated;
REVOKE ALL ON FUNCTION fd_update_profile(text, text) FROM PUBLIC, authenticated;
REVOKE ALL ON FUNCTION fd_send_friend_request(text) FROM PUBLIC, authenticated;
REVOKE ALL ON FUNCTION fd_respond_friend_request(uuid, boolean) FROM PUBLIC, authenticated;
REVOKE ALL ON FUNCTION fd_list_friends() FROM PUBLIC, authenticated;
REVOKE ALL ON FUNCTION fd_list_requests() FROM PUBLIC, authenticated;
REVOKE ALL ON FUNCTION fd_create_post(text, jsonb, uuid) FROM PUBLIC, authenticated;
REVOKE ALL ON FUNCTION fd_toggle_like(uuid, boolean) FROM PUBLIC, authenticated;
REVOKE ALL ON FUNCTION fd_feed(integer) FROM PUBLIC, authenticated;
REVOKE ALL ON FUNCTION fd_find_profile(text) FROM PUBLIC, authenticated;
REVOKE ALL ON FUNCTION fd_remove_friend(text) FROM PUBLIC, authenticated;
REVOKE ALL ON FUNCTION fd_delete_post(uuid) FROM PUBLIC, authenticated;
REVOKE ALL ON FUNCTION fd_block_user(text) FROM PUBLIC, authenticated;
REVOKE ALL ON FUNCTION fd_report(text, text, text) FROM PUBLIC, authenticated;

GRANT EXECUTE ON FUNCTION fd_my_profile() TO authenticated;
GRANT EXECUTE ON FUNCTION fd_update_profile(text, text) TO authenticated;
GRANT EXECUTE ON FUNCTION fd_send_friend_request(text) TO authenticated;
GRANT EXECUTE ON FUNCTION fd_respond_friend_request(uuid, boolean) TO authenticated;
GRANT EXECUTE ON FUNCTION fd_list_friends() TO authenticated;
GRANT EXECUTE ON FUNCTION fd_list_requests() TO authenticated;
GRANT EXECUTE ON FUNCTION fd_create_post(text, jsonb, uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION fd_toggle_like(uuid, boolean) TO authenticated;
GRANT EXECUTE ON FUNCTION fd_feed(integer) TO authenticated;
GRANT EXECUTE ON FUNCTION fd_find_profile(text) TO authenticated;
GRANT EXECUTE ON FUNCTION fd_remove_friend(text) TO authenticated;
GRANT EXECUTE ON FUNCTION fd_delete_post(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION fd_block_user(text) TO authenticated;
GRANT EXECUTE ON FUNCTION fd_report(text, text, text) TO authenticated;
