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
package org.deltafi.core.nifi;

import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.text.StringEscapeUtils;
import org.deltafi.core.exceptions.IngressException;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class FlowFileUtil {

    private static final Pattern ENTRY_PATTERN = Pattern.compile("<entry key=\"([^\"]+)\">([^<]+)</entry>", Pattern.MULTILINE);
    public static final String FILENAME_ATTRIBUTES = "flowfile.attributes";
    public static final String FILENAME_CONTENT = "flowfile.content";

    private FlowFileUtil() {}

    public static FlowFile unarchiveFlowfileV1(@NotNull InputStream stream, Map<String, String> metadata) throws IngressException {
        try (TarArchiveInputStream archive = new TarArchiveInputStream(stream)) {

            Map<String, String> flowfileMetadata = new HashMap<>(metadata);
            final TarArchiveEntry attribEntry = archive.getNextTarEntry();
            if (Objects.isNull(attribEntry)) { throw new IngressException("No content in flowfile"); }
            if (!attribEntry.getName().equals(FILENAME_ATTRIBUTES)) {
                throw new IngressException("Expected two tar entries: "
                        + FILENAME_CONTENT + " and "
                        + FILENAME_ATTRIBUTES);
            }

            flowfileMetadata.putAll(extractFlowfileAttributes(archive));

            final TarArchiveEntry contentEntry = archive.getNextTarEntry();

            if (Objects.isNull(contentEntry) || !contentEntry.getName().equals(FILENAME_CONTENT)) {
                throw new IOException("Expected two tar entries: "
                        + FILENAME_CONTENT + " and "
                        + FILENAME_ATTRIBUTES);
            }

            byte[] content = archive.readAllBytes();
            return new FlowFile(content, flowfileMetadata);

        } catch (IOException e) {
            throw new IngressException("Unable to unarchive tar", e);
        }
    }

    private static Map<String, String> extractFlowfileAttributes(final ArchiveInputStream stream) {
        final Map<String, String> result = new HashMap<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));

        Matcher matcher = ENTRY_PATTERN.matcher(reader.lines().collect(Collectors.joining("\n")));
        while(matcher.find()) {
            final String escapedKey = matcher.group(1);
            final String escapedValue = matcher.group(2);

            result.put(StringEscapeUtils.unescapeXml(escapedKey), StringEscapeUtils.unescapeXml(escapedValue));
        }

        return result;
    }
}
