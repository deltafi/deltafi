CREATE TABLE pending_deletes (
     node text NOT NULL,
     did uuid NOT NULL,
     bucket text,
     added_at TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_pending_deletes_node ON pending_deletes (node);