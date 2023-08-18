/**
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
package org.deltafi.test.content.loader;

import lombok.Data;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.nio.charset.Charset;
import java.util.List;
import java.util.stream.Stream;

@Data
public class ContentLoader {
    private byte[] value;
    private String contentName;
    private String mediaType;

    public static ContentLoader contentLoader() {
        return new ContentLoader();
    }

    public ContentLoader bytes(byte[] value) {
        this.value = value;
        return this;
    }

    public ContentLoader string(String value) {
        return string(value, Charset.defaultCharset());
    }

    public ContentLoader string(String value, Charset charset) {
        this.value = value != null ? value.getBytes(charset) : null;
        return this;
    }

    public ContentLoader contentName(String contentName) {
        this.contentName = contentName;
        return this;
    }

    public ContentLoader mediaType(String mediaType) {
        this.mediaType = mediaType;
        return this;
    }

    public ContentLoader classPathResource(String path) {
        Resource resource = new ClassPathResource(path);
        this.value = readResourceAsBytes(resource);
        if (contentName == null) {
            contentName = resource.getFilename();
        }
        return this;
    }

    public static List<byte[]> readAsBytesFromClasspath(String ... paths) {
        if (paths == null) {
            return null;
        }

        return Stream.of(paths)
                .map(ClassPathResource::new)
                .map(ContentLoader::readResourceAsBytes)
                .toList();
    }

    public static List<String> readAsStringFromClasspath(List<String> paths) {
        return readAsStringFromClasspath(paths, Charset.defaultCharset());
    }

    public static List<String> readAsStringFromClasspath(List<String> paths, Charset charset) {
        if (paths == null) {
            return null;
        }

        return paths.stream()
                .map(ClassPathResource::new)
                .map(resource -> readAsStringFromClasspath(resource, charset))
                .toList();
    }

    private static String readAsStringFromClasspath(ClassPathResource resource, Charset charset) {
        try {
            return resource.getContentAsString(charset);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] readResourceAsBytes(Resource resource) {
        try {
            return resource.getContentAsByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
