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
package org.deltafi.common.nifi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/**
 * Unpacks FlowFile streams in two steps: first extracting attributes, then content.
 */
public interface FlowFileTwoStepUnpackager {
    /**
     * Reads and returns the attributes from the next FlowFile in the stream.
     *
     * @param in the InputStream to read attributes from
     * @return a map of attribute key-value pairs
     * @throws IOException if an I/O error occurs
     */
    Map<String, String> unpackageAttributes(InputStream in) throws IOException;

    /**
     * Reads the content from the current FlowFile and writes it to the output stream.
     *
     * @param in the InputStream to read content from
     * @param out the OutputStream to write content to
     * @throws IOException if an I/O error occurs
     */
    void unpackageContent(InputStream in, OutputStream out) throws IOException;

    /**
     * Returns whether there is more FlowFile data available in the stream.
     *
     * @return true if more data is available, false otherwise
     */
    boolean hasMoreData();
}
