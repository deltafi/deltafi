ALTER TYPE dff_state_enum ADD VALUE 'PAUSED' AFTER 'FILTERED';

ALTER TABLE delta_files ADD COLUMN paused boolean DEFAULT false;