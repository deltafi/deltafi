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
package org.deltafi.core.action.metadata;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A single metadata modification, applied in order to a working copy of the DeltaFile's metadata.
 * The {@code type} discriminator selects the concrete operation; each operation reads and mutates the
 * working metadata map so later operations see the effects of earlier ones.
 */
@Data
@NoArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({@JsonSubTypes.Type(value = MetadataOperation.AddOrModify.class, name = "ADD_OR_MODIFY"), @JsonSubTypes.Type(value = MetadataOperation.Copy.class, name = "COPY"), @JsonSubTypes.Type(value = MetadataOperation.DeleteKeys.class, name = "DELETE_KEYS"), @JsonSubTypes.Type(value = MetadataOperation.DeleteMatching.class, name = "DELETE_MATCHING"), @JsonSubTypes.Type(value = MetadataOperation.ReplaceKey.class, name = "REPLACE_KEY"), @JsonSubTypes.Type(value = MetadataOperation.ReplaceValue.class, name = "REPLACE_VALUE")})
public abstract class MetadataOperation {
    @JsonProperty(defaultValue = "true")
    @JsonPropertyDescription("Apply this operation only when true. May be a template resolving to true/false, " + "e.g. {{ metadata['normalize'] == 'true' }}. Defaults to true.")
    private boolean condition = true;

    /**
     * Apply this operation to the working metadata map, mutating it in place.
     *
     * @param working the running metadata map (already reflects prior operations)
     */
    public abstract void apply(Map<String, String> working);

    /**
     * Add or replace metadata key-value pairs.
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    @NoArgsConstructor
    public static class AddOrModify extends MetadataOperation {
        @JsonPropertyDescription("Key/value pairs to add or replace.")
        private Map<String, String> values = new HashMap<>();

        @Override
        public void apply(Map<String, String> working) {
            working.putAll(values);
        }
    }

    /**
     * Copy existing metadata values to one or more new keys.
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    @NoArgsConstructor
    public static class Copy extends MetadataOperation {
        @JsonPropertyDescription("Map of source key to destination key(s); comma-separate for multiple. Copies the current value; missing sources are skipped.")
        private Map<String, String> keysToCopy = new HashMap<>();

        @Override
        public void apply(Map<String, String> working) {
            keysToCopy.forEach((source, destinations) -> {
                if (working.containsKey(source)) {
                    for (String destination : destinations.split(",")) {
                        working.put(destination.trim(), working.get(source));
                    }
                }
            });
        }
    }

    /**
     * Delete an explicit list of metadata keys.
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    @NoArgsConstructor
    public static class DeleteKeys extends MetadataOperation {
        @JsonPropertyDescription("Metadata keys to delete.")
        private List<String> keysToDelete = new ArrayList<>();

        @Override
        public void apply(Map<String, String> working) {
            keysToDelete.forEach(working::remove);
        }
    }

    /**
     * Delete every metadata key whose name matches a regex.
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    @NoArgsConstructor
    public static class DeleteMatching extends MetadataOperation {
        @JsonPropertyDescription("Regex pattern where any metadata keys whose name match are deleted.")
        private String pattern;

        @Override
        public void apply(Map<String, String> working) {
            if (pattern == null) {
                return;
            }
            Pattern compiled = Pattern.compile(pattern);
            working.keySet().removeIf(key -> compiled.matcher(key).matches());
        }
    }

    /**
     * Rewrite metadata key names using a regex replacement.
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    @NoArgsConstructor
    public static class ReplaceKey extends MetadataOperation {
        @JsonPropertyDescription("Regex pattern where matching portions of each key name are replaced with the replacement (all occurrences).")
        private String pattern;

        @JsonPropertyDescription("Replacement for the matched portions; supports $1, $2 capture groups.")
        private String replacement;

        @JsonProperty(defaultValue = "true")
        @JsonPropertyDescription("When true, rename (drop the original key); when false, copy (keep both). " + "Defaults to true.")
        private boolean dropOriginal = true;

        @Override
        public void apply(Map<String, String> working) {
            if (pattern == null) {
                return;
            }
            Pattern compiled = Pattern.compile(pattern);
            // snapshot to avoid concurrent modification while rewriting
            Map<String, String> snapshot = new LinkedHashMap<>(working);
            snapshot.forEach((key, value) -> {
                String newKey = compiled.matcher(key).replaceAll(replacement == null ? "" : replacement);
                if (!newKey.equals(key)) {
                    working.put(newKey, value);
                    if (dropOriginal) {
                        working.remove(key);
                    }
                }
            });
        }
    }

    /**
     * Rewrite metadata values using a regex replacement, optionally limited to keys matching a pattern.
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    @NoArgsConstructor
    public static class ReplaceValue extends MetadataOperation {
        @JsonPropertyDescription("Optional regex pattern where only keys whose name match have their values rewritten (omit to apply to all keys).")
        private String keyPattern;

        @JsonPropertyDescription("Regex pattern where matching portions of each value are replaced with the replacement (all occurrences).")
        private String pattern;

        @JsonPropertyDescription("Replacement for the matched portions; supports $1, $2 capture groups.")
        private String replacement;

        @Override
        public void apply(Map<String, String> working) {
            if (pattern == null) {
                return;
            }
            Pattern keyMatcher = (keyPattern == null || keyPattern.isEmpty()) ? null : Pattern.compile(keyPattern);
            Pattern valueMatcher = Pattern.compile(pattern);
            String useReplacement = replacement == null ? "" : replacement;
            working.replaceAll((key, value) -> {
                if (keyMatcher != null && !keyMatcher.matcher(key).matches()) {
                    return value;
                }
                return valueMatcher.matcher(value).replaceAll(useReplacement);
            });
        }
    }
}
