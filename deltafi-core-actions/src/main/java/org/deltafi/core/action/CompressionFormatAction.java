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
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.ar.ArArchiveEntry;
import org.apache.commons.compress.archivers.ar.ArArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.compressors.CompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipParameters;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.format.*;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.types.ActionContext;
import org.deltafi.core.exception.CompressionFormatException;
import org.deltafi.core.parameters.ArchiveType;
import org.deltafi.core.parameters.CompressionFormatParameters;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class CompressionFormatAction extends FormatAction<CompressionFormatParameters> {

    private static final GzipParameters gzipParameters;

    static {
        gzipParameters = new GzipParameters();
        gzipParameters.setOperatingSystem(3); // 3=Unix
    }

    public CompressionFormatAction() {
        super("Compresses and/or archives content to .zip, .tar.gz, .tar.xz, .tar, .ar, .gz, or .xz");
    }

    private static CompressorOutputStream createCompressorOutputStream(OutputStream outputStream, ArchiveType archiveType) throws IllegalArgumentException, IOException {
        return switch (archiveType) {
            case GZIP -> new GzipCompressorOutputStream(outputStream, gzipParameters);
            case XZ -> new XZCompressorOutputStream(outputStream);
            default -> throw new IllegalArgumentException("Unsupported archive type: '" + archiveType + "'");
        };
    }

    private static ArchiveOutputStream createArchiveOutputStream(OutputStream outputStream, ArchiveType archiveType) throws IllegalArgumentException, IOException {
        return switch (archiveType) {
            case AR -> new ArArchiveOutputStream(outputStream);
            case TAR -> tarArchiveOutputStream(outputStream);
            case TAR_GZIP -> tarArchiveOutputStream(new GzipCompressorOutputStream(outputStream, gzipParameters));
            case TAR_XZ -> tarArchiveOutputStream(new XZCompressorOutputStream(outputStream));
            case ZIP -> new ZipArchiveOutputStream(outputStream);
            default -> throw new IllegalArgumentException("Unsupported archive type: '" + archiveType + "'");
        };
    }

    private static TarArchiveOutputStream tarArchiveOutputStream(OutputStream outputStream) {
        TarArchiveOutputStream tarArchiveOutputStream = new TarArchiveOutputStream(outputStream);
        tarArchiveOutputStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
        return tarArchiveOutputStream;
    }

    private static ArchiveEntry createArchiveEntry(String name, long len, ArchiveType archiveType) throws IllegalArgumentException {
        return switch (archiveType) {
            case AR -> new ArArchiveEntry(name, len);
            case TAR, TAR_GZIP, TAR_XZ -> createTarArchiveEntry(name, len);
            case ZIP -> createZipArchiveEntry(name, len);
            default -> throw new IllegalArgumentException("Unsupported archive type: '" + archiveType + "'");
        };
    }

    private static ZipArchiveEntry createZipArchiveEntry(String name, long len) {
        ZipArchiveEntry entry = new ZipArchiveEntry(name);
        entry.setSize(len);
        return entry;
    }

    private static TarArchiveEntry createTarArchiveEntry(String name, long len) {
        TarArchiveEntry entry = new TarArchiveEntry(name);
        entry.setSize(len);
        return entry;
    }

    @Override
    public FormatResultType format(@NotNull ActionContext context, @NotNull CompressionFormatParameters params, @NotNull FormatInput input) {
        // Note regarding 'Z' and 'TAR_Z' (used to identify the traditional Unix compress method).
        // Not supported as an output stream type by org.apache.commons.compress.
        try {
            return switch (params.getArchiveType()) {
                case AR, TAR, TAR_GZIP, TAR_XZ, ZIP -> archive(context, params, input);
                case GZIP, XZ -> compress(context, params, input);
                default -> new ErrorResult(context, "Invalid archive type: " + params.getArchiveType()).logErrorTo(log);
            };
        } catch (Exception e) {
            return e.getCause() != null ?
                    new ErrorResult(context, e.getMessage(), e.getCause()).logErrorTo(log) :
                    new ErrorResult(context, e.getMessage()).logErrorTo(log);
        }
    }

    private FormatResultType compress(@NotNull ActionContext context, @NotNull CompressionFormatParameters params, @NotNull FormatInput input) throws CompressionFormatException {
        List<FormatResult> results = getFormatResults(input.content(), context, params);
        if (results.size() == 1) {
            return results.get(0);
        } else if (results.size() > 1) {
            FormatManyResult many = new FormatManyResult(context);
            for (FormatResult result : results) {
                many.add(result);
            }
            return many;
        } else {
            throw new CompressionFormatException("No content found");
        }
    }

    private List<FormatResult> getFormatResults(List<ActionContent> contentInput, ActionContext context, CompressionFormatParameters params) {
        List<FormatResult> results = new ArrayList<>();
        for (ActionContent content : contentInput) {
            FormatResult result = new FormatResult(context, compressOne(context, content, params));
            result.addMetadata("archiveType", params.getArchiveType().getValue());
            results.add(result);
        }
        return results;
    }

    private ActionContent compressOne(ActionContext context, ActionContent content, CompressionFormatParameters params) {

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (CompressorOutputStream compressed = createCompressorOutputStream(bos, params.getArchiveType())) {
            try (InputStream contentStream = content.loadInputStream()) {
                IOUtils.copy(contentStream, compressed);
                compressed.close();
                return saveNewContent(context, bos, content.getName(), params);
            } catch (IOException e) {
                throw new CompressionFormatException("Failed to load content from storage", e);
            }
        } catch (IOException e) {
            throw new CompressionFormatException("Unexpected IO stream problem", e);
        } catch (IllegalArgumentException e) {
            throw new CompressionFormatException("Invalid archive format", e);
        }
    }

    private ActionContent saveNewContent(ActionContext context, ByteArrayOutputStream bos, String name, CompressionFormatParameters params) {
        String newName = updateName(name, params);
        String mediaType = getMediaType(params);
        return ActionContent.saveContent(context, bos.toByteArray(), newName, mediaType);
    }

    private String updateName(String name, CompressionFormatParameters params) {
        if (params.getAddArchiveSuffix()) {
            return name + "." + params.getArchiveType().getValue();
        }
        return name;
    }

    private String getMediaType(CompressionFormatParameters params) {
        String mediaType = params.getArchiveType().getMediaType();
        return StringUtils.isNoneEmpty(params.getMediaType()) ? params.getMediaType() : mediaType;
    }

    private FormatResultType archive(@NotNull ActionContext context, @NotNull CompressionFormatParameters params, @NotNull FormatInput input) throws CompressionFormatException {

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ArchiveOutputStream archive = createArchiveOutputStream(bos, params.getArchiveType())) {

            for (int i = 0; i < input.content().size(); ++i) {
                ActionContent content = input.content(i);
                try (InputStream contentStream = content.loadInputStream()) {
                    ArchiveEntry archiveEntry = createArchiveEntry(content.getName(), content.getSize(), params.getArchiveType());
                    archive.putArchiveEntry(archiveEntry);
                    IOUtils.copy(contentStream, archive);
                    archive.closeArchiveEntry();
                } catch (IOException e) {
                    throw new CompressionFormatException("Failed to load content from storage", e);
                }
            }

            archive.close();

            FormatResult result = new FormatResult(context, saveNewContent(context, bos, input.content(0).getName(), params));
            result.addMetadata("archiveType", params.getArchiveType().getValue());
            return result;
        } catch (IOException e) {
            throw new CompressionFormatException("Unexpected IO stream problem", e);
        } catch (IllegalArgumentException e) {
            throw new CompressionFormatException("Invalid archive format", e);
        }
    }

    @Override
    public List<String> getRequiresDomains() {
        return List.of(DeltaFiConstants.MATCHES_ANY);
    }

    @Override
    public List<String> getRequiresEnrichments() {
        return List.of(DeltaFiConstants.MATCHES_ANY);
    }
}
