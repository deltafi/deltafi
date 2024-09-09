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
package org.deltafi.core.repo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Repository
@Transactional
public interface EgressFlowRepo extends FlowRepo, EgressFlowRepoCustom {
    @Modifying
    @Query(value = "UPDATE flows SET expected_annotations = cast(:expectedAnnotations AS jsonb) WHERE name = :flowName AND type = 'EGRESS'",
            nativeQuery = true)
    int updateExpectedAnnotations(String flowName, String expectedAnnotations);

    default int updateExpectedAnnotations(String flowName, Set<String> expectedAnnotations) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            String jsonExpectedAnnotations = objectMapper.writeValueAsString(expectedAnnotations);
            return updateExpectedAnnotations(flowName, jsonExpectedAnnotations);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error converting expectedAnnotations to JSON", e);
        }
    }
}
