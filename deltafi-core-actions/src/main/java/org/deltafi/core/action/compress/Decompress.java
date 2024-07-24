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
package org.deltafi.core.action.compress;

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
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.SaveManyContent;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Set;

@Component
@Slf4j
public class Decompress extends TransformAction<DecompressParameters> {
    private record DetectedFormatData(Format format, InputStream compressorInputStream,
            ArchiveInputStream<?> archiveInputStream) {}

    public Decompress() {
        super("Decompresses content from .ar, .gz, .tar, .tar.gz, .tar.xz, .tar.Z, .xz, .Z, or .zip");
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull DecompressParameters params,
            @NotNull TransformInput input) {
        if (input.getContent().isEmpty()) {
            return new ErrorResult(context, "No content found");
        }

        TransformResult result = new TransformResult(context);

        for (ActionContent content : input.getContent()) {
            InputStream contentInputStream = content.loadInputStream();
            try {
                if (params.getFormat() == null) {
                    DetectedFormatData detectedFormatData = detectFormat(contentInputStream);
                    extract(result, content, detectedFormatData.format(), detectedFormatData.compressorInputStream(),
                            detectedFormatData.archiveInputStream());
                } else {
                    extract(result, content, params.getFormat(),
                            createCompressorInputStream(params.getFormat(), contentInputStream),
                            createArchiveInputStream(params.getFormat(), contentInputStream));
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

        return result;
    }

    private DetectedFormatData detectFormat(InputStream contentInputStream) throws ArchiveException, IOException {
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

    private void extract(TransformResult result, ActionContent content, Format format,
            InputStream compressorInputStream, ArchiveInputStream<?> archiveInputStream) throws IOException {
        if (archiveInputStream != null) {
            try (archiveInputStream) {
                unarchive(result, format == Format.TAR ? content : null, archiveInputStream);
            }
        } else {
            decompress(result, content, compressorInputStream);
        }
        result.addMetadata("compressFormat", format.getValue());
    }

    private void unarchive(TransformResult result, ActionContent content, ArchiveInputStream<?> archiveInputStream)
            throws IOException {
        ArrayList<SaveManyContent> saveManyContents = new ArrayList<>();

        ArchiveEntry entry;
        while ((entry = archiveInputStream.getNextEntry()) != null) {
            if (entry.isDirectory()) {
                continue;
            }
            if (content != null) { // in place
                result.addContent(content.subcontent(archiveInputStream.getBytesRead(), entry.getSize(),
                        entry.getName(), MediaType.APPLICATION_OCTET_STREAM));
            } else {
                saveManyContents.add(new SaveManyContent(entry.getName(), MediaType.APPLICATION_OCTET_STREAM,
                        archiveInputStream.readAllBytes()));
            }
        }

        if (!saveManyContents.isEmpty()) {
            result.saveContent(saveManyContents);
        }
    }

    private void decompress(TransformResult result, ActionContent content, InputStream compressorInputStream)
            throws IOException {
        try (compressorInputStream) {
            result.saveContent(compressorInputStream, stripSuffix(content.getName()), MediaType.APPLICATION_OCTET_STREAM);
        }
    }

    private static String stripSuffix(String filename) {
        int suffixIndex = filename.lastIndexOf('.');
        return suffixIndex == -1 ? filename : filename.substring(0, suffixIndex);
    }
}
