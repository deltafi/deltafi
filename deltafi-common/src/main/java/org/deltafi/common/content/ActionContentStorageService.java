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
package org.deltafi.common.content;

import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.storage.s3.ObjectStorageService;
import org.deltafi.common.types.ActionEvent;
import org.deltafi.common.types.Content;
import org.deltafi.common.types.SaveManyContent;

import java.io.InputStream;
import java.util.*;

public class ActionContentStorageService extends ContentStorageService {

    private final List<Content> savedContent;

    public ActionContentStorageService(ContentStorageService contentStorageService) {
        super(contentStorageService.objectStorageService, contentStorageService.contentBucket);
        savedContent = new ArrayList<>();
    }

    public ActionContentStorageService(ObjectStorageService objectStorageService, String bucketName) {
        super(objectStorageService, bucketName);
        savedContent = new ArrayList<>();
    }

    @Override
    public Content save(UUID did, InputStream inputStream, String name, String mediaType) throws ObjectStorageException {
        Content content = super.save(did, inputStream, name, mediaType);
        if (!content.getSegments().isEmpty()) {
            savedContent.add(content);
        }
        return content;
    }

    @Override
    public List<Content> saveMany(UUID did, List<SaveManyContent> saveManyContentList) throws ObjectStorageException {
        List<Content> contents = super.saveMany(did, saveManyContentList);
        savedContent.addAll(contents);
        return contents;
    }

    public void clear() {
        savedContent.clear();
    }

    public int savedContentSize() {
        return savedContent.size();
    }

    public int deleteUnusedContent(ActionEvent event) {
        int count = 0;
        if (!savedContent.isEmpty()) {
            Set<String> segmentsInUse = event.usedSegmentObjectNames();
            Map<String, Segment> savedSegments = savedSegmentObjectNames();

            List<Segment> toDelete = savedSegments.entrySet()
                    .stream()
                    .filter(e -> !segmentsInUse.contains(e.getKey()))
                    .map(Map.Entry::getValue)
                    .toList();

            if (!toDelete.isEmpty()) {
                count = toDelete.size();
                deleteAll(toDelete);
            }
        }
        clear();
        return count;
    }

    private Map<String, Segment> savedSegmentObjectNames() {
        Map<String, Segment> objectNames = new HashMap<>();
        if (savedContent != null) {
            for (Content content : savedContent) {
                if (content.getSize() > 0) {
                    for (Segment segment : content.getSegments()) {
                        objectNames.put(segment.objectName(), segment);
                    }
                }
            }
        }
        return objectNames;
    }
}