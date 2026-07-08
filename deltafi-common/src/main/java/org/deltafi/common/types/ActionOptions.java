/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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

import java.util.Arrays;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ActionOptions {
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class KeyedDescription {
        private String key;
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class ContentSpec {
        private String name;
        private String mediaType;
        private String description;
    }

    public interface IOSpec {
        String getContentSummary();
        List<ContentSpec> getContentSpecs();
        String getMetadataSummary();
        List<KeyedDescription> getMetadataDescriptions();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class InputSpec implements IOSpec {
        private String contentSummary;
        private List<ContentSpec> contentSpecs;

        private String metadataSummary;
        private List<KeyedDescription> metadataDescriptions;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class OutputSpec implements IOSpec {
        private String contentSummary;
        private List<ContentSpec> contentSpecs;

        private String metadataSummary;
        private List<KeyedDescription> metadataDescriptions;

        private boolean passthrough;

        private String annotationsSummary;
        private List<KeyedDescription> annotationDescriptions;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class DescriptionWithConditions {
        private String description;
        private List<String> conditions;
    }

    @SuppressWarnings("unused")
    public static class ActionOptionsBuilder {
        public ActionOptionsBuilder filters(List<DescriptionWithConditions> filters) {
            this.filters = filters;
            return this;
        }

        public ActionOptionsBuilder filters(String... filters) {
            return filters(Arrays.stream(filters)
                    .map(description -> new DescriptionWithConditions(description, null)).toList());
        }

        public ActionOptionsBuilder errors(List<DescriptionWithConditions> errors) {
            this.errors = errors;
            return this;
        }

        public ActionOptionsBuilder errors(String... errors) {
            return errors(Arrays.stream(errors)
                    .map(description -> new DescriptionWithConditions(description, null)).toList());
        }

        public ActionOptionsBuilder notes(List<String> notes) {
            this.notes = notes;
            return this;
        }

        public ActionOptionsBuilder notes(String... notes) {
            return notes(List.of(notes));
        }
    }

    private String description;

    private InputSpec inputSpec;

    private OutputSpec outputSpec;

    private List<DescriptionWithConditions> filters;
    private List<DescriptionWithConditions> errors;
    private List<String> notes;

    private String details;
}
