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
package org.deltafi.core.action.splitter;

import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.common.reader.BoundedLineReader;
import org.deltafi.core.exception.SplitException;
import org.deltafi.core.parameters.LineSplitterParameters;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Takes Content and creates a list of sub-references that point to segments of
 * the Content. Each sub-reference offset and size is found based upon line terminators
 * and the {@link LineSplitterParameters} that are passed in.
 */
public class LineSplitter {

    private static final String NAME_TEMPLATE = "${contentName}.${fragmentId}${ext}";
    private static final String CONTENT_NAME = "${contentName}";
    private static final String EXT = "${ext}";
    private static final String FRAGMENT_ID = "${fragmentId}";

    /**
     * Create a list of content based on the splitterParameters.
     * If includeHeadersInAllChunks is true the header line (excluding any comments) will be included in
     * all segments.
     * @param content Content that needs to be segmented
     * @param params params describing the rules for segmenting the input stream
     * @return a list of Content pointing to segments of the original content
     */
    public static List<ActionContent> splitContent(ActionContent content, LineSplitterParameters params) throws SplitException {
        try (InputStream inputStream = content.loadInputStream()) {
            return splitContent(content, inputStream, params);
        } catch (IOException e) {
            throw new SplitException(e);
        }
    }

    private static List<ActionContent> splitContent(ActionContent content, InputStream inputStream, LineSplitterParameters params) throws IOException, SplitException {
        BoundedLineReader reader = new BoundedLineReader(new InputStreamReader(inputStream), params.getMaxSize());

        ActionContent header = getHeader(content, reader, params);
        List<ActionContent> chunks = new ArrayList<>();

        long offset = header.getSize();
        int bytesRead = 0;
        int rowCount = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            bytesRead += line.length();
            if (!params.hasCommentChars() || !line.startsWith(params.getCommentChars())) {
                rowCount++;
            }

            if (rowCount > params.getMaxRows() || bytesRead > params.getMaxSize()) {
                if (bytesRead == line.length()) {
                    throw new SplitException("The segment will not fit within the max size limit");
                }

                ActionContent chunk;
                if (chunks.isEmpty()) {
                    // the first chunk can include the header and be written contiguously
                    chunk = content.subcontent(0, header.getSize() + bytesRead - line.length(), buildContentName(content.getName(), 0), content.getMediaType());
                } else {
                    ActionContent subcontent = content.subcontent(offset, bytesRead - line.length());
                    if (params.isIncludeHeaderInAllChunks()) {
                        chunk = header.copy();
                        chunk.append(subcontent);
                    } else {
                        chunk = subcontent;
                    }
                    chunk.setName(buildContentName(content.getName(), chunks.size()));
                }

                chunks.add(chunk);
                offset += (bytesRead - line.length());
                bytesRead = line.length();
                rowCount = 1;
            }
        }

        // anything leftover becomes the final chunk
        if (bytesRead > 0 || (chunks.isEmpty() && header.getSize() > 0)) {
            ActionContent chunk;
            ActionContent subcontent = content.subcontent(offset, content.getSize() - offset);
            if (params.isIncludeHeaderInAllChunks() || chunks.isEmpty()) {
                chunk = header.copy();
                if (subcontent.getSize() > 0) {
                    chunk.append(subcontent);
                }
            } else {
                chunk = subcontent;
            }
            chunk.setName(buildContentName(content.getName(), chunks.size()));
            chunks.add(chunk);
        }

        return chunks;
    }

    private static ActionContent getHeader(ActionContent content, BoundedLineReader reader, LineSplitterParameters params) throws IOException {
        StringBuilder header = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            header.append(line);

            if (!params.hasCommentChars() || !line.startsWith(params.getCommentChars())) {
                break;
            }
        }
        return content.subcontent(0, Math.min(content.getSize(), header.length()));
    }

    private static String buildContentName(String originalName, int idx) {
        if (originalName == null || originalName.isBlank()) {
            return String.valueOf(idx);
        }

        String filenameReplacement = originalName;
        String extReplacement = "";

        int extIdx = originalName.lastIndexOf(".");
        if (extIdx != -1) {
            filenameReplacement = originalName.substring(0, extIdx);
            extReplacement = originalName.substring(extIdx);
        }

        String childName = NAME_TEMPLATE.replace(CONTENT_NAME, filenameReplacement);
        childName = childName.replace(FRAGMENT_ID, String.valueOf(idx));
        childName = childName.replace(EXT, extReplacement);

        return childName;
    }
}
