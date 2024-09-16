/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ActionContentStorageService extends ContentStorageService {

    private final List<Content> savedContent;

    public ActionContentStorageService(ContentStorageService contentStorageService) {
        super(contentStorageService.objectStorageService);
        savedContent = new ArrayList<>();
    }

    public ActionContentStorageService(ObjectStorageService objectStorageService) {
        super(objectStorageService);
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

    public void publishSavedContent(ActionEvent event) {
        event.setSavedContent(savedContent);
    }

}