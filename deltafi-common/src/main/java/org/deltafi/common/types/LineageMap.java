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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LineageMap {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        OBJECT_MAPPER.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    private final Map<String, LineageData> lineage;

    public LineageMap() {
        lineage = new HashMap<>();
    }

    public String writeMapAsString() throws JsonProcessingException {
        return OBJECT_MAPPER.writeValueAsString(lineage);
    }

    public void readMapFromString(String json) throws JsonProcessingException {
        lineage.clear();
        lineage.putAll(OBJECT_MAPPER.readValue(json, new TypeReference<>() {
        }));
    }

    public boolean isEmpty() {
        return lineage.isEmpty();
    }

    public int size() {
        return lineage.size();
    }

    public LineageData findEntry(String contentName) {
        return lineage.get(contentName);
    }

    public LineageData findParentEntry(String contentName) {
        if (lineage.containsKey(contentName)) {
            return findEntry(findEntry(contentName).getParentContentName());
        }
        return null;
    }

    public List<String> findAllFullNameMatches(String fullName) {
        return lineage.entrySet().stream()
                .filter(e -> e.getValue().fullName.equals(fullName))
                .map(e -> e.getKey())
                .toList();
    }

    public String add(String filename, String path, String parent) {
        String fullName = path + filename;
        String key = fullName;
        boolean altKey = false;
        if (lineage.containsKey(fullName)) {
            key = computeAlternateKey(fullName, parent);
            altKey = true;
        }
        lineage.put(key, new LineageData(fullName, parent, altKey));
        return key;
    }

    private String computeAlternateKey(String fullName, String parent) {
        if (!lineage.containsKey(parent + ":" + fullName)) {
            return parent + ":" + fullName;
        } else {
            return parent + ":" + fullName + "_" + UUID.randomUUID().toString().substring(0, 7);
        }
    }
}
