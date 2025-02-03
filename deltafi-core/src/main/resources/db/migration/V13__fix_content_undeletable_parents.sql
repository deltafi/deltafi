UPDATE delta_files
SET content_deletable = TRUE
WHERE terminal = TRUE
AND content_deleted IS NULL
AND total_bytes > 0