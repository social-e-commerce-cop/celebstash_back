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
