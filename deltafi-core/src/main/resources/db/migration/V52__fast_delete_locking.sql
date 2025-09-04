-- Add started_at column for timeout-based recovery
-- This allows workers to claim rows and other workers to reclaim them if they timeout
ALTER TABLE pending_deletes 
ADD COLUMN started_at TIMESTAMP;

-- Index to efficiently find work to do (NULL started_at or old started_at)
CREATE INDEX idx_pending_deletes_node_started ON pending_deletes (node, started_at);

-- Drop the old single-column index if it exists (the new composite index covers it)
DROP INDEX IF EXISTS idx_pending_deletes_node;

-- Add a comment explaining the timeout-based recovery strategy
COMMENT ON TABLE pending_deletes IS 'Table for tracking pending content deletions. Uses started_at timestamp for timeout-based recovery - rows with NULL or >1min old started_at are eligible for processing.';