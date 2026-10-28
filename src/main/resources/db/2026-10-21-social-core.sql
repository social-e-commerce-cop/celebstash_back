-- ============================================================================================
-- Social core migration (session, likes, feed, messaging, notifications, stories)
--
-- Production runs with SPRING_JPA_HIBERNATE_DDL_AUTO=none and SPRING_SQL_INIT_MODE=never, so
-- neither Hibernate nor schema.sql applies these changes there. Run this file against the Neon
-- database BEFORE deploying the matching backend build: the new entity fields
-- (notifications.actor_id, chat_messages.client_message_id) are mapped columns, and queries on
-- those tables fail until the columns exist.
--
-- Every statement is idempotent; running the file twice is safe.
-- The DELETE statements remove exact duplicates that the new unique indexes would reject.
-- ============================================================================================

-- 1. Notifications: who caused it, and indexes for the list and the unread badge.
ALTER TABLE IF EXISTS notifications ADD COLUMN IF NOT EXISTS actor_id BIGINT REFERENCES users(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_notifications_user_created ON notifications(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_user_unread ON notifications(user_id) WHERE read = false;

-- 2. Chat: client-generated message id makes sending idempotent (no duplicate on retry).
ALTER TABLE IF EXISTS chat_messages ADD COLUMN IF NOT EXISTS client_message_id VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS ux_chat_messages_sender_client_id
    ON chat_messages(sender_id, client_message_id) WHERE client_message_id IS NOT NULL;

-- 3. Likes: one like per user per item. Keeps the earliest row of any duplicate set.
DELETE FROM likes a USING likes b
 WHERE a.id > b.id
   AND a.user_id = b.user_id AND a.likeable_type = b.likeable_type AND a.likeable_id = b.likeable_id;
CREATE UNIQUE INDEX IF NOT EXISTS ux_likes_user_item ON likes(user_id, likeable_type, likeable_id);

-- 4a. Stories: the legacy NOT NULL "type" column (from an older StoryType design) is no longer mapped by
-- the Story entity, so every story insert failed with a not-null violation. Make it optional. The ADD
-- keeps the statement valid on databases that never had the column.
ALTER TABLE IF EXISTS stories ADD COLUMN IF NOT EXISTS type VARCHAR(255);
ALTER TABLE IF EXISTS stories ALTER COLUMN type DROP NOT NULL;
-- Same drift on story_views: legacy databases key the table on (story_id, user_id) from when it was a
-- join table, while the entity stores the viewer in viewer_id, so every view insert failed. The
-- backend now writes both columns; this makes sure user_id exists on databases created later.
ALTER TABLE IF EXISTS story_views ADD COLUMN IF NOT EXISTS user_id BIGINT;

-- 4b. Stories: one reaction (latest wins) and one view per user per story.
DELETE FROM story_reactions a USING story_reactions b
 WHERE a.id < b.id AND a.story_id = b.story_id AND a.user_id = b.user_id;
CREATE UNIQUE INDEX IF NOT EXISTS ux_story_reactions_story_user ON story_reactions(story_id, user_id);

DELETE FROM story_views a USING story_views b
 WHERE a.id > b.id AND a.story_id = b.story_id AND a.viewer_id = b.viewer_id;
CREATE UNIQUE INDEX IF NOT EXISTS ux_story_views_story_viewer ON story_views(story_id, viewer_id);

-- Re-derive the denormalised counters from the rows that remain.
UPDATE stories s SET
    reaction_count = (SELECT COUNT(*) FROM story_reactions r WHERE r.story_id = s.id),
    view_count     = (SELECT COUNT(*) FROM story_views v WHERE v.story_id = s.id);

-- 5. Feed: ranked query filters by status and sorts by created_at.
CREATE INDEX IF NOT EXISTS idx_posts_status_created ON posts(status, created_at DESC);
