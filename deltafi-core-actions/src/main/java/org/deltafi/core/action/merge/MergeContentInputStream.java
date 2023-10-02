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
import org.deltafi.core.parameters.ArchiveType;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * An InputStream providing content merged from a supplied list of content. The merge can be done by binary
 * concatenation, TAR, ZIP, AR, TAR.GZ, or TAR.XZ.
 */
public class MergeContentInputStream extends PipedInputStream {
    @RequiredArgsConstructor
    private static class WriterThread extends Thread {
        private static final int CONTENT_READ_BUFFER_SIZE = 16384;

        private final PipedInputStream pipedInputStream;
        private final List<ActionContent> contentList;
        private final ArchiveType archiveType;

        private boolean connected;

        @Override
        public void run() {
            try (PipedOutputStream pipedOutputStream = new PipedOutputStream(pipedInputStream)) {
                markConnected();

                if (archiveType == null) {
                    binaryConcatenate(pipedOutputStream);
                    return;
                }

                switch (archiveType) {
                    case TAR -> archiveTar(pipedOutputStream);
                    case ZIP -> archiveZip(pipedOutputStream);
                    case AR -> archiveAr(pipedOutputStream);

                    case TAR_XZ -> {
                        try (XZCompressorOutputStream xz = new XZCompressorOutputStream(pipedOutputStream)) {
                            archiveTar(xz);
                        }
                    }
                    case TAR_GZIP -> {
                        try (GzipCompressorOutputStream gzip = new GzipCompressorOutputStream(pipedOutputStream)) {
                            archiveTar(gzip);
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private synchronized void waitForPipeConnection() {
            while (!connected) {
                try {
                    wait();
                } catch (InterruptedException ignored) {}
            }
        }

        private synchronized void markConnected() {
            connected = true;
            notifyAll();
        }

        private interface ContentWriter {
            void write(ActionContent actionContent) throws IOException;
        }

        private void binaryConcatenate(OutputStream outputStream) throws IOException {
            writeContent(actionContent -> writeContent(actionContent, outputStream));
        }

        private void writeContent(ContentWriter contentWriter) throws IOException {
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
                byte[] buffer;
                do {
                    buffer = contentStream.readNBytes(CONTENT_READ_BUFFER_SIZE);
                    outputStream.write(buffer);
                } while (buffer.length > 0);
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

        private void archive(OutputStream outputStream,
                Function<OutputStream, ArchiveOutputStream> archiveOutputStreamSupplier,
                BiFunction<String, Long, ArchiveEntry> archiveEntrySupplier) throws IOException {
            ArchiveOutputStream archiveOutputStream = archiveOutputStreamSupplier.apply(outputStream);
            writeContent(actionContent -> {
                String name = actionContent.getName() == null ? "" : actionContent.getName();
                ArchiveEntry archiveEntry = archiveEntrySupplier.apply(name, actionContent.getSize());
                archiveOutputStream.putArchiveEntry(archiveEntry);
                writeContent(actionContent, archiveOutputStream);
                archiveOutputStream.closeArchiveEntry();
            });

            archiveOutputStream.finish();
        }
    }

    /**
     * Creates a MergeContentInputStream.
     * @param contentList the content list to merge
     * @param archiveType the archive type to use or null to use binary concatenation
     */
    public MergeContentInputStream(List<ActionContent> contentList, ArchiveType archiveType) {
        WriterThread writerThread = new WriterThread(this, contentList, archiveType);
        writerThread.start();
        writerThread.waitForPipeConnection();
    }
}
