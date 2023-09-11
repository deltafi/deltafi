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
package org.deltafi.test.content.loader;

import lombok.Data;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.nio.charset.Charset;
import java.util.List;
import java.util.stream.Stream;

/**
 * Helper class for building up content to store
 */
@Data
public class ContentLoader {
    private byte[] value;
    private String contentName;
    private String mediaType;

    public static ContentLoader contentLoader() {
        return new ContentLoader();
    }

    /**
     * Get the value in this ContentLoader
     * @return the value or an empty byte array if the value is null
     */
    public byte[] getValue() {
        return value != null ? value : new byte[]{};
    }

    /**
     * Set the value to given byte array
     * @param value to use for the value
     * @return this
     */
    public ContentLoader bytes(byte[] value) {
        this.value = value;
        return this;
    }

    /**
     * Set the byte array value from the given string using the default charset when getting the byte array
     * @param value to convert to a byte array
     * @return this
     */
    public ContentLoader string(String value) {
        return string(value, Charset.defaultCharset());
    }

    /**
     * Set the byte array value from the given string using the given charset when getting the byte array
     * @param value to convert to a byte array
     * @return this
     */
    public ContentLoader string(String value, Charset charset) {
        this.value = value != null ? value.getBytes(charset) : null;
        return this;
    }

    /**
     * Set the contentName that will be used when saving the content
     * @param contentName to use when saving the content
     * @return this
     */
    public ContentLoader contentName(String contentName) {
        this.contentName = contentName;
        return this;
    }

    /**
     * Set the mediaType that will be used when saving the content
     * @param mediaType to use when saving content
     * @return this
     */
    public ContentLoader mediaType(String mediaType) {
        this.mediaType = mediaType;
        return this;
    }

    /**
     * Set the value byte array from the content in the ClassPathResource at the given path
     * @param path of the classpath resource to read
     * @return this
     */
    public ContentLoader classPathResource(String path) {
        Resource resource = new ClassPathResource(path);
        this.value = readResourceAsBytes(resource);
        if (contentName == null) {
            contentName = resource.getFilename();
        }
        return this;
    }

    /**
     * Helper to read the bytes stored at each classpath location
     * @param paths zero or more classpath resources to read
     * @return a list of the bytes stored at the given paths, or null if no paths were provided
     */
    public static List<byte[]> readAsBytesFromClasspath(String ... paths) {
        if (paths == null) {
            return null;
        }

        return Stream.of(paths)
                .map(ClassPathResource::new)
                .map(ContentLoader::readResourceAsBytes)
                .toList();
    }

    /**
     * Helper to read the contents stored at each classpath location as a string using the default charset
     * @param paths zero or more classpath resources to read
     * @return a list of the strings stored at the given paths, or null if no paths were provided
     */
    public static List<String> readAsStringFromClasspath(List<String> paths) {
        return readAsStringFromClasspath(paths, Charset.defaultCharset());
    }

    /**
     * Helper to read the contents stored at each classpath location as a string using the given charset
     * @param paths zero or more classpath resources to read
     * @return a list of the strings stored at the given paths, or null if no paths were provided
     */
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
