create table if not exists  integration_tests (
name text not null,
description text,
plugins jsonb,
data_sources jsonb,
transformation_flows jsonb,
egress_flows jsonb,
inputs jsonb,
timeout text,
expected_delta_files jsonb,
primary key (name));

alter table test_results rename column description to test_name;
