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
package org.deltafi.core.services;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.content.Segment;
import org.deltafi.common.storage.s3.MissingContentException;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.types.Content;
import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.exceptions.EntityNotFound;
import org.deltafi.core.types.ContentRequest;
import org.deltafi.core.types.DeltaFile;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.UUID;

@Slf4j
@Service
@AllArgsConstructor
public class FetchContentService {

    private final CoreAuditLogger auditLogger;
    private final ContentStorageService contentStorageService;
    private final DeltaFilesService deltaFilesService;

    public InputStream streamContent(ContentRequest contentRequest) throws ObjectStorageException {
        Content content = contentRequest.trimmedContent();
        content.getSegments().forEach(this::audit);
        try {
            return contentStorageService.load(content);
        } catch (MissingContentException e) {
            log.error("Missing content for segment {}", e.getSegment(), e);
            Segment segment = e.getSegment();
            if (segment == null) {
                throw new EntityNotFound(e.getMessage());
            }

            throw missingContent(segment.getDid(), "Content not found: " + e.getMessage());
        } catch (ObjectStorageException e) {
            log.error("Error fetching content", e);
            throw e;
        }
    }

    private EntityNotFound missingContent(UUID uuid, String defaultMessage) {
        DeltaFile deltaFile = deltaFilesService.getDeltaFile(uuid);

        if (deltaFile == null) {
            return new EntityNotFound("Parent DeltaFile (" + uuid + ") has been deleted.");
        }

        if (deltaFile.getContentDeleted() != null) {
            return new EntityNotFound("Parent DeltaFile (" + uuid + ") content has been deleted. Reason for this deletion: " + deltaFile.getContentDeletedReason());
        }

        return new EntityNotFound(defaultMessage);
    }

    private void audit(Segment segment) {
        auditLogger.audit("viewed content for DID {}", segment.getDid());
    }

}
