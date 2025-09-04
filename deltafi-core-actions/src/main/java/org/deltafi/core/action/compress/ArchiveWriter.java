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
package org.deltafi.core.action.compress;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.ar.ArArchiveEntry;
import org.apache.commons.compress.archivers.ar.ArArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipParameters;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.content.ActionContentListWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

public class ArchiveWriter extends ActionContentListWriter {
    private static final GzipParameters GZIP_PARAMETERS;
    static {
        GZIP_PARAMETERS = new GzipParameters();
        GZIP_PARAMETERS.setOperatingSystem(3); // 3=Unix
    }

    private final Format format;
    private final Clock clock;

    public ArchiveWriter(List<ActionContent> contentList, Format format, Clock clock) {
        super(contentList);
        this.format = format;
        this.clock = clock;
    }

    @Override
    public void write(OutputStream outputStream) throws IOException {
        switch (format) {
            case TAR -> archiveTar(outputStream);
            case ZIP -> archiveZip(outputStream);
            case AR -> archiveAr(outputStream);
            case TAR_XZ -> archiveTar(new XZCompressorOutputStream(outputStream));
            case TAR_BZIP2 -> archiveTar(new BZip2CompressorOutputStream(outputStream));
            case TAR_GZIP -> archiveTar(new GzipCompressorOutputStream(outputStream, GZIP_PARAMETERS));
            default -> throw new UnsupportedOperationException("Archive format not supported: " + format);
        }
    }

    private void archiveTar(OutputStream outputStream) throws IOException {
        archive(outputStream, TarArchiveOutputStream::new,
                (fileName, fileSize) -> {
                    TarArchiveEntry archiveEntry = new TarArchiveEntry(fileName);
                    archiveEntry.setSize(fileSize);
                    archiveEntry.setLastModifiedTime(FileTime.fromMillis(clock.millis()));
                    return archiveEntry;
                });
    }

    private void archiveZip(OutputStream outputStream) throws IOException {
        archive(outputStream, ZipArchiveOutputStream::new,
                (fileName, fileSize) -> {
                    ZipArchiveEntry archiveEntry = new ZipArchiveEntry(fileName);
                    archiveEntry.setSize(fileSize);
                    archiveEntry.setLastModifiedTime(FileTime.fromMillis(clock.millis()));
                    return archiveEntry;
                });
    }

    private void archiveAr(OutputStream outputStream) throws IOException {
        archive(outputStream, ArArchiveOutputStream::new,
                (fileName, fileSize) -> new ArArchiveEntry(fileName, fileSize, 0, 0, 33188, clock.millis() / 1000L));
    }

    private <E extends ArchiveEntry> void archive(OutputStream outputStream,
            Function<OutputStream, ArchiveOutputStream<E>> archiveOutputStreamSupplier,
            BiFunction<String, Long, E> archiveEntrySupplier) throws IOException {
        try (ArchiveOutputStream<E> archiveOutputStream = archiveOutputStreamSupplier.apply(outputStream)) {
            writeContentList(actionContent -> {
                String name = actionContent.getName() == null ? "" : actionContent.getName();
                E archiveEntry = archiveEntrySupplier.apply(name, actionContent.getSize());
                archiveOutputStream.putArchiveEntry(archiveEntry);
                writeContent(actionContent, archiveOutputStream);
                archiveOutputStream.closeArchiveEntry();
            });

            archiveOutputStream.finish();
        }
    }
}
