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
package org.deltafi.common.content;

import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.storage.s3.ObjectStorageService;
import org.deltafi.common.types.ActionEvent;
import org.deltafi.common.types.Content;
import org.deltafi.common.types.SaveManyContent;

import java.io.InputStream;
import java.util.*;

/**
 * Tracks content saved during action execution and supports cleanup of unused content.
 */
public class ActionContentStorageService extends ContentStorageService {

    private final List<Content> savedContent;

    /**
     * Create an ActionContentStorageService that delegates to the given storage service.
     * @param contentStorageService the storage service to delegate to
     */
    public ActionContentStorageService(ContentStorageService contentStorageService) {
        super(contentStorageService.objectStorageService, contentStorageService.contentBucket);
        savedContent = new ArrayList<>();
    }

    /**
     * Create an ActionContentStorageService with the given storage backend and bucket.
     * @param objectStorageService the object storage backend
     * @param bucketName the bucket to use for content
     */
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

    /**
     * Clear the list of tracked saved content.
     */
    public void clear() {
        savedContent.clear();
    }

    /**
     * Return the number of tracked saved content items.
     * @return the number of content items saved since the last clear
     */
    public int savedContentSize() {
        return savedContent.size();
    }

    /**
     * Delete any saved content whose segments are not referenced by the given action event.
     * @param event the action event whose content references determine what is still in use
     * @return the number of segments deleted
     */
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