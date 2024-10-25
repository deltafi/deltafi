ALTER TABLE flows DROP CONSTRAINT flows_type_check;

UPDATE flows
SET type = 'DATA_SINK'
WHERE type = 'EGRESS';

ALTER TABLE flows ADD CONSTRAINT flows_type_check CHECK (type IN ('REST_DATA_SOURCE', 'TIMED_DATA_SOURCE', 'TRANSFORM', 'DATA_SINK'));

ALTER TABLE delta_file_flows DROP CONSTRAINT delta_file_flows_type_check;

UPDATE delta_file_flows
SET type = 'DATA_SINK'
WHERE type = 'EGRESS';

ALTER TABLE delta_file_flows ADD CONSTRAINT delta_file_flows_type_check CHECK (type IN ('REST_DATA_SOURCE', 'TIMED_DATA_SOURCE', 'TRANSFORM', 'DATA_SINK'));

UPDATE plugins
SET flow_plans = jsonb_set(flow_plans, '{type}', '"DATA_SINK"', false)
WHERE flow_plans->>'type' = 'EGRESS';

ALTER TABLE system_snapshot RENAME COLUMN egress_flows TO data_sinks;

ALTER TABLE integration_tests RENAME COLUMN egress_flows TO data_sinks;
