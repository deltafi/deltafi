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

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.CompressorException;
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
import org.deltafi.core.exception.DecompressionTransformException;
import org.deltafi.core.parameters.DecompressionTransformParameters;
import org.deltafi.core.parameters.DecompressionType;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Component
@Slf4j
public class DecompressionTransformAction extends TransformAction<DecompressionTransformParameters> {

    public DecompressionTransformAction() {
        super("Decompresses .tgz, .tar.Z, .tar.xz, .zip, .tar, .ar, .gz, .xz, .Z");
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context,
                                         @NotNull DecompressionTransformParameters params,
                                         @NotNull TransformInput input) {
        TransformResult result = new TransformResult(context);
        String decompressionType = params.getDecompressionType().getValue();
        ActionContent content = input.content(0);

        try (InputStream contentStream = content.loadInputStream()) {
            try {
                switch (params.getDecompressionType()) {
                    case TAR_GZIP -> decompressTarGzip(contentStream, result);
                    case TAR_Z -> decompressTarZ(contentStream, result);
                    case TAR_XZ -> decompressTarXZ(contentStream, result);
                    case ZIP -> unarchiveZip(contentStream, result);
                    case TAR -> inPlaceUnarchiveTar(contentStream, result, content);
                    case AR -> unarchiveAR(contentStream, result);
                    case GZIP -> decompressGzip(contentStream, result, content.getName());
                    case XZ -> decompressXZ(contentStream, result, content.getName());
                    case Z -> decompressZ(contentStream, result, content.getName());
                    case AUTO ->
                            decompressionType = decompressAutomatic(contentStream, result, content, content.getName());
                    default -> {
                        return new ErrorResult(context, "Invalid decompression type: " + params.getDecompressionType()).logErrorTo(log);
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
        result.addMetadata("decompressionType", decompressionType);

        return result;
    }

    private String decompressAutomatic(@NotNull InputStream stream, @NotNull TransformResult result, @NotNull ActionContent content, String contentName) {
        String decompressionType;
        try (BufferedInputStream buffer = new BufferedInputStream(stream)) {
            try {
                String compressionType = CompressorStreamFactory.detect(buffer);
                try (BufferedInputStream decompressed = new BufferedInputStream(new CompressorStreamFactory().createCompressorInputStream(buffer))) {
                    try {
                        String archiveType = ArchiveStreamFactory.detect(decompressed);
                        try (ArchiveInputStream dearchived = new ArchiveStreamFactory().createArchiveInputStream(new BufferedInputStream(decompressed))) {
                            unarchive(dearchived, result);
                        } catch (IOException e) {
                            throw new DecompressionTransformException("Unable to unarchive content", e);
                        }
                        decompressionType = String.join(".", archiveType, compressionType);
                    } catch (ArchiveException e) {
                        result.saveContent(decompressed, contentName, MediaType.APPLICATION_OCTET_STREAM);
                        decompressionType = compressionType;
                    }
                }
            } catch (CompressorException ex) {
                try {
                    String archiveType = ArchiveStreamFactory.detect(buffer);
                    if (DecompressionType.TAR.getValue().equals(archiveType)) {
                        inPlaceUnarchiveTar(new BufferedInputStream(buffer), result, content);
                        decompressionType = archiveType;
                    } else {
                        try (ArchiveInputStream dearchived = new ArchiveStreamFactory().createArchiveInputStream(new BufferedInputStream(buffer))) {
                            unarchive(dearchived, result);
                            decompressionType = archiveType;
                        } catch (IOException e) {
                            throw new DecompressionTransformException("Unable to unarchive content", e);
                        }
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

        return decompressionType;
    }

    void decompressTarGzip(@NotNull InputStream stream, @NotNull TransformResult result) throws DecompressionTransformException {
        try (GzipCompressorInputStream decompressed = new GzipCompressorInputStream(stream)) {
            unarchiveTar(decompressed, result);
        } catch (IOException e) {
            throw new DecompressionTransformException("Unable to decompress gzip", e);
        }
    }

    void decompressTarZ(@NotNull InputStream stream, @NotNull TransformResult result) throws DecompressionTransformException {
        try (ZCompressorInputStream decompressed = new ZCompressorInputStream(stream)) {
            unarchiveTar(decompressed, result);
        } catch (IOException e) {
            throw new DecompressionTransformException("Unable to decompress Z", e);
        }
    }

    void decompressTarXZ(@NotNull InputStream stream, @NotNull TransformResult result) throws DecompressionTransformException {
        try (XZCompressorInputStream decompressed = new XZCompressorInputStream(stream)) {
            unarchiveTar(decompressed, result);
        } catch (IOException e) {
            throw new DecompressionTransformException("Unable to decompress xz", e);
        }
    }

    void decompressGzip(@NotNull InputStream stream, @NotNull TransformResult result, String name) throws DecompressionTransformException {
        try (GzipCompressorInputStream decompressed = new GzipCompressorInputStream(stream)) {
            result.saveContent(decompressed, name, MediaType.APPLICATION_OCTET_STREAM);
        } catch (IOException e) {
            throw new DecompressionTransformException("Unable to decompress gzip", e);
        }
    }

    void decompressXZ(@NotNull InputStream stream, @NotNull TransformResult result, String name) throws DecompressionTransformException {
        try (XZCompressorInputStream decompressed = new XZCompressorInputStream(stream)) {
            result.saveContent(decompressed, name, MediaType.APPLICATION_OCTET_STREAM);
        } catch (IOException e) {
            throw new DecompressionTransformException("Unable to decompress xz", e);
        }
    }

    void decompressZ(@NotNull InputStream stream, @NotNull TransformResult result, String name) throws DecompressionTransformException {
        try (ZCompressorInputStream decompressed = new ZCompressorInputStream(stream)) {
            result.saveContent(decompressed, name, MediaType.APPLICATION_OCTET_STREAM);
        } catch (IOException e) {
            throw new DecompressionTransformException("Unable to decompress Z", e);
        }
    }

    void unarchive(ArchiveInputStream archive, @NotNull TransformResult result) throws IOException {
        ArchiveEntry entry;
        List<SaveManyContent> saveManyContentList = new ArrayList<>();
        while ((entry = archive.getNextEntry()) != null) {
            if (entry.isDirectory()) continue;

            saveManyContentList.add(new SaveManyContent(entry.getName(), MediaType.APPLICATION_OCTET_STREAM, archive.readAllBytes()));
        }
        result.saveContent(saveManyContentList);
    }

    void unarchiveTar(@NotNull InputStream stream, @NotNull TransformResult result) throws DecompressionTransformException {
        try (TarArchiveInputStream archive = new TarArchiveInputStream(stream)) {
            unarchive(archive, result);
        } catch (IOException e) {
            throw new DecompressionTransformException("Unable to unarchive tar", e);
        }
    }

    void unarchiveAR(@NotNull InputStream stream, @NotNull TransformResult result) throws DecompressionTransformException {
        try (ArArchiveInputStream archive = new ArArchiveInputStream(stream)) {
            unarchive(archive, result);
        } catch (IOException e) {
            throw new DecompressionTransformException("Unable to unarchive ar", e);
        }
    }

    void unarchiveZip(@NotNull InputStream stream, @NotNull TransformResult result) throws DecompressionTransformException {
        try (ZipArchiveInputStream archive = new ZipArchiveInputStream(stream)) {
            unarchive(archive, result);
        } catch (IOException e) {
            throw new DecompressionTransformException("Unable to decompress zip", e);
        }
    }

    void unarchiveTarOffsets(TarArchiveInputStream archive, @NotNull TransformResult result, @NotNull ActionContent content) throws IOException {
        TarArchiveEntry entry;
        while ((entry = archive.getNextTarEntry()) != null) {
            if (entry.isDirectory()) continue;
            result.addContent(content.subcontent(archive.getBytesRead(), entry.getSize(), entry.getName(),
                    MediaType.APPLICATION_OCTET_STREAM));
        }
    }

    void inPlaceUnarchiveTar(@NotNull InputStream stream, @NotNull TransformResult result, @NotNull ActionContent content) throws DecompressionTransformException {
        try (TarArchiveInputStream archive = new TarArchiveInputStream(stream)) {
            unarchiveTarOffsets(archive, result, content);
        } catch (IOException e) {
            throw new DecompressionTransformException("Unable to unarchive tar", e);
        }
    }

}
