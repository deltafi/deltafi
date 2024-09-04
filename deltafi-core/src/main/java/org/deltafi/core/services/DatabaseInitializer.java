/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.deltafi.core.services;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Implement Postgres-specific initialization not handled by JPA **/
@Component
public class DatabaseInitializer {
    private final JdbcTemplate jdbcTemplate;

    public DatabaseInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    @Transactional
    public void initializeDatabase() {
        String deltaFilesInFlight = "CREATE INDEX IF NOT EXISTS idx_delta_files_stage_in_flight ON delta_files ((stage = 'IN_FLIGHT'))";
        jdbcTemplate.execute(deltaFilesInFlight);

        String deltaFilesError = "CREATE INDEX IF NOT EXISTS idx_delta_files_stage_error ON delta_files ((stage = 'ERROR'))";
        jdbcTemplate.execute(deltaFilesError);

        String actionsErrorCount = "CREATE INDEX IF NOT EXISTS idx_actions_error_count ON actions (state, error_acknowledged) WHERE state = 'ERROR' AND error_acknowledged IS NULL";
        jdbcTemplate.execute(actionsErrorCount);

        String actionsColdQueued = "CREATE INDEX IF NOT EXISTS idx_actions_cold_queued ON actions (name, type) WHERE state = 'COLD_QUEUED'";
        jdbcTemplate.execute(actionsColdQueued);

        String actionsNextResume = "CREATE INDEX IF NOT EXISTS idx_actions_auto_resume_flow_sparse ON public.actions (next_auto_resume, delta_file_flow_id)\n" +
                "WHERE next_auto_resume IS NOT NULL";
        jdbcTemplate.execute(actionsNextResume);
    }
}
