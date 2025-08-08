UPDATE flows
SET flow_status = jsonb_set(flow_status, '{valid}', 'true', true);

UPDATE flows
SET flow_status = jsonb_set(
        jsonb_set(flow_status, '{state}', '"STOPPED"', true),
        '{valid}',
        'false',
        true)
WHERE flow_status->>'state' = 'INVALID';