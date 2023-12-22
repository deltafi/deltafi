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
package org.deltafi.core.action.merge;

import lombok.RequiredArgsConstructor;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.ar.ArArchiveEntry;
import org.apache.commons.compress.archivers.ar.ArArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.common.io.Writer;
import org.deltafi.core.parameters.ArchiveType;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

@RequiredArgsConstructor
public class MergeContentWriter implements Writer {
    private final List<ActionContent> contentList;
    private final ArchiveType archiveType;

    @Override
    public void write(OutputStream outputStream) throws IOException {
        if (archiveType == null) {
            binaryConcatenate(outputStream);
            return;
        }

        switch (archiveType) {
            case TAR -> archiveTar(outputStream);
            case ZIP -> archiveZip(outputStream);
            case AR -> archiveAr(outputStream);

            case TAR_XZ -> {
                try (XZCompressorOutputStream xz = new XZCompressorOutputStream(outputStream)) {
                    archiveTar(xz);
                }
            }
            case TAR_GZIP -> {
                try (GzipCompressorOutputStream gzip = new GzipCompressorOutputStream(outputStream)) {
                    archiveTar(gzip);
                }
            }
        }
    }

    private interface ContentWriter {
        void write(ActionContent actionContent) throws IOException;
    }

    private void binaryConcatenate(OutputStream outputStream) throws IOException {
        writeContent(actionContent -> writeContent(actionContent, outputStream));
    }

    private void writeContent(MergeContentWriter.ContentWriter contentWriter) throws IOException {
        List<String> errorMessages = new ArrayList<>();

        contentList.forEach(actionContent -> {
            try {
                contentWriter.write(actionContent);
            } catch (IOException e) {
                errorMessages.add(e.getMessage());
            }
        });

        if (!errorMessages.isEmpty()) {
            throw new IOException(String.join("\n", errorMessages));
        }
    }

    private void writeContent(ActionContent actionContent, OutputStream outputStream) throws IOException {
        try (InputStream contentStream = actionContent.loadInputStream()) {
            contentStream.transferTo(outputStream);
        }
    }

    private void archiveTar(OutputStream outputStream) throws IOException {
        archive(outputStream, TarArchiveOutputStream::new,
                (fileName, fileSize) -> {
                    TarArchiveEntry archiveEntry = new TarArchiveEntry(fileName);
                    archiveEntry.setSize(fileSize);
                    return archiveEntry;
                });
    }

    private void archiveZip(OutputStream outputStream) throws IOException {
        archive(outputStream, ZipArchiveOutputStream::new,
                (fileName, fileSize) -> {
                    ZipArchiveEntry archiveEntry = new ZipArchiveEntry(fileName);
                    archiveEntry.setSize(fileSize);
                    return archiveEntry;
                });
    }

    private void archiveAr(OutputStream outputStream) throws IOException {
        archive(outputStream, ArArchiveOutputStream::new, ArArchiveEntry::new);
    }

    private <E extends ArchiveEntry> void archive(OutputStream outputStream,
            Function<OutputStream, ArchiveOutputStream<E>> archiveOutputStreamSupplier,
            BiFunction<String, Long, E> archiveEntrySupplier) throws IOException {
        ArchiveOutputStream<E> archiveOutputStream = archiveOutputStreamSupplier.apply(outputStream);
        writeContent(actionContent -> {
            String name = actionContent.getName() == null ? "" : actionContent.getName();
            E archiveEntry = archiveEntrySupplier.apply(name, actionContent.getSize());
            archiveOutputStream.putArchiveEntry(archiveEntry);
            writeContent(actionContent, archiveOutputStream);
            archiveOutputStream.closeArchiveEntry();
        });

        archiveOutputStream.finish();
    }
}
