-- ABOUTME: Adds plugin installation state tracking columns for async reconciliation.
-- ABOUTME: Supports desired state vs actual state plugin management model.

ALTER TABLE plugins
    ADD COLUMN IF NOT EXISTS install_state VARCHAR(20) DEFAULT 'INSTALLED',
    ADD COLUMN IF NOT EXISTS install_error TEXT,
    ADD COLUMN IF NOT EXISTS last_state_change TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS install_attempts INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS retry_requested BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS last_successful_version TEXT,
    ADD COLUMN IF NOT EXISTS last_successful_image TEXT,
    ADD COLUMN IF NOT EXISTS last_successful_image_tag TEXT,
    ADD COLUMN IF NOT EXISTS disabled BOOLEAN DEFAULT FALSE;

-- Index for finding plugins that need reconciliation action
CREATE INDEX IF NOT EXISTS idx_plugins_install_state ON plugins(install_state)
    WHERE install_state IN ('PENDING', 'INSTALLING', 'FAILED', 'REMOVING');
