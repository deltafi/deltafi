
DELETE FROM annotation_values
WHERE id IN (
    SELECT id FROM (
                       SELECT id, value_text,
                              ROW_NUMBER() OVER (PARTITION BY value_text ORDER BY id) as row_num
                       FROM annotation_values
                   ) t
    WHERE t.row_num > 1
);
DROP INDEX idx_annotation_values_value_text;
CREATE UNIQUE INDEX idx_annotation_values_value_text ON annotation_values(value_text);
