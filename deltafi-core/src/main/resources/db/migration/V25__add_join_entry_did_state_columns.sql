alter table join_entry_dids
    add column error_reason text,
    add column orphan boolean,
    add column action_name text;

create index idx_orphan on join_entry_dids (orphan);