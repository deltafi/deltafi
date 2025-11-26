ALTER TABLE event_annotations ADD COLUMN created TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE INDEX idx_event_annotations_created ON event_annotations(created);
