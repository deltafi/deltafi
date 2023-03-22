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
package org.deltafi.common.nifi;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.nifi.util.FlowFilePackagerV1;
import org.apache.nifi.util.FlowFileUnpackager;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class FlowFileUnpackagerV1Unicode implements FlowFileUnpackager {
    private int flowFilesRead = 0;

    @Override
    public Map<String, String> unpackageFlowFile(InputStream in, OutputStream out) throws IOException {
        flowFilesRead++;

        try (TarArchiveInputStream tarIn = new TarArchiveInputStream(in)) {
            TarArchiveEntry attribEntry = tarIn.getNextTarEntry();
            if ((attribEntry == null) || !attribEntry.getName().equals(FlowFilePackagerV1.FILENAME_ATTRIBUTES)) {
                throw new IOException("Expected two tar entries: " + FlowFilePackagerV1.FILENAME_CONTENT + " and " +
                        FlowFilePackagerV1.FILENAME_ATTRIBUTES);
            }
            Map<String, String> attributes = readAttributes(tarIn);

            TarArchiveEntry contentEntry = tarIn.getNextTarEntry();
            if ((contentEntry == null) || !contentEntry.getName().equals(FlowFilePackagerV1.FILENAME_CONTENT)) {
                throw new IOException("Expected two tar entries: " + FlowFilePackagerV1.FILENAME_CONTENT + " and " +
                        FlowFilePackagerV1.FILENAME_ATTRIBUTES);
            }

            byte[] buffer = new byte[512 << 10]; // 512 KB
            int bytesRead;
            while ((bytesRead = tarIn.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            out.flush();

            return attributes;
        }
    }

    private static final Pattern ENTRY_PATTERN = Pattern.compile("<entry key=\"([^\"]+)\">([^<]+)</entry>",
            Pattern.MULTILINE);

    private Map<String, String> readAttributes(TarArchiveInputStream stream) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));

        Map<String, String> attributes = new HashMap<>();
        Matcher entryMatcher = ENTRY_PATTERN.matcher(reader.lines().collect(Collectors.joining("\n")));
        while (entryMatcher.find()) {
            attributes.put(StringEscapeUtils.unescapeXml(entryMatcher.group(1)),
                    StringEscapeUtils.unescapeXml(entryMatcher.group(2)));
        }
        return attributes;
    }

    @Override
    public boolean hasMoreData() {
        return flowFilesRead == 0;
    }
}
