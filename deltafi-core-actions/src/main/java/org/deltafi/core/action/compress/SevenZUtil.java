/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.action.compress;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.tika.Tika;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.common.types.LineageMap;
import org.deltafi.common.types.SaveManyContent;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.deltafi.core.action.compress.BatchSizes.BATCH_BYTES;
import static org.deltafi.core.action.compress.BatchSizes.BATCH_FILES;

@Component
@Slf4j
public class SevenZUtil {
    public static void extractSevenZ(TransformResult result, LineageMap lineage, String parentName, InputStream contentInputStream) throws ArchiveException {
        String parentDir = "";
        int lastSlash = parentName.lastIndexOf('/');
        if (lastSlash > 0) {
            parentDir = parentName.substring(0, lastSlash + 1);
        }

        try {
            unarchiveSevnZ(result, lineage, parentDir, parentName, SevenZFile.builder()
                    .setByteArray(contentInputStream.readAllBytes())
                    .get());
        } catch (Exception e) {
            throw new ArchiveException("Unable to extract 7z archive");
        }
    }

    private static final Tika TIKA = new Tika();

    private static void unarchiveSevnZ(TransformResult result, LineageMap lineage,
                                       String parentDir, String parentName, SevenZFile sevenZFile) throws IOException {
        SevenZArchiveEntry entry;
        List<SaveManyContent> saveManyContentList = new ArrayList<>();
        int currentBatchSize = 0;

        while ((entry = sevenZFile.getNextEntry()) != null) {
            if (entry.isDirectory()) {
                continue;
            }

            String newContentName = lineage.add(entry.getName(), parentDir, parentName);

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

            saveManyContentList.add(new SaveManyContent(newContentName, TIKA.detect(entry.getName()), fileContent));
            currentBatchSize += fileSize;
        }

        // Save any remaining files that didn't make up a full batch
        if (!saveManyContentList.isEmpty()) {
            result.saveContent(saveManyContentList);
        }
    }

    public static boolean isSevenZ(String name, String mediaType, Format format) {
        return (format != null && format.equals(Format.SEVEN_Z)) ||
                Format.SEVEN_Z.getMediaType().equals(mediaType) ||
                (name != null && (
                        name.endsWith("." + Format.SEVEN_Z.getValue()) ||
                                name.endsWith(".7zip")));

    }

}
