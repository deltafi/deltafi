DROP TABLE IF EXISTS status_checks;
CREATE TABLE status_checks (
    id text NOT NULL,
    next_run_time timestamp(6) with time zone
);
