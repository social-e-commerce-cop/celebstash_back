-- Alter stories table to guarantee all new columns exist
ALTER TABLE IF EXISTS stories ADD COLUMN IF NOT EXISTS allow_reactions BOOLEAN DEFAULT TRUE;
ALTER TABLE IF EXISTS stories ADD COLUMN IF NOT EXISTS allow_replies BOOLEAN DEFAULT TRUE;
ALTER TABLE IF EXISTS stories ADD COLUMN IF NOT EXISTS reaction_count INT DEFAULT 0;
ALTER TABLE IF EXISTS stories ADD COLUMN IF NOT EXISTS reply_count INT DEFAULT 0;
ALTER TABLE IF EXISTS stories ADD COLUMN IF NOT EXISTS share_count INT DEFAULT 0;
ALTER TABLE IF EXISTS stories ADD COLUMN IF NOT EXISTS view_count INT DEFAULT 0;
ALTER TABLE IF EXISTS stories ADD COLUMN IF NOT EXISTS thumbnail_url VARCHAR(255);
ALTER TABLE IF EXISTS stories ADD COLUMN IF NOT EXISTS is_archived BOOLEAN DEFAULT FALSE;

-- Alter wallets table to guarantee held_balance and pin exist
ALTER TABLE IF EXISTS wallets ADD COLUMN IF NOT EXISTS held_balance NUMERIC(38, 2) DEFAULT 0.00;
ALTER TABLE IF EXISTS wallets ADD COLUMN IF NOT EXISTS pin VARCHAR(6);

-- Alter transactions table for payment gateways and tracking
ALTER TABLE IF EXISTS transactions ADD COLUMN IF NOT EXISTS reference VARCHAR(100);
ALTER TABLE IF EXISTS transactions ADD COLUMN IF NOT EXISTS external_reference VARCHAR(100);
ALTER TABLE IF EXISTS transactions ADD COLUMN IF NOT EXISTS provider VARCHAR(50);
ALTER TABLE IF EXISTS transactions ADD COLUMN IF NOT EXISTS phone_number VARCHAR(50);
ALTER TABLE IF EXISTS transactions ADD COLUMN IF NOT EXISTS currency VARCHAR(10) DEFAULT 'RWF';
ALTER TABLE IF EXISTS transactions ADD COLUMN IF NOT EXISTS before_balance NUMERIC(19, 2);
ALTER TABLE IF EXISTS transactions ADD COLUMN IF NOT EXISTS after_balance NUMERIC(19, 2);
CREATE INDEX IF NOT EXISTS idx_transactions_reference ON transactions(reference);
CREATE INDEX IF NOT EXISTS idx_transactions_external_ref ON transactions(external_reference);

-- Alter users table to guarantee new user columns exist
ALTER TABLE IF EXISTS users ADD COLUMN IF NOT EXISTS account_verified BOOLEAN DEFAULT FALSE;
ALTER TABLE IF EXISTS users ADD COLUMN IF NOT EXISTS account_verified_at TIMESTAMP;
ALTER TABLE IF EXISTS users ADD COLUMN IF NOT EXISTS fandom_name VARCHAR(255);

-- Alter products table to guarantee auction columns exist
ALTER TABLE IF EXISTS products ADD COLUMN IF NOT EXISTS product_type VARCHAR(255) DEFAULT 'REGULAR';
ALTER TABLE IF EXISTS products ADD COLUMN IF NOT EXISTS initial_bid_price NUMERIC(38, 2);
ALTER TABLE IF EXISTS products ADD COLUMN IF NOT EXISTS current_bid_price NUMERIC(38, 2);
ALTER TABLE IF EXISTS products ADD COLUMN IF NOT EXISTS current_bidder_id BIGINT;
ALTER TABLE IF EXISTS products ADD COLUMN IF NOT EXISTS bid_start_time TIMESTAMP;
ALTER TABLE IF EXISTS products ADD COLUMN IF NOT EXISTS bid_end_time TIMESTAMP;

-- Alter posts table and populate defaults for existing records
ALTER TABLE IF EXISTS posts ADD COLUMN IF NOT EXISTS is_sponsored BOOLEAN DEFAULT FALSE;
UPDATE posts SET is_sponsored = FALSE WHERE is_sponsored IS NULL;

ALTER TABLE IF EXISTS posts ADD COLUMN IF NOT EXISTS status VARCHAR(255) DEFAULT 'ACTIVE';
UPDATE posts SET status = 'ACTIVE' WHERE status IS NULL;

-- Drop outdated check constraints on status/type enums to allow newly added enum values
ALTER TABLE IF EXISTS products DROP CONSTRAINT IF EXISTS products_status_check;
ALTER TABLE IF EXISTS products DROP CONSTRAINT IF EXISTS products_product_type_check;
ALTER TABLE IF EXISTS orders DROP CONSTRAINT IF EXISTS orders_status_check;
ALTER TABLE IF EXISTS reservations DROP CONSTRAINT IF EXISTS reservations_status_check;
ALTER TABLE IF EXISTS posts DROP CONSTRAINT IF EXISTS posts_status_check;
ALTER TABLE IF EXISTS transactions DROP CONSTRAINT IF EXISTS transactions_type_check;
ALTER TABLE IF EXISTS transactions DROP CONSTRAINT IF EXISTS transactions_status_check;

-- Stored files table for persisting uploaded images and media directly in PostgreSQL database
CREATE TABLE IF NOT EXISTS stored_files (
    id BIGSERIAL PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL UNIQUE,
    original_file_name VARCHAR(255),
    content_type VARCHAR(100),
    size BIGINT,
    data BYTEA,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_stored_files_name ON stored_files(file_name);


-- Social core (2026-10-21). Mirrors src/main/resources/db/2026-10-21-social-core.sql for local
-- development; production applies that file manually because SQL init is disabled there.
ALTER TABLE IF EXISTS notifications ADD COLUMN IF NOT EXISTS actor_id BIGINT REFERENCES users(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_notifications_user_created ON notifications(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_user_unread ON notifications(user_id) WHERE read = false;
ALTER TABLE IF EXISTS chat_messages ADD COLUMN IF NOT EXISTS client_message_id VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS ux_chat_messages_sender_client_id ON chat_messages(sender_id, client_message_id) WHERE client_message_id IS NOT NULL;
DELETE FROM likes a USING likes b WHERE a.id > b.id AND a.user_id = b.user_id AND a.likeable_type = b.likeable_type AND a.likeable_id = b.likeable_id;
CREATE UNIQUE INDEX IF NOT EXISTS ux_likes_user_item ON likes(user_id, likeable_type, likeable_id);
DELETE FROM story_reactions a USING story_reactions b WHERE a.id < b.id AND a.story_id = b.story_id AND a.user_id = b.user_id;
CREATE UNIQUE INDEX IF NOT EXISTS ux_story_reactions_story_user ON story_reactions(story_id, user_id);
DELETE FROM story_views a USING story_views b WHERE a.id > b.id AND a.story_id = b.story_id AND a.viewer_id = b.viewer_id;
CREATE UNIQUE INDEX IF NOT EXISTS ux_story_views_story_viewer ON story_views(story_id, viewer_id);
CREATE INDEX IF NOT EXISTS idx_posts_status_created ON posts(status, created_at DESC);

-- Legacy NOT NULL stories.type column (from an older StoryType design) is no longer mapped by the
-- Story entity, so every story insert failed. Make it optional; the ADD keeps this valid on databases
-- that never had the column.
ALTER TABLE IF EXISTS stories ADD COLUMN IF NOT EXISTS type VARCHAR(255);
ALTER TABLE IF EXISTS stories ALTER COLUMN type DROP NOT NULL;
ALTER TABLE IF EXISTS story_views ADD COLUMN IF NOT EXISTS user_id BIGINT;
-- Real playback events log for tracking music release and track plays
CREATE TABLE IF NOT EXISTS music_plays (
    id BIGSERIAL PRIMARY KEY,
    release_id BIGINT NOT NULL REFERENCES music_releases(id) ON DELETE CASCADE,
    track_id BIGINT REFERENCES music_tracks(id) ON DELETE SET NULL,
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    played_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    duration_seconds INT
);
CREATE INDEX IF NOT EXISTS idx_music_plays_release ON music_plays(release_id);
CREATE INDEX IF NOT EXISTS idx_music_plays_user ON music_plays(user_id);
CREATE INDEX IF NOT EXISTS idx_music_plays_played_at ON music_plays(played_at);


-- Music approval workflow + cart variants (mirrors db/2026-10-29-music-approval-and-cart-variants.sql)
ALTER TABLE IF EXISTS music_releases ADD COLUMN IF NOT EXISTS submitted_at TIMESTAMP;
ALTER TABLE IF EXISTS music_releases ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP;
ALTER TABLE IF EXISTS music_releases ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP;
ALTER TABLE IF EXISTS music_releases ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(1000);
UPDATE music_releases SET approved_at = COALESCE(updated_at, created_at), reviewed_at = COALESCE(updated_at, created_at)
 WHERE status IN ('PUBLISHED', 'ARCHIVED') AND approved_at IS NULL;
ALTER TABLE IF EXISTS music_releases DROP CONSTRAINT IF EXISTS music_releases_release_type_check;
ALTER TABLE IF EXISTS notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE IF EXISTS cart_items ADD COLUMN IF NOT EXISTS selected_size VARCHAR(255);
ALTER TABLE IF EXISTS cart_items ADD COLUMN IF NOT EXISTS selected_color VARCHAR(255);
