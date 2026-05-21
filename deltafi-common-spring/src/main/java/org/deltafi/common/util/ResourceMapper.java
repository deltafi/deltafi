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

import com.fasterxml.jackson.core.type.TypeReference;
import org.deltafi.common.constant.DeprecatedSince;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Optional;

/**
 * Backward-compatible facade for the original Spring-coupled {@code ResourceMapper}. Retains the
 * original API surface — including the Spring {@link Resource} overloads — so existing callers that
 * depend on {@code deltafi-common-spring} continue to compile unchanged. Every method delegates to
 * the framework-agnostic {@link JsonYamlMapper} in {@code deltafi-common}.
 *
 * <p>In 3.0 this class is removed. Callers should use {@link JsonYamlMapper} directly with
 * {@link File}, {@link java.nio.file.Path}, {@link URL}, or {@code InputStream + filename}
 * overloads; callers holding a Spring {@link Resource} should pass
 * {@code resource.getInputStream()} and the resource filename.
 *
 * @deprecated Use {@link JsonYamlMapper}. Will be removed in 3.0.
 */
@Deprecated(since = DeprecatedSince.VERSION_2_55_1, forRemoval = true)
public final class ResourceMapper {

    private ResourceMapper() {}

    /** Read a file and map its content to the given type. */
    public static <T> T readValue(File file, Class<T> type) throws IOException {
        return JsonYamlMapper.readValue(file, type);
    }

    /** Read a Spring {@link Resource} and map its content to the given type. */
    public static <T> T readValue(Resource resource, Class<T> type) throws IOException {
        return JsonYamlMapper.readValue(resource.getInputStream(), filename(resource), type);
    }

    /** Read a Spring {@link Resource} and map its content using the given TypeReference. */
    public static <T> T readValue(Resource resource, TypeReference<T> valueTypeRef) throws IOException {
        return JsonYamlMapper.readValue(resource.getInputStream(), filename(resource), valueTypeRef);
    }

    /** Read a Spring {@link Resource} containing a sequence of values and return them as a list. */
    public static <T> List<T> readValues(Resource resource, Class<T> type) throws IOException {
        return JsonYamlMapper.readValues(resource.getInputStream(), filename(resource), type);
    }

    /** Read content from a URL. The URL's trailing extension selects JSON vs YAML. */
    public static <T> T readValue(URL url, Class<T> type) throws IOException {
        return JsonYamlMapper.readValue(url, type);
    }

    /** Read multiple documents from a URL. The URL's trailing extension selects JSON vs YAML. */
    public static <T> List<T> readValues(URL url, Class<T> type) throws IOException {
        return JsonYamlMapper.readValues(url, type);
    }

    private static String filename(Resource resource) {
        return Optional.ofNullable(resource.getFilename()).orElse("");
    }
}
