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
package org.deltafi.core.action;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.z.ZCompressorInputStream;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.SaveManyContent;
import org.deltafi.core.exception.DecompressionTransformException;
import org.deltafi.core.parameters.DecompressionType;
import org.deltafi.core.parameters.RecursiveDecompressParameters;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Component
@Slf4j
public class RecursiveDecompress extends TransformAction<RecursiveDecompressParameters> {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        OBJECT_MAPPER.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    final int MAX_LEVELS_SAFEGUARD = 100;
    final private int BATCH_FILES = 250;
    final private int BATCH_BYTES = 100 * 1024 * 1024;

    public RecursiveDecompress() {
        super("Recursively decompresses and un-archive");
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context,
                                         @NotNull RecursiveDecompressParameters params,
                                         @NotNull TransformInput input) {

        if (params.maxLevelsCheck > MAX_LEVELS_SAFEGUARD) {
            return new ErrorResult(context, "Invalid maxLevelsCheck",
                    "maxLevelsCheck set to " + params.maxLevelsCheck + ", but system limit is " + MAX_LEVELS_SAFEGUARD);
        }
        List<SaveManyContent> contentList = new ArrayList<>();

        TransformResultType result = doDecompress(context,
                contentList,
                params.getDecompressionType(),
                input.content(0).getName(),
                input.content(0).loadBytes());

        if (result != null) {
            return result; // error result
        }

        Map<String, String> lineage = new HashMap<>();
        if (StringUtils.isNoneEmpty(params.manifestFilename)) {
            for (SaveManyContent slContent : contentList) {
                lineage.put(slContent.name(), input.content(0).getName());
            }
        }

        List<SaveManyContent> filesToProcess = contentList;
        List<SaveManyContent> ordinaryFiles = new ArrayList<>();

        boolean continueLoop = true;
        int level = 0;
        while (continueLoop) {
            boolean allFilesWereOrdinary = true;
            List<SaveManyContent> nextSetOfFiles = new ArrayList<>();

            for (SaveManyContent content : filesToProcess) {
                boolean ordinaryFileType = false;
                TransformResultType tempResult = null;

                String parentName = content.name();
                String parentLC = parentName.toLowerCase(Locale.ROOT);

                List<SaveManyContent> unarchivedFiles = new ArrayList<>();
                List<SaveManyContent> decompressedFiles = new ArrayList<>();

                // Un-archive cases (e.g. TAR) that will the parent prefix path added
                if (parentLC.endsWith(".zip")) {
                    tempResult = doDecompress(context, unarchivedFiles, DecompressionType.ZIP,
                            content.name(), content.content());
                } else if (parentLC.endsWith(".ar")) {
                    tempResult = doDecompress(context, unarchivedFiles, DecompressionType.AR,
                            content.name(), content.content());
                } else if (parentLC.endsWith(".tar")) {
                    tempResult = doDecompress(context, unarchivedFiles, DecompressionType.TAR,
                            content.name(), content.content());
                } else if (parentLC.endsWith(".tgz") || parentLC.endsWith(".tar.gz")) {
                    tempResult = doDecompress(context, unarchivedFiles, DecompressionType.TAR_GZIP,
                            content.name(), content.content());
                } else if (parentLC.endsWith(".tar.xz")) {
                    tempResult = doDecompress(context, unarchivedFiles, DecompressionType.TAR_XZ,
                            content.name(), content.content());
                } else if (parentLC.endsWith(".tar.z")) {
                    tempResult = doDecompress(context, unarchivedFiles, DecompressionType.TAR_Z,
                            content.name(), content.content());
                }
                // decompress only cases:
                else if (parentLC.endsWith(".gz")) {
                    tempResult = doDecompress(context, decompressedFiles, DecompressionType.GZIP,
                            content.name(), content.content());
                } else if (parentLC.endsWith(".xz")) {
                    tempResult = doDecompress(context, decompressedFiles, DecompressionType.XZ,
                            content.name(), content.content());
                } else if (parentLC.endsWith(".z")) {
                    tempResult = doDecompress(context, decompressedFiles, DecompressionType.Z,
                            content.name(), content.content());
                } else {
                    ordinaryFileType = true;
                    ordinaryFiles.add(content);
                }

                if (tempResult != null) {
                    return tempResult; // error result
                }

                if (!decompressedFiles.isEmpty()) {
                    for (SaveManyContent decomContent : decompressedFiles) {
                        lineage.put(decomContent.name(), content.name());
                    }
                    nextSetOfFiles.addAll(decompressedFiles);
                }

                if (!unarchivedFiles.isEmpty()) {
                    String parentDir = "";
                    int lastSlash = parentName.lastIndexOf('/');
                    if (lastSlash > 0) {
                        parentDir = content.name().substring(0, lastSlash + 1);
                    }

                    for (SaveManyContent slContent : unarchivedFiles) {
                        String fullName = parentDir + slContent.name();
                        lineage.put(fullName, parentName);
                        nextSetOfFiles.add(new SaveManyContent(fullName,
                                slContent.mediaType(),
                                slContent.content()));
                    }
                }

                if (!ordinaryFileType) {
                    allFilesWereOrdinary = false;
                }
            }

            filesToProcess.clear();

            level++;
            if (allFilesWereOrdinary) {
                // no additional recursion necessary
                continueLoop = false;
            } else if (level >= params.maxLevelsCheck) {
                // hit the limit
                continueLoop = false;
                // treat any remaining files as "ordinary"
                ordinaryFiles.addAll(nextSetOfFiles);
            } else {
                filesToProcess = nextSetOfFiles;
            }
        }

        TransformResult transformResult = new TransformResult(context);
        // Generate the manifest
        if (!lineage.isEmpty() && StringUtils.isNoneEmpty(params.manifestFilename)) {
            String jsonManifest;
            try {
                jsonManifest = OBJECT_MAPPER.writeValueAsString(lineage);
            } catch (JsonProcessingException e) {
                return new ErrorResult(context, "Error generating manifest", e);
            }
            transformResult.saveContent(jsonManifest, params.manifestFilename, MediaType.APPLICATION_JSON);
        }

        // Save all the content to storage - in batches
        if (!ordinaryFiles.isEmpty()) {
            List<SaveManyContent> batch = new ArrayList<>();
            int currentBatchSize = 0;
            for (SaveManyContent file : ordinaryFiles) {
                int fileSize = file.content().length;
                if ((batch.size() + 1 > BATCH_FILES) || (currentBatchSize + fileSize > BATCH_BYTES)) {
                    // save the existing batch
                    transformResult.saveContent(batch);
                    batch.clear();
                    currentBatchSize = 0;
                }

                batch.add(file);
                currentBatchSize += fileSize;
            }

            if (!batch.isEmpty()) {
                // save remaining items
                transformResult.saveContent(batch);
            }
        }

        return transformResult;
    }

    private TransformResultType doDecompress(ActionContext context,
                                             List<SaveManyContent> contentList,
                                             DecompressionType decompression,
                                             String name,
                                             byte[] bytes) {
        try (InputStream contentStream = new ByteArrayInputStream(bytes)) {
            try {
                switch (decompression) {
                    case TAR_GZIP -> decompressTarGzip(contentStream, contentList);
                    case TAR_Z -> decompressTarZ(contentStream, contentList);
                    case TAR_XZ -> decompressTarXZ(contentStream, contentList);
                    case ZIP -> unarchiveZip(contentStream, contentList);
                    case TAR -> unarchiveTar(contentStream, contentList);
                    case AR -> unarchiveAR(contentStream, contentList);
                    case GZIP -> decompressGzip(contentStream, contentList, name);
                    case XZ -> decompressXZ(contentStream, contentList, name);
                    case Z -> decompressZ(contentStream, contentList, name);
                    case AUTO -> decompressAutomatic(contentStream, contentList, name);
                    default -> {
                        return new ErrorResult(context, "Invalid decompression type: " + decompression).logErrorTo(log);
                    }
                }
                contentStream.close();
            } catch (DecompressionTransformException | IOException e) {
                if (e.getCause() == null) {
                    return new ErrorResult(context, e.getMessage()).logErrorTo(log);
                } else {
                    return new ErrorResult(context, e.getMessage(), e.getCause()).logErrorTo(log);
                }
            }
        } catch (IOException e) {
            return new ErrorResult(context, "Failed to load compressed binary from storage", e).logErrorTo(log);
        }
        return null;
    }

    private void decompressAutomatic(@NotNull InputStream stream, @NotNull List<SaveManyContent> contentList, @NotNull String contentName) {
        try (BufferedInputStream buffer = new BufferedInputStream(stream)) {
            try {
                String compressionType = CompressorStreamFactory.detect(buffer);
                try (BufferedInputStream decompressed = new BufferedInputStream(new CompressorStreamFactory().createCompressorInputStream(buffer))) {
                    try {
                        String archiveType = ArchiveStreamFactory.detect(decompressed);
                        try (ArchiveInputStream dearchived = new ArchiveStreamFactory().createArchiveInputStream(new BufferedInputStream(decompressed))) {
                            unarchive(dearchived, contentList);
                        } catch (IOException e) {
                            throw new DecompressionTransformException("Unable to unarchive content", e);
                        }
                    } catch (ArchiveException e) {
                        String newContentName = contentName;
                        if (contentName.endsWith(".gz") && "gz".equalsIgnoreCase(compressionType)) {
                            newContentName = contentName.substring(0, contentName.length() - 3);
                        } else if (contentName.endsWith(".xz") && "xz".equalsIgnoreCase(compressionType)) {
                            newContentName = contentName.substring(0, contentName.length() - 3);
                        } else if (contentName.endsWith(".Z") && "Z".equalsIgnoreCase(compressionType)) {
                            newContentName = contentName.substring(0, contentName.length() - 2);
                        }
                        contentList.add(new SaveManyContent(newContentName, MediaType.APPLICATION_OCTET_STREAM, decompressed.readAllBytes()));
                    }
                }
            } catch (CompressorException ex) {
                try {
                    String archiveType = ArchiveStreamFactory.detect(buffer);
                    try (ArchiveInputStream dearchived = new ArchiveStreamFactory().createArchiveInputStream(new BufferedInputStream(buffer))) {
                        unarchive(dearchived, contentList);
                    } catch (IOException e) {
                        throw new DecompressionTransformException("Unable to unarchive content", e);
                    }

                } catch (ArchiveException e) {
                    throw new DecompressionTransformException("No compression or archive formats detected");
                }
            } catch (IOException ex) {
                throw new DecompressionTransformException("Unable to decompress content", ex);
            }
        } catch (IOException ex) {
            throw new DecompressionTransformException("Unable to stream content", ex);
        }
    }

    void decompressTarGzip(@NotNull InputStream stream, @NotNull List<SaveManyContent> contentList) throws DecompressionTransformException {
        try (GzipCompressorInputStream decompressed = new GzipCompressorInputStream(stream)) {
            unarchiveTar(decompressed, contentList);
        } catch (IOException e) {
            throw new DecompressionTransformException("Unable to decompress gzip", e);
        }
    }

    void decompressTarZ(@NotNull InputStream stream, @NotNull List<SaveManyContent> contentList) throws DecompressionTransformException {
        try (ZCompressorInputStream decompressed = new ZCompressorInputStream(stream)) {
            unarchiveTar(decompressed, contentList);
        } catch (IOException e) {
            throw new DecompressionTransformException("Unable to decompress Z", e);
        }
    }

    void decompressTarXZ(@NotNull InputStream stream, @NotNull List<SaveManyContent> contentList) throws DecompressionTransformException {
        try (XZCompressorInputStream decompressed = new XZCompressorInputStream(stream)) {
            unarchiveTar(decompressed, contentList);
        } catch (IOException e) {
            throw new DecompressionTransformException("Unable to decompress xz", e);
        }
    }

    void decompressGzip(@NotNull InputStream stream, @NotNull List<SaveManyContent> contentList, String name) throws DecompressionTransformException {
        try (GzipCompressorInputStream decompressed = new GzipCompressorInputStream(stream)) {
            String newName = name;
            if (newName.toLowerCase(Locale.ROOT).endsWith(".gz")) {
                newName = name.substring(0, name.length() - 3);
            }
            contentList.add(new SaveManyContent(newName, MediaType.APPLICATION_OCTET_STREAM, decompressed.readAllBytes()));
        } catch (IOException e) {
            throw new DecompressionTransformException("Unable to decompress gzip", e);
        }
    }

    void decompressXZ(@NotNull InputStream stream, @NotNull List<SaveManyContent> contentList, String name) throws DecompressionTransformException {
        try (XZCompressorInputStream decompressed = new XZCompressorInputStream(stream)) {
            String newName = name;
            if (newName.toLowerCase(Locale.ROOT).endsWith(".xz")) {
                newName = name.substring(0, name.length() - 3);
            }
            contentList.add(new SaveManyContent(newName, MediaType.APPLICATION_OCTET_STREAM, decompressed.readAllBytes()));
        } catch (IOException e) {
            throw new DecompressionTransformException("Unable to decompress xz", e);
        }
    }

    void decompressZ(@NotNull InputStream stream, @NotNull List<SaveManyContent> contentList, String name) throws DecompressionTransformException {
        try (ZCompressorInputStream decompressed = new ZCompressorInputStream(stream)) {
            String newName = name;
            if (newName.toLowerCase(Locale.ROOT).endsWith(".z")) {
                newName = name.substring(0, name.length() - 2);
            }
            contentList.add(new SaveManyContent(newName, MediaType.APPLICATION_OCTET_STREAM, decompressed.readAllBytes()));
        } catch (IOException e) {
            throw new DecompressionTransformException("Unable to decompress Z", e);
        }
    }

    void unarchive(ArchiveInputStream archive, @NotNull List<SaveManyContent> contentList) throws IOException {
        ArchiveEntry entry;
        int currentBatchSize = 0;

        while ((entry = archive.getNextEntry()) != null) {
            if (entry.isDirectory()) continue;

            byte[] fileContent = archive.readAllBytes();
            int fileSize = fileContent.length;
            contentList.add(new SaveManyContent(entry.getName(), MediaType.APPLICATION_OCTET_STREAM, fileContent));
        }
    }

    void unarchiveTar(@NotNull InputStream stream, @NotNull List<SaveManyContent> contentList) throws DecompressionTransformException {
        try (TarArchiveInputStream archive = new TarArchiveInputStream(stream)) {
            unarchive(archive, contentList);
        } catch (IOException e) {
            throw new DecompressionTransformException("Unable to unarchive tar", e);
        }
    }

    void unarchiveAR(@NotNull InputStream stream, @NotNull List<SaveManyContent> contentList) throws DecompressionTransformException {
        try (ArArchiveInputStream archive = new ArArchiveInputStream(stream)) {
            unarchive(archive, contentList);
        } catch (IOException e) {
            throw new DecompressionTransformException("Unable to unarchive ar", e);
        }
    }

    void unarchiveZip(@NotNull InputStream stream, @NotNull List<SaveManyContent> contentList) throws DecompressionTransformException {
        try (ZipArchiveInputStream archive = new ZipArchiveInputStream(stream)) {
            unarchive(archive, contentList);
        } catch (IOException e) {
            throw new DecompressionTransformException("Unable to decompress zip", e);
        }
    }

}
