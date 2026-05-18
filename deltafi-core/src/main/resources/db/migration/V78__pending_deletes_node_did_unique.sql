-- Dedupe before adding the constraint: keep the oldest row per (node, did)
-- so the lag check continues to reflect the true age of pending deletes.
DELETE FROM pending_deletes a
USING pending_deletes b
WHERE a.node = b.node
  AND a.did = b.did
  AND (a.added_at, a.ctid) > (b.added_at, b.ctid);

ALTER TABLE pending_deletes
    ADD CONSTRAINT pending_deletes_node_did_key UNIQUE (node, did);
