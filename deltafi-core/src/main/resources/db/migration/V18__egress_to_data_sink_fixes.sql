UPDATE flows
SET discriminator = 'DATA_SINK'
WHERE discriminator = 'EGRESS';

UPDATE plugins
SET flow_plans = (
    SELECT jsonb_agg(
                   CASE
                       WHEN elem->>'type' = 'EGRESS'
                           THEN jsonb_set(elem, '{type}', '"DATA_SINK"')
                       ELSE elem
                       END
           )
    FROM jsonb_array_elements(flow_plans) elem
);
