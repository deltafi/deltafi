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
package org.deltafi.common.json;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;

public class NdjsonDetector {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Determines if an input stream contains data conforming to the application/x-ndjson media type. The first two
     * lines of the stream are read to make the determination.
     *
     * @param inputStream the input stream to read
     * @return true if the input stream conforms to the application/x-ndjson media type, false otherwise
     */
    public static boolean isNdjson(InputStream inputStream) {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        try {
            String line = bufferedReader.readLine();
            if (line == null) {
                return false;
            }
            OBJECT_MAPPER.readTree(line);

            line = bufferedReader.readLine();
            if (line == null) {
                return true;
            }
            OBJECT_MAPPER.readTree(line);

            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
