-- flyway does not allow transactional and non-transactional statements in the same migration
VACUUM FULL event_annotations;