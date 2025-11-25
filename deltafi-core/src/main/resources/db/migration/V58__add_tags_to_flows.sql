ALTER TABLE flows ADD COLUMN tags jsonb DEFAULT '[]'::jsonb NOT NULL;
CREATE INDEX idx_flows_tags ON flows USING GIN(tags);