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
package org.deltafi.common.util;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MappingIterator;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Reads JSON and YAML files into Java objects. Format is chosen by filename extension —
 * {@code .json} and {@code .jsonl} are read as JSON; everything else is read as YAML.
 */
public class JsonYamlMapper {

    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
            .build();

    private static final ObjectMapper YAML_MAPPER = YAMLMapper.builder()
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
            .build();

    private JsonYamlMapper() {}

    /** Read a file and map its content to the given type. */
    public static <T> T readValue(File file, Class<T> type) throws IOException {
        return readValue(file.toPath(), type);
    }

    /** Read a file and map its content to the given type. */
    public static <T> T readValue(Path path, Class<T> type) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return readValue(in, path.getFileName().toString(), type);
        }
    }

    /** Read a stream and map its content to the given type. The filename selects JSON vs YAML by extension. */
    public static <T> T readValue(InputStream stream, String filename, Class<T> type) throws IOException {
        return mapperFor(filename).readValue(stream, type);
    }

    /** Read a stream and map its content using the given TypeReference. The filename selects JSON vs YAML by extension. */
    public static <T> T readValue(InputStream stream, String filename, TypeReference<T> valueTypeRef) throws IOException {
        return mapperFor(filename).readValue(stream, valueTypeRef);
    }

    /** Read a file containing a sequence of values and return them as a list. */
    public static <T> List<T> readValues(Path path, Class<T> type) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return readValues(in, path.getFileName().toString(), type);
        }
    }

    /** Read a stream containing a sequence of values and return them as a list. The filename selects JSON vs YAML by extension. */
    public static <T> List<T> readValues(InputStream stream, String filename, Class<T> type) throws IOException {
        try (MappingIterator<T> it = mapperFor(filename).readerFor(type).readValues(stream)) {
            return it.readAll();
        }
    }

    /** Read content from a URL. The URL's trailing extension selects JSON vs YAML. */
    public static <T> T readValue(URL url, Class<T> type) throws IOException {
        try (InputStream in = url.openStream()) {
            return mapperFor(url.toString()).readValue(in, type);
        }
    }

    /** Read multiple documents from a URL. The URL's trailing extension selects JSON vs YAML. */
    public static <T> List<T> readValues(URL url, Class<T> type) throws IOException {
        try (InputStream in = url.openStream();
             MappingIterator<T> it = mapperFor(url.toString()).readerFor(type).readValues(in)) {
            return it.readAll();
        }
    }

    private static ObjectMapper mapperFor(String filename) {
        String name = filename == null ? "" : filename;
        return name.endsWith(".json") || name.endsWith(".jsonl") ? JSON_MAPPER : YAML_MAPPER;
    }
}
