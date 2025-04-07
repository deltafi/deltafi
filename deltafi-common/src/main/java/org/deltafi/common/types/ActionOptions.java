/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.common.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Arrays;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Data
@NoArgsConstructor(force = true)
@RequiredArgsConstructor
@Builder
public class ActionOptions {
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Data
    @NoArgsConstructor(force = true)
    @RequiredArgsConstructor
    @Builder
    public static class KeyedDescription {
        private final String key;
        private final String description;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Data
    @NoArgsConstructor(force = true)
    @RequiredArgsConstructor
    @Builder
    public static class ContentSpec {
        private final String name;
        private final String mediaType;
        private final String description;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Data
    @NoArgsConstructor(force = true)
    @SuperBuilder
    public static class InputSpec {
        private final String contentSummary;
        private final List<ContentSpec> contentSpecs;

        private final String metadataSummary;
        private final List<KeyedDescription> metadataDescriptions;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Data
    @EqualsAndHashCode(callSuper = true)
    @NoArgsConstructor(force = true)
    @SuperBuilder
    public static class OutputSpec extends InputSpec {
        private final boolean passthrough;

        private final String annotationsSummary;
        private final List<KeyedDescription> annotationDescriptions;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Data
    @NoArgsConstructor(force = true)
    @RequiredArgsConstructor
    public static class DescriptionWithConditions {
        private final String description;
        private final List<String> conditions;

        public DescriptionWithConditions(String description) {
            this.description = description;
            this.conditions = null;
        }
    }

    @SuppressWarnings("unused")
    public static class ActionOptionsBuilder {
        public ActionOptionsBuilder filters(List<DescriptionWithConditions> filters) {
            this.filters = filters;
            return this;
        }

        public ActionOptionsBuilder filters(String... filters) {
            return filters(Arrays.stream(filters).map(DescriptionWithConditions::new).toList());
        }

        public ActionOptionsBuilder errors(List<DescriptionWithConditions> errors) {
            this.errors = errors;
            return this;
        }

        public ActionOptionsBuilder errors(String... errors) {
            return errors(Arrays.stream(errors).map(DescriptionWithConditions::new).toList());
        }

        public ActionOptionsBuilder notes(List<String> notes) {
            this.notes = notes;
            return this;
        }

        public ActionOptionsBuilder notes(String... notes) {
            return notes(List.of(notes));
        }
    }

    private final String description;

    private final InputSpec inputSpec;

    private final OutputSpec outputSpec;

    private final List<DescriptionWithConditions> filters;
    private final List<DescriptionWithConditions> errors;
    private final List<String> notes;

    private final String details;
}
