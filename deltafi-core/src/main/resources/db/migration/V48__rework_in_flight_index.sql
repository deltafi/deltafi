DROP INDEX idx_delta_files_stage_in_flight;
CREATE INDEX idx_delta_files_stage_in_flight
    ON delta_files (stage)
    INCLUDE (referenced_bytes)
    WHERE stage = 'IN_FLIGHT'::df_stage_enum;