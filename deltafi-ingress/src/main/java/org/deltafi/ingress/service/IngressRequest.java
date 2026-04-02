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
package org.deltafi.ingress.service;

import com.fasterxml.uuid.Generators;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.types.Content;
import org.deltafi.common.types.SaveManyContent;
import org.deltafi.ingress.exception.IngressException;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.*;

@SuppressWarnings("unused")
public abstract class IngressRequest<T extends IngressRequest<T>> {

    private final DeltaFiIngressService deltaFiIngressService;
    private final Map<String, String> metadata;
    private String deltaFileName;
    private String dataSource;

    IngressRequest(DeltaFiIngressService deltaFiIngressService) {
        this.deltaFiIngressService = deltaFiIngressService;
        this.metadata = new HashMap<>();
    }

    public void execute() throws IngressException {
        try {
            deltaFiIngressService.ingress(this);
        } catch (IngressException e) {
            cleanupOnError();
            throw e;
        }
    }

    protected void cleanupOnError() throws IngressException {}

    public Map<String, String> metadata() {
        return metadata;
    }

    public String deltaFileName() {
        return deltaFileName;
    }

    public String dataSource() {
        return dataSource;
    }

    public T deltaFileName(String deltaFileName) {
        this.deltaFileName = deltaFileName;
        return me();
    }

    public T dataSource(String dataSource) {
        this.dataSource = dataSource;
        return me();
    }

    public T addMetadata(String key, String value) {
        metadata.put(key, value);
        return me();
    }

    public T addMetadata(Map<String, String> metadata) {
        if (metadata != null) {
            this.metadata.putAll(metadata);
        }
        return me();
    }

    abstract T me();


    public static class ByRef extends IngressRequest<ByRef>{

        private final UUID did;
        private final ContentStorageService contentStorageService;
        private final List<Content> content;
        private final Map<String, String> annotations;

        ByRef(DeltaFiIngressService deltaFiIngressService, ContentStorageService contentStorageService) {
            super(deltaFiIngressService);
            this.did = Generators.timeBasedEpochGenerator().generate();
            this.contentStorageService = contentStorageService;
            this.content = new ArrayList<>();
            this.annotations = new HashMap<>();
        }

        public UUID did() {
            return did;
        }

        public List<Content> content() {
            return content;
        }

        public Map<String, String> annotations() {
            return annotations;
        }

        @Override
        public void cleanupOnError() {
            this.content.forEach(contentStorageService::delete);
        }

        public ByRef addAnnotation(String key, String value) {
            annotations.put(key, value);
            return me();
        }

        public ByRef addAnnotations(Map<String, String> annotations) {
            if (annotations != null) {
                this.annotations.putAll(annotations);
            }
            return me();
        }

        /**
         * Save content to content storage and attach to the result
         * @param content   String content to store.  The entire string will be stored in content storage
         * @param name      the content name
         * @param mediaType Media type for the content being stored
         * @return The created Content
         */
        public ByRef saveContent(@NotNull String content, @NotNull String name, @NotNull String mediaType) throws ObjectStorageException {
            return saveContent(content.getBytes(), name, mediaType);
        }

        /**
         * Save content to content storage and attach to the result
         * @param bytes     Byte array of content to store.  The entire byte array will be stored in content storage
         * @param name      the content name
         * @param mediaType Media type for the content being stored
         * @return The created Content
         */
        public ByRef saveContent(@NotNull byte[] bytes, @NotNull String name, @NotNull String mediaType) throws ObjectStorageException {
            return addContent(contentStorageService.save(did, bytes, name, mediaType));
        }

        /**
         * Save content to content storage and attach to the result
         * @param stream InputStream of content to store.  The entire stream will be read into content storage, and the
         * stream may be closed by underlying processors after execution
         * @param name the content name
         * @param mediaType Media type for the content being stored
         * @return The created Content
         */
        public ByRef saveContent(@NotNull InputStream stream, @NotNull String name, @NotNull String mediaType) throws ObjectStorageException {
            return addContent(contentStorageService.save(did, stream, name, mediaType));
        }

        /**
         * Save multiple pieces of content to content storage and attach to the result
         * @param saveManyContentList a list of SaveManyContent objects containing the file name, media type, and bytes that
         * need to be stored for each content
         */
        public ByRef saveContent(@NotNull List<SaveManyContent> saveManyContentList) throws ObjectStorageException {
            return addContent(contentStorageService.saveMany(did, saveManyContentList));
        }

        @Override
        ByRef me() {
            return this;
        }

        private ByRef addContent(Content content) {
            this.content.add(content);
            return this;
        }

        private ByRef addContent(List<Content> content) {
            this.content.addAll(content);
            return this;
        }
    }

    public static class ByValue extends IngressRequest<ByValue> {

        private String mediaType;
        private byte[] bytes;

        ByValue(DeltaFiIngressService deltaFiIngressService) {
            super(deltaFiIngressService);
        }

        public String mediaType() {
            return mediaType;
        }

        public byte[] bytes() {
            return bytes;
        }

        public ByValue mediaType(String mediaType) {
            this.mediaType = mediaType;
            return this;
        }

        public ByValue bytes(String content) {
            return bytes(content.getBytes());
        }

        public ByValue bytes(String content, Charset charset) {
            return bytes(content.getBytes(charset));
        }

        public ByValue bytes(byte[] bytes) {
            this.bytes = bytes;
            return this;
        }

        @Override
        ByValue me() {
            return this;
        }
    }
}
