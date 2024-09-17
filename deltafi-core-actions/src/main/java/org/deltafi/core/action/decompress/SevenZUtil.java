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
package org.deltafi.core.action.decompress;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.common.types.SaveManyContent;
import org.deltafi.core.exception.DecompressionTransformException;
import org.deltafi.core.parameters.DecompressionType;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.deltafi.core.action.decompress.BatchSizes.BATCH_BYTES;
import static org.deltafi.core.action.decompress.BatchSizes.BATCH_FILES;

@Component
@Slf4j
public class SevenZUtil {
    public static final String MEDIA_TYPE = "aapplication/x-7z-compressed";

    public static void extractSevenZ(TransformResult result, InputStream contentInputStream) throws DecompressionTransformException {
        try {
            unarchiveSevnZ(result, SevenZFile.builder()
                    .setByteArray(contentInputStream.readAllBytes())
                    .get());
        } catch (Exception e) {
            throw new DecompressionTransformException("Unable to extract 7z archive");
        }
    }

    private static void unarchiveSevnZ(TransformResult result, SevenZFile sevenZFile) throws IOException {
        SevenZArchiveEntry entry;
        List<SaveManyContent> saveManyContentList = new ArrayList<>();
        int currentBatchSize = 0;

        while ((entry = sevenZFile.getNextEntry()) != null) {
            if (entry.isDirectory()) {
                continue;
            }

            InputStream inputStream = sevenZFile.getInputStream(entry);
            byte[] fileContent = inputStream.readAllBytes();
            int fileSize = fileContent.length;

            // Check if adding this file will exceed the batch constraints
            if (!saveManyContentList.isEmpty() &&
                    (saveManyContentList.size() + 1 > BATCH_FILES) || (currentBatchSize + fileSize > BATCH_BYTES)) {
                // Save the current batch
                result.saveContent(new ArrayList<>(saveManyContentList));

                // Clear the list for the next batch and reset counters
                saveManyContentList.clear();
                currentBatchSize = 0;
            }

            saveManyContentList.add(new SaveManyContent(entry.getName(), MediaType.APPLICATION_OCTET_STREAM, fileContent));
            currentBatchSize += fileSize;
        }

        // Save any remaining files that didn't make up a full batch
        if (!saveManyContentList.isEmpty()) {
            result.saveContent(saveManyContentList);
        }
    }

    public static void extractSevenZ(List<SaveManyContent> contentList, InputStream contentInputStream) throws DecompressionTransformException {
        try {
            unarchiveSevnZ(contentList, SevenZFile.builder()
                    .setByteArray(contentInputStream.readAllBytes())
                    .get());
        } catch (Exception e) {
            throw new DecompressionTransformException("Unable to extract 7z archive");
        }
    }

    private static void unarchiveSevnZ(List<SaveManyContent> contentList, SevenZFile sevenZFile) throws IOException {
        SevenZArchiveEntry entry;
        while ((entry = sevenZFile.getNextEntry()) != null) {
            if (entry.isDirectory()) {
                continue;
            }
            InputStream inputStream = sevenZFile.getInputStream(entry);
            contentList.add(new SaveManyContent(entry.getName(), MediaType.APPLICATION_OCTET_STREAM, inputStream.readAllBytes()));
        }
    }

    public static boolean isSevenZ(String name, String mediaType, DecompressionType format) {
        return format == DecompressionType.SEVEN_Z ||
                (format == DecompressionType.AUTO &&
                        (MEDIA_TYPE.equals(mediaType) || (name != null &&
                                (name.endsWith(".7z") || name.endsWith(".7zip")))));
    }

}
