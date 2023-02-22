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
package org.deltafi.common.splitter;

import org.deltafi.common.content.ContentReference;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.content.Segment;
import org.deltafi.common.storage.s3.ObjectStorageException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Takes a ContentReference and creates a list of sub-references that point to segments of
 * the ContentReference. Each sub-reference offset and size is found based upon line terminators
 * and the {@link SplitterParams} that are passed in.
 */
public class ContentReferenceSplitter {

    static final String SEGMENT_OVERFLOW = "The segment will not fit within the max size limit";

    private final ContentStorageService contentStorageService;

    public ContentReferenceSplitter(ContentStorageService contentStorageService) {
        this.contentStorageService = contentStorageService;
    }

    /**
     * Create a list of content references based on the splitterParameters.
     * If includeHeaders is true the header line (excluding any comments) will be included in
     * all segments.
     * @param contentReference ContentReference that needs to be segmented
     * @param splitterParams params describing the rules for segmenting the input stream
     * @return a list of ContentReferences pointing to segments of the original content
     */
    public List<ContentReference> splitContentReference(ContentReference contentReference, SplitterParams splitterParams) {
        if (contentReference.getSize() == 0) {
            return List.of();
        }

        try (InputStream inputStream = contentStorageService.load(contentReference)) {
            return splitInputStream(contentReference, inputStream, splitterParams);
        } catch (IOException | ObjectStorageException e) {
            throw new SplitException("Failed to split the contentReference", e);
        }
    }

    /**
     * Create a list of content references based on the splitterParameters
     * @param contentReference original ContentReference that is splitting
     * @param contentInputStream inputStream of the content that needs to be segmented
     * @param splitterParameters params describing the rules for segmenting the input stream
     * @return a list of ContentReferences pointing to segments of the original content
     */
    List<ContentReference> splitInputStream(ContentReference contentReference, InputStream contentInputStream, SplitterParams splitterParameters) {
        try (CountingReader countingReader = new CountingReader(new InputStreamReader(contentInputStream), splitterParameters.getMaxSize())) {
            return splitData(contentReference, countingReader, splitterParameters);
        } catch (IOException exception) {
            throw new SplitException("Failed to process the inputStream", exception);
        }
    }

    List<ContentReference> splitData(ContentReference contentReference, CountingReader countingReader, SplitterParams splitterParams) throws IOException {
        List<ContentReference> subReferences = new ArrayList<>();

        List<Segment> headerSegments = findHeaderSegments(contentReference, countingReader, splitterParams);

        long maxRows = splitterParams.getMaxRows();
        long maxSize = splitterParams.getMaxSize();
        long headerSize = ContentReference.sumSegmentSizes(headerSegments);

        boolean firstSegment = true;
        long startOfChunk = 0;
        long endOfChunk = 0;
        long segmentRowCount = 0;
        while (countingReader.countBytesInNextLine() != -1) {

            long curSize = countingReader.getBytesRead() - startOfChunk;

            // the max size was decreased for stitching on the header, lower the first segment size to account for the header that is baked in
            if (firstSegment) {
                curSize -= headerSize;
            }

            if (curSize > maxSize || segmentRowCount == maxRows) {
                // edge case when the first segment includes comments that put it over the size limit
                if (endOfChunk == 0) {
                    throw new SplitException(SEGMENT_OVERFLOW);
                }

                if (firstSegment) {
                    ContentReference subRef = contentReference.subreference(startOfChunk, endOfChunk - startOfChunk);
                    subReferences.add(subRef);
                    firstSegment = false;
                    startOfChunk += subRef.getSize();
                } else {
                    List<Segment> subRefWithHeaders = new ArrayList<>(headerSegments);
                    List<Segment> subRefSegments = contentReference.subreferenceSegments(startOfChunk, endOfChunk - startOfChunk);
                    subRefWithHeaders.addAll(subRefSegments);

                    subReferences.add(new ContentReference(contentReference.getMediaType(), subRefWithHeaders));
                    startOfChunk += ContentReference.sumSegmentSizes(subRefSegments);
                }
                segmentRowCount = 0;
            }

            endOfChunk = countingReader.getBytesRead();
            segmentRowCount++;
        }

        long bytesLeftover = countingReader.getBytesRead() - startOfChunk;

        if (bytesLeftover > 0) {
            List<Segment> subRefWithHeaders = new ArrayList<>(headerSegments);
            List<Segment> subRefSegments = contentReference.subreferenceSegments(startOfChunk, bytesLeftover);
            subRefWithHeaders.addAll(subRefSegments);
            subReferences.add(new ContentReference(contentReference.getMediaType(), subRefWithHeaders));
        }

        return subReferences;
    }

    List<Segment> findHeaderSegments(ContentReference contentReference, CountingReader countingReader, SplitterParams splitterParams) throws IOException {
        if (!splitterParams.isIncludeHeaders()) {
            return List.of();
        }

        List<Segment> headerSegments = new ArrayList<>(findHeaderSegment(contentReference, countingReader, splitterParams.getCommentChars()));

        long headerSize = ContentReference.sumSegmentSizes(headerSegments);
        long headerOffset = ContentReference.minOffset(headerSegments);

        if (headerOffset + headerSize > splitterParams.getMaxSize()) {
            throw new SplitException(SEGMENT_OVERFLOW);
        }

        // Lower the maxSize to account for stitching the header segment into all ContentReferences (other than the first ContentRef that already has the header)
        splitterParams.setMaxSize(splitterParams.getMaxSize() - headerSize);
        countingReader.setMaxLineSize(splitterParams.getMaxSize());

        return headerSegments;
    }

    List<Segment> findHeaderSegment(ContentReference contentReference, CountingReader countingReader, String commentChars) throws IOException {
        boolean hasComments = commentChars != null && !commentChars.isBlank();

        long offset = 0;
        String maybeHeader = countingReader.readLine();
        while (maybeHeader != null) {
            if (!hasComments || !maybeHeader.startsWith(commentChars)) {
                return contentReference.subreferenceSegments(offset, countingReader.getBytesRead() - offset);
            } else {
                offset = countingReader.getBytesRead();
                maybeHeader = countingReader.readLine();
            }
        }

        throw new SplitException("Unable to find the header line");
    }

}
