ALTER TABLE delta_files ADD COLUMN waiting_for_children boolean DEFAULT false;
CREATE INDEX idx_delta_file_waiting_for_children ON delta_files (did) WHERE (waiting_for_children = true);
CREATE INDEX idx_delta_files_did_not_terminal ON delta_files (did) WHERE terminal = false;
CREATE INDEX idx_delta_file_flows_pending_annotations ON delta_file_flows (delta_file_id) WHERE array_length(pending_annotations, 1) > 0;
