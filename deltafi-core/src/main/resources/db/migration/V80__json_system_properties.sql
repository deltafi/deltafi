-- Convert SET and LIST to JSON, un-escaping commas
WITH subquery AS (
    SELECT key, json_agg(normalized) AS normalizedJson
    FROM (
        SELECT key, regexp_replace(value, 'ESCAPED-COMMA', ',', 'g') AS normalized
        FROM (
            SELECT key, unnest(regexp_split_to_array(regexp_replace(custom_value, '\\,', 'ESCAPED-COMMA', 'g'), ',\s+')) AS value
            FROM properties
            WHERE (data_type = 'SET' OR data_type = 'LIST') AND NOT starts_with(custom_value, '[')
        )
    )
    GROUP BY key
)
UPDATE properties SET custom_value = subquery.normalizedJson FROM subquery WHERE properties.key = subquery.key;
