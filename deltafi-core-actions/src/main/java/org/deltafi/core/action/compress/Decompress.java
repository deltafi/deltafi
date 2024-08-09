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
package org.deltafi.core.action.compress;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.z.ZCompressorInputStream;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.LineageMap;
import org.deltafi.common.types.SaveManyContent;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.deltafi.core.action.compress.BatchSizes.BATCH_BYTES;
import static org.deltafi.core.action.compress.BatchSizes.BATCH_FILES;

@Component
@Slf4j
public class Decompress extends TransformAction<DecompressParameters> {
    static final int MAX_LEVELS_SAFEGUARD = 100;
    private static final String COMPRESS_FORMAT = "compressFormat";

    public Decompress() {
        super("Decompresses content from .ar, .gz, .7z, .tar, .tar.gz, .tar.xz, .tar.Z, .xz, .Z, or .zip.");
    }

    private static String stripSuffix(String filename) {
        int suffixIndex = filename.lastIndexOf('.');
        return suffixIndex == -1 ? filename : filename.substring(0, suffixIndex);
    }

    private static boolean recursiveCandidate(String filename) {
        String filenameLC = filename.toLowerCase(Locale.ROOT);
        return filename.endsWith(".ar") ||
                filenameLC.endsWith(".gz") ||
                filenameLC.endsWith(".7z") ||
                filenameLC.endsWith(".7zip") ||
                filenameLC.endsWith(".tar") ||
                filenameLC.endsWith(".tgz") ||
                filenameLC.endsWith(".xz") ||
                filenameLC.endsWith(".z") ||
                filenameLC.endsWith(".zip");
    }

    private static Format formatFromName(String filename) {
        String filenameLC = filename.toLowerCase(Locale.ROOT);
        if (filename.endsWith(".ar")) {
            return Format.AR;
        } else if (filenameLC.endsWith(".tar")) {
            return Format.TAR;
        } else if (filenameLC.endsWith(".tar.gz") || filenameLC.endsWith(".tgz")) {
            return Format.TAR_GZIP;
        } else if (filenameLC.endsWith("tar.xz")) {
            return Format.TAR_XZ;
        } else if (filenameLC.endsWith(".tar.z")) {
            return Format.TAR_Z;
        } else if (filenameLC.endsWith(".zip")) {
            return Format.ZIP;
        } else if (filenameLC.endsWith(".gz")) {
            return Format.GZIP;
        } else if (filenameLC.endsWith(".7z") || filenameLC.endsWith(".7zip")) {
            return Format.SEVEN_Z;
        } else if (filenameLC.endsWith(".xz")) {
            return Format.XZ;
        } else if (filenameLC.endsWith(".z")) {
            return Format.Z;
        }
        return null;
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull DecompressParameters params,
                                         @NotNull TransformInput input) {
        if (input.getContent().isEmpty()) {
            return new ErrorResult(context, "No content found");
        }

        LineageMap lineage = new LineageMap();
        TransformResultType result = (params.getMaxRecursionLevels() == 0)
                ? basicDecompress(context, params, input, lineage)
                : recursiveDecompress(context, params, input, lineage);

        if (result instanceof TransformResult) {
            // Generate the lineage
            if (!lineage.isEmpty() && StringUtils.isNoneEmpty(params.getLineageFilename())) {
                String jsonLineage;
                try {
                    jsonLineage = lineage.writeMapAsString();
                } catch (JsonProcessingException e) {
                    return new ErrorResult(context, "Cannot write lineage JSON", e);
                }
                ((TransformResult) result).saveContent(jsonLineage, params.getLineageFilename(), MediaType.APPLICATION_JSON);
            }
        }
        return result;
    }

    private TransformResultType basicDecompress(@NotNull ActionContext context, @NotNull DecompressParameters params,
                                                @NotNull TransformInput input, LineageMap lineage) {
        TransformResult result = new TransformResult(context);
        if (params.isRetainExistingContent()) {
            result.addContent(input.getContent());
        }

        String compressFormatName = "";
        for (ActionContent content : input.getContent()) {
            InputStream contentInputStream = content.loadInputStream();
            try {
                if (SevenZUtil.isSevenZ(content.getName(), content.getMediaType(), params.getFormat())) {
                    SevenZUtil.extractSevenZ(result, lineage, content.getName(), contentInputStream);
                    compressFormatName = Format.SEVEN_Z.getValue();
                } else if (params.getFormat() == null) {
                    DetectedFormatData detectedFormatData = detectFormat(contentInputStream);
                    extract(result, lineage, content, detectedFormatData.format(), detectedFormatData.compressorInputStream(),
                            detectedFormatData.archiveInputStream());
                    compressFormatName = detectedFormatData.format().getValue();
                } else {
                    extract(result, lineage, content, params.getFormat(),
                            createCompressorInputStream(params.getFormat(), contentInputStream),
                            createArchiveInputStream(params.getFormat(), contentInputStream));
                    compressFormatName = params.getFormat().getValue();
                }
            } catch (ArchiveException | IOException e) {
                try {
                    contentInputStream.close();
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
                return new ErrorResult(context, "Unable to decompress content", e).logErrorTo(log);
            }
        }

        if (!compressFormatName.isEmpty()) {
            result.addMetadata(COMPRESS_FORMAT, compressFormatName);
        }

        return result;
    }

    private TransformResultType recursiveDecompress(@NotNull ActionContext context, @NotNull DecompressParameters params,
                                                    @NotNull TransformInput input, LineageMap lineage) {
        int recursionLevel = 0;
        boolean recursionNeeded = true;

        if (params.getMaxRecursionLevels() > MAX_LEVELS_SAFEGUARD) {
            return new ErrorResult(context, "The 'maxLevelsCheck' must not exceed the system limit of: "
                    + MAX_LEVELS_SAFEGUARD);
        }

        if (params.getFormat() != null || params.isRetainExistingContent()) {
            return new ErrorResult(context, "The 'format' and 'retainExistingContent'"
                    + " parameters may not be used in recursive mode");
        }

        TransformResult finalResult = new TransformResult(context);
        List<ActionContent> processingList = input.getContent();

        while (recursionNeeded && (recursionLevel <= params.getMaxRecursionLevels())) {
            recursionLevel++;
            recursionNeeded = false;

            TransformResult result = new TransformResult(context);

            for (ActionContent content : processingList) {
                Format format = formatFromName(content.getName());
                if (format == null) {
                    // This content does not require further processing
                    finalResult.addContent(content);
                    continue;
                }

                InputStream contentInputStream = content.loadInputStream();
                try {
                    if (format == Format.SEVEN_Z) {
                        SevenZUtil.extractSevenZ(result, lineage, content.getName(), contentInputStream);
                    } else {
                        extract(result, lineage, content, format,
                                createCompressorInputStream(format, contentInputStream),
                                createArchiveInputStream(format, contentInputStream));
                    }
                } catch (IOException | ArchiveException e) {
                    try {
                        contentInputStream.close();
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                    return new ErrorResult(context, "Unable to decompress content", e).logErrorTo(log);
                }
            }

            processingList = result.getContent();

            // See if any new content extracted may require
            // an additional round of decompression processing.
            for (ActionContent content : result.getContent()) {
                if (recursiveCandidate(content.getName())) {
                    recursionNeeded = true;
                    break;
                }
            }
        }

        finalResult.addContent(processingList);

        return finalResult;
    }


    private DetectedFormatData detectFormat(InputStream contentInputStream) throws ArchiveException {
        // Wrap in a BufferedInputStream (supporting mark/reset) so the type can be detected.
        BufferedInputStream bufferedContentInputStream = new BufferedInputStream(contentInputStream);
        try {
            CompressorInputStream compressorInputStream =
                    CompressorStreamFactory.getSingleton().createCompressorInputStream(bufferedContentInputStream,
                            Set.of(CompressorStreamFactory.GZIP, CompressorStreamFactory.XZ, CompressorStreamFactory.Z));
            return detectCompressedArchiveType(compressorInputStream);
        } catch (CompressorException e) {
            return detectNoncompressedArchiveType(bufferedContentInputStream);
        }
    }

    private DetectedFormatData detectCompressedArchiveType(CompressorInputStream compressorInputStream)
            throws ArchiveException {
        // Wrap in a BufferedInputStream (supporting mark/reset) so the type can be detected.
        BufferedInputStream bufferedCompressorInputStream = new BufferedInputStream(compressorInputStream);
        ArchiveInputStream<?> archiveInputStream;

        try {
            archiveInputStream = ArchiveStreamFactory.DEFAULT.createArchiveInputStream(bufferedCompressorInputStream);
        } catch (ArchiveException e) {
            // Not a compressed archive stream, just a compressed stream
            Format format = switch (compressorInputStream) {
                case GzipCompressorInputStream ignored -> Format.GZIP;
                case XZCompressorInputStream ignored -> Format.XZ;
                default -> Format.Z;
            };
            return new DetectedFormatData(format, bufferedCompressorInputStream, null);
        }

        if (archiveInputStream instanceof TarArchiveInputStream) {
            Format format = switch (compressorInputStream) {
                case GzipCompressorInputStream ignored -> Format.TAR_GZIP;
                case XZCompressorInputStream ignored -> Format.TAR_XZ;
                default -> Format.TAR_Z;
            };
            return new DetectedFormatData(format, null, archiveInputStream);
        }

        String archiveType = ArchiveStreamFactory.detect(bufferedCompressorInputStream);
        try {
            archiveInputStream.close();
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
        throw new ArchiveException("Archive type in compressed content not supported: " + archiveType);
    }

    private DetectedFormatData detectNoncompressedArchiveType(BufferedInputStream bufferedContentInputStream)
            throws ArchiveException {
        ArchiveInputStream<?> archiveInputStream;

        try {
            archiveInputStream = ArchiveStreamFactory.DEFAULT.createArchiveInputStream(bufferedContentInputStream);
        } catch (ArchiveException ae) {
            try {
                bufferedContentInputStream.close();
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
            throw ae;
        }

        if (archiveInputStream instanceof ArArchiveInputStream) {
            return new DetectedFormatData(Format.AR, null, archiveInputStream);
        }
        if (archiveInputStream instanceof TarArchiveInputStream) {
            return new DetectedFormatData(Format.TAR, null, archiveInputStream);
        }
        if (archiveInputStream instanceof ZipArchiveInputStream) {
            return new DetectedFormatData(Format.ZIP, null, archiveInputStream);
        }

        String archiveType = ArchiveStreamFactory.detect(bufferedContentInputStream);
        try {
            archiveInputStream.close();
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
        throw new ArchiveException("Archive type not supported: " + archiveType);
    }

    private CompressorInputStream createCompressorInputStream(Format format, InputStream contentInputStream)
            throws IOException {
        return switch (format) {
            case GZIP -> new GzipCompressorInputStream(contentInputStream);
            case XZ -> new XZCompressorInputStream(contentInputStream);
            case Z -> new ZCompressorInputStream(contentInputStream);
            default -> null;
        };
    }

    private ArchiveInputStream<?> createArchiveInputStream(Format format, InputStream contentInputStream)
            throws IOException {
        return switch (format) {
            case AR -> new ArArchiveInputStream(contentInputStream);
            case TAR -> new TarArchiveInputStream(contentInputStream);
            case TAR_GZIP -> new TarArchiveInputStream(new GzipCompressorInputStream(contentInputStream));
            case TAR_XZ -> new TarArchiveInputStream(new XZCompressorInputStream(contentInputStream));
            case TAR_Z -> new TarArchiveInputStream(new ZCompressorInputStream(contentInputStream));
            case ZIP -> new ZipArchiveInputStream(contentInputStream);
            default -> null;
        };
    }

    private void extract(TransformResult result, LineageMap lineage, ActionContent content, Format format,
                         InputStream compressorInputStream, ArchiveInputStream<?> archiveInputStream) throws IOException {
        String parentName = content.getName();
        String parentDir = "";
        int lastSlash = parentName.lastIndexOf('/');
        if (lastSlash > 0) {
            parentDir = parentName.substring(0, lastSlash + 1);
        }

        if (archiveInputStream != null) {
            try (archiveInputStream) {
                unarchive(result, lineage, parentDir, parentName, format == Format.TAR ? content : null, archiveInputStream);
            }
        } else {
            decompress(result, lineage, content, compressorInputStream);
        }
    }

    private void unarchive(TransformResult result, LineageMap lineage,
                           String parentDir, String parentName, ActionContent content, ArchiveInputStream<?> archiveInputStream)
            throws IOException {
        ArrayList<SaveManyContent> saveManyBatch = new ArrayList<>();
        int currentBatchSize = 0;

        ArchiveEntry entry;
        while ((entry = archiveInputStream.getNextEntry()) != null) {
            if (entry.isDirectory()) {
                continue;
            }

            String newContentName = lineage.add(entry.getName(), parentDir, parentName);

            if (content != null) { // in place
                result.addContent(content.subcontent(archiveInputStream.getBytesRead(), entry.getSize(),
                        newContentName, MediaType.APPLICATION_OCTET_STREAM));
            } else {
                SaveManyContent file = new SaveManyContent(newContentName, MediaType.APPLICATION_OCTET_STREAM,
                        archiveInputStream.readAllBytes());
                int fileSize = file.content().length;

                if ((saveManyBatch.size() + 1 > BATCH_FILES) || (currentBatchSize + fileSize > BATCH_BYTES)) {
                    // save the existing batch
                    result.saveContent(saveManyBatch);
                    saveManyBatch.clear();
                    currentBatchSize = 0;
                }

                saveManyBatch.add(file);
                currentBatchSize += fileSize;
            }
        }

        if (!saveManyBatch.isEmpty()) {
            // save remaining items
            result.saveContent(saveManyBatch);
        }
    }

    private void decompress(TransformResult result, LineageMap lineage, ActionContent content, InputStream compressorInputStream)
            throws IOException {
        try (compressorInputStream) {
            String withoutSuffix = stripSuffix(content.getName());
            String newContentName = lineage.add(withoutSuffix, "", content.getName());
            result.saveContent(compressorInputStream, newContentName, MediaType.APPLICATION_OCTET_STREAM);
        }
    }

    private record DetectedFormatData(Format format, InputStream compressorInputStream,
                                      ArchiveInputStream<?> archiveInputStream) {
    }
}
