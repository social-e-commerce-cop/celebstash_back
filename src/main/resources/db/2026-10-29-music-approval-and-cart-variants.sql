-- ============================================================================================
-- Music approval workflow + cart variants
--
-- Production runs with SPRING_JPA_HIBERNATE_DDL_AUTO=none and SPRING_SQL_INIT_MODE=never, so run
-- this file against the database BEFORE deploying the matching backend build: the new entity
-- fields are mapped columns and queries on these tables fail until the columns exist.
--
-- Every statement is idempotent; running the file twice is safe. No data is deleted.
-- ============================================================================================

-- 1. Release review lifecycle: DRAFT -> PENDING_REVIEW -> PUBLISHED | REJECTED (status is VARCHAR).
ALTER TABLE IF EXISTS music_releases ADD COLUMN IF NOT EXISTS submitted_at TIMESTAMP;
ALTER TABLE IF EXISTS music_releases ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP;
ALTER TABLE IF EXISTS music_releases ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP;
ALTER TABLE IF EXISTS music_releases ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(1000);
CREATE INDEX IF NOT EXISTS idx_music_releases_status_created ON music_releases(status, created_at DESC);

-- Releases that were already live before review existed stay live: record them as approved so
-- archive/unarchive keeps working for them. Drafts are untouched and will go through review.
UPDATE music_releases
   SET approved_at = COALESCE(updated_at, created_at),
       reviewed_at = COALESCE(updated_at, created_at)
 WHERE status IN ('PUBLISHED', 'ARCHIVED') AND approved_at IS NULL;

-- 2. New enum values (PLAYLIST release type, music review notifications). Hibernate generates
--    CHECK constraints for enum columns on table creation; drop them so the new values insert.
ALTER TABLE IF EXISTS music_releases DROP CONSTRAINT IF EXISTS music_releases_release_type_check;
ALTER TABLE IF EXISTS notifications DROP CONSTRAINT IF EXISTS notifications_type_check;

-- 3. Size / colour the buyer picked, kept on the cart line and copied into order item notes.
ALTER TABLE IF EXISTS cart_items ADD COLUMN IF NOT EXISTS selected_size VARCHAR(255);
ALTER TABLE IF EXISTS cart_items ADD COLUMN IF NOT EXISTS selected_color VARCHAR(255);
