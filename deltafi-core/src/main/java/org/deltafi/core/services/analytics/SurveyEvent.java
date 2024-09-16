/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.services.analytics;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.commons.lang3.StringUtils;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record SurveyEvent(OffsetDateTime timestamp, String dataSource, int files, long ingressBytes, Map<String, String> annotations) {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final Set<String> IGNORE_KEYS = Set.of("update_timestamp", "total_bytes", "annotations");

    public SurveyEvent(OffsetDateTime timestamp, @JsonAlias("flow") String dataSource, int files, @JsonAlias("ingress_bytes") long ingressBytes, Map<String, String> annotations) {
        this.timestamp = timestamp;
        this.dataSource = dataSource;
        this.files = files;
        this.ingressBytes = ingressBytes;
        this.annotations = Objects.requireNonNullElseGet(annotations, LinkedHashMap::new);
    }

    public boolean isValid() {
        return StringUtils.isNotBlank(dataSource) && files > 0 && ingressBytes > -1;
    }

    // Add unmapped fields to the annotations array (adds to any entries passed in to the annotations field)
    @JsonAnySetter
    public void addAnnotations(String key, Object value) throws JsonProcessingException {
        if (IGNORE_KEYS.contains(key)) {
            return;
        }

        switch (value) {
            case null -> annotations.put(key, null);
            case String s -> annotations.put(key, s);
            default -> annotations.put(key, MAPPER.writeValueAsString(value));
        }
    }
}
