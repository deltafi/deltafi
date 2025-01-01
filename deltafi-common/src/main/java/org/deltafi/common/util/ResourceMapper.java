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
package org.deltafi.common.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class ResourceMapper {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
            .registerModule(new JavaTimeModule())
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);

    private ResourceMapper() {}

    /**
     * Read the content of the file and map it the type
     * @param file containing the content to read
     * @param type the class type to map to
     * @return the content of the file mapped to the given type
     * @throws IOException if the content cannot be read properly
     */
    public static <T> T readValue(File file, Class<T> type) throws IOException {
        return readValue(new FileSystemResource(file), type);
    }

    /**
     * Read the content of the resource and map it the type
     * @param resource containing the content to read
     * @param type the class type to map to
     * @return the content of the file mapped to the given type
     * @throws IOException if the content cannot be read properly
     */
    public static <T> T readValue(Resource resource, Class<T> type) throws IOException {
        return getMapper(resource).readValue(resource.getInputStream(), type);
    }

    /**
     * Read the content of the resource and map it using the valueTypeRef
     * @param resource containing the content to read
     * @param valueTypeRef TypeReference to use
     * @return the content of the file mapped to the given type
     * @throws IOException if the content cannot be read properly
     */
    public static <T> T readValue(Resource resource, TypeReference<T> valueTypeRef) throws IOException {
        return getMapper(resource).readValue(resource.getInputStream(), valueTypeRef);
    }

    /**
     * Read the content of the resource and map it to a list of the given type
     * @param resource containing the content to read
     * @param type the class type to map to
     * @return the content of the file mapped to the given type
     * @throws IOException if the content cannot be read properly
     */
    public static <T> List<T> readValues(Resource resource, Class<T> type) throws IOException {
        try (MappingIterator<T> it = getMapper(resource).readerFor(type).readValues(resource.getInputStream())) {
            return it.readAll();
        }
    }

    private static ObjectMapper getMapper(Resource resource) {
        String fileName = Optional.ofNullable(resource.getFilename()).orElse("");
        return fileName.endsWith(".json") || fileName.endsWith(".jsonl") ? JSON_MAPPER : YAML_MAPPER;
    }
}
