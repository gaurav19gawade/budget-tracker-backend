-- V3__add_account_type_and_user_preferences.sql
-- Add account type columns to teller_enrollments
ALTER TABLE teller_enrollments
    ADD COLUMN IF NOT EXISTS account_type     VARCHAR(50),
    ADD COLUMN IF NOT EXISTS account_subtype  VARCHAR(50),
    ADD COLUMN IF NOT EXISTS environment      VARCHAR(20) DEFAULT 'sandbox';

-- Create user_preferences table
CREATE TABLE IF NOT EXISTS user_preferences (
    id                   BIGSERIAL PRIMARY KEY,
    user_id              BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    report_frequency     VARCHAR(20) NOT NULL DEFAULT 'NONE',
    last_report_sent_at  TIMESTAMP,
    updated_at           TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_user_preferences_user_id ON user_preferences(user_id);
