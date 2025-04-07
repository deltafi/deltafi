-- Move the actions description to actionOptions.description
UPDATE plugins
SET actions = (
    SELECT jsonb_agg(updated_json)
    FROM (
        SELECT jsonb_set(action_json, '{actionOptions}', jsonb_build_object('description', (SELECT action_json::json->'description'))) #- '{description}' updated_json
        FROM jsonb_array_elements(actions) action_json where NOT action_json ? 'actionOptions'
        UNION
        SELECT action_json updated_json
        FROM jsonb_array_elements(actions) action_json where action_json ? 'actionOptions'
    )
)
WHERE jsonb_array_length(actions) > 0;
