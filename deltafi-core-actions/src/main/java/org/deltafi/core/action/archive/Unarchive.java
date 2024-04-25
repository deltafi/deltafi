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
package org.deltafi.core.action.archive;

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
import java.util.List;
import java.util.Set;

@Component
@Slf4j
public class Unarchive extends TransformAction<UnarchiveParameters> {
    private record DetectedArchiveData(ArchiveType archiveType, ArchiveInputStream<?> archiveInputStream) {}

    public Unarchive() {
        super("Unarchives .ar, .tar, .tar.gz, .tar.xz, .tar.Z, and .zip");
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull UnarchiveParameters params,
            @NotNull TransformInput input) {
        InputStream contentInputStream = input.content(0).loadInputStream();
        try {
            if (params.getArchiveType() == null) {
                DetectedArchiveData detectedArchiveData = detectArchiveType(contentInputStream);
                return unarchive(context, input.content(0), detectedArchiveData.archiveType(),
                        detectedArchiveData.archiveInputStream());
            }
            return unarchive(context, input.content(0), params.getArchiveType(),
                    createArchiveInputStream(params.getArchiveType(), contentInputStream));
        } catch (ArchiveException | IOException e) {
            try {
                contentInputStream.close();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
            return new ErrorResult(context, "Unable to unarchive content", e).logErrorTo(log);
        }
    }

    private DetectedArchiveData detectArchiveType(InputStream contentInputStream) throws ArchiveException {
        // Wrap in a BufferedInputStream (supporting mark/reset) so the type can be detected.
        BufferedInputStream bufferedContentInputStream = new BufferedInputStream(contentInputStream);
        try {
            return detectCompressedArchiveType(bufferedContentInputStream);
        } catch (CompressorException e) {
            return detectNoncompressedArchiveType(bufferedContentInputStream);
        }
    }

    private DetectedArchiveData detectCompressedArchiveType(BufferedInputStream bufferedContentInputStream)
            throws CompressorException, ArchiveException {
        CompressorInputStream compressorInputStream =
                CompressorStreamFactory.getSingleton().createCompressorInputStream(bufferedContentInputStream,
                        Set.of(CompressorStreamFactory.GZIP, CompressorStreamFactory.XZ, CompressorStreamFactory.Z));
        // Wrap in a BufferedInputStream (supporting mark/reset) so the type can be detected.
        BufferedInputStream bufferedCompressorInputStream = new BufferedInputStream(compressorInputStream);
        ArchiveInputStream<?> archiveInputStream;
        try {
            archiveInputStream = ArchiveStreamFactory.DEFAULT.createArchiveInputStream(bufferedCompressorInputStream);
        } catch (ArchiveException e) {
            try {
                bufferedCompressorInputStream.close();
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
            throw e;
        }
        if (!(archiveInputStream instanceof TarArchiveInputStream)) {
            String archiveType = ArchiveStreamFactory.detect(bufferedCompressorInputStream);
            try {
                archiveInputStream.close();
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
            throw new ArchiveException("Archive type in compressed content not supported: " + archiveType);
        }
        if (compressorInputStream instanceof GzipCompressorInputStream) {
            return new DetectedArchiveData(ArchiveType.TAR_GZIP, archiveInputStream);
        }
        if (compressorInputStream instanceof XZCompressorInputStream) {
            return new DetectedArchiveData(ArchiveType.TAR_XZ, archiveInputStream);
        }
        return new DetectedArchiveData(ArchiveType.TAR_Z, archiveInputStream);
    }

    private DetectedArchiveData detectNoncompressedArchiveType(BufferedInputStream bufferedContentInputStream)
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
            return new DetectedArchiveData(ArchiveType.AR, archiveInputStream);
        }
        if (archiveInputStream instanceof TarArchiveInputStream) {
            return new DetectedArchiveData(ArchiveType.TAR, archiveInputStream);
        }
        if (archiveInputStream instanceof ZipArchiveInputStream) {
            return new DetectedArchiveData(ArchiveType.ZIP, archiveInputStream);
        }
        String archiveType = ArchiveStreamFactory.detect(bufferedContentInputStream);
        try {
            archiveInputStream.close();
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
        throw new ArchiveException("Archive type not supported: " + archiveType);
    }

    private ArchiveInputStream<?> createArchiveInputStream(ArchiveType archiveType, InputStream contentInputStream)
            throws IOException {
        return switch (archiveType) {
            case AR -> new ArArchiveInputStream(contentInputStream);
            case TAR -> new TarArchiveInputStream(contentInputStream);
            case TAR_GZIP -> new TarArchiveInputStream(new GzipCompressorInputStream(contentInputStream));
            case TAR_XZ -> new TarArchiveInputStream(new XZCompressorInputStream(contentInputStream));
            case TAR_Z -> new TarArchiveInputStream(new ZCompressorInputStream(contentInputStream));
            case ZIP -> new ZipArchiveInputStream(contentInputStream);
        };
    }

    private TransformResultType unarchive(ActionContext context, ActionContent content, ArchiveType archiveType,
            ArchiveInputStream<?> archiveInputStream) {
        try (archiveInputStream) {
            TransformResult result = archiveType == ArchiveType.TAR ?
                    unarchiveInPlace(context, content, archiveInputStream) : unarchive(context, archiveInputStream);
            result.addMetadata("archiveType", archiveType.getValue());
            return result;
        } catch (IOException e) {
            return new ErrorResult(context, "Unable to unarchive content", e).logErrorTo(log);
        }
    }

    private TransformResult unarchiveInPlace(ActionContext context, ActionContent content,
            ArchiveInputStream<?> archiveInputStream) throws IOException {
        TransformResult result = new TransformResult(context);

        ArchiveEntry entry;
        while ((entry = archiveInputStream.getNextEntry()) != null) {
            if (entry.isDirectory()) {
                continue;
            }
            result.addContent(content.subcontent(archiveInputStream.getBytesRead(), entry.getSize(), entry.getName(),
                    MediaType.APPLICATION_OCTET_STREAM));
        }

        return result;
    }

    private TransformResult unarchive(ActionContext context, ArchiveInputStream<?> archiveInputStream) throws IOException {
        List<SaveManyContent> saveManyContentList = new ArrayList<>();

        ArchiveEntry entry;
        while ((entry = archiveInputStream.getNextEntry()) != null) {
            if (entry.isDirectory()) {
                continue;
            }

            saveManyContentList.add(new SaveManyContent(entry.getName(),
                    MediaType.APPLICATION_OCTET_STREAM, archiveInputStream.readAllBytes()));
        }

        TransformResult result = new TransformResult(context);
        result.saveContent(saveManyContentList);
        return result;
    }
}
