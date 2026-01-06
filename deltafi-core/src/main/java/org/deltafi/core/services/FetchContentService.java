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
// ABOUTME: Service for fetching DeltaFile content by location pointer.
// ABOUTME: Resolves content from DeltaFile structure and streams from storage.
package org.deltafi.core.services;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.storage.s3.MissingContentException;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.types.Content;
import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.exceptions.EntityNotFound;
import org.deltafi.core.services.EntityAuthorizationResolver.AuthResponse;
import org.deltafi.core.types.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@AllArgsConstructor
public class FetchContentService {

    private final CoreAuditLogger auditLogger;
    private final ContentStorageService contentStorageService;
    private final DeltaFilesService deltaFilesService;
    private final EntityAuthorizationResolver entityAuthorizationResolver;

    public ContentResult fetchContent(ContentRequest request) throws ObjectStorageException {
        DeltaFile deltaFile = deltaFilesService.getDeltaFile(request.did());
        if (deltaFile == null) {
            throw new EntityNotFound("DeltaFile not found: " + request.did());
        }

        if (deltaFile.getContentDeleted() != null) {
            throw new EntityNotFound("Content for DeltaFile " + request.did() +
                    " has been deleted. Reason: " + deltaFile.getContentDeletedReason());
        }

        ContentMetadataPair contentAndMetadata = resolveContent(deltaFile, request);

        AuthResponse authResponse = entityAuthorizationResolver.isAuthorized(contentAndMetadata.metadata);
        if (!authResponse.authorized()) {
            auditLogger.audit("user is not authorized to view the content named '{}' in DeltaFile '{}'", contentAndMetadata.content.getName(), request.did());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, authResponse.reason());
        }

        Content content = contentAndMetadata.content();
        Content trimmedContent = applyOffsetAndSize(content, request.offset(), request.size());

        auditLogger.audit("viewed content for DID {}", request.did());

        try {
            InputStream stream = contentStorageService.load(trimmedContent);
            return new ContentResult(
                    content.getName(),
                    content.getMediaType(),
                    trimmedContent.getSize(),
                    stream
            );
        } catch (MissingContentException e) {
            log.error("Missing content for segment {}", e.getSegment(), e);
            throw new EntityNotFound("Content not found: " + e.getMessage());
        }
    }

    private ContentMetadataPair resolveContent(DeltaFile deltaFile, ContentRequest request) {
        DeltaFileFlow flow = deltaFile.getFlow(request.flowNumber());
        if (flow == null) {
            throw new EntityNotFound("Flow " + request.flowNumber() + " not found in DeltaFile " + request.did());
        }

        ContentAndMetadata contentAndMetadata = flow.contentAtOrBefore(request.actionIndex());
        List<Content> contentList = contentAndMetadata.contentList();

        if (request.contentIndex() < 0 || request.contentIndex() >= contentList.size()) {
            throw new EntityNotFound("Content " + request.contentIndex() + " not found at specified location in DeltaFile " + request.did());
        }

        return new ContentMetadataPair(contentList.get(request.contentIndex()), contentAndMetadata.metadata());
    }

    private Content applyOffsetAndSize(Content content, Long offset, Long size) {
        if (offset == null && size == null) {
            return content;
        }

        long actualOffset = offset != null ? offset : 0;
        long actualSize = size != null ? size : (content.getSize() - actualOffset);

        if (actualOffset < 0) {
            throw new IllegalArgumentException("Offset must be non-negative");
        }
        if (actualSize < 0) {
            throw new IllegalArgumentException("Size must be non-negative");
        }
        if (actualOffset + actualSize > content.getSize()) {
            throw new IllegalArgumentException("Requested range exceeds content size");
        }

        return content.subcontent(actualOffset, actualSize);
    }

    private record ContentMetadataPair(Content content, Map<String, String> metadata) {}
}
