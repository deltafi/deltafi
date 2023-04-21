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
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.join.JoinAction;
import org.deltafi.actionkit.action.join.JoinResult;
import org.deltafi.actionkit.action.join.JoinResultType;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.DeltaFile;
import org.deltafi.common.types.SourceInfo;
import org.deltafi.core.parameters.MergeContentJoinParameters;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

@Component
public class MergeContentJoinAction extends JoinAction<MergeContentJoinParameters> {
    private static final int CONTENT_READ_BUFFER_SIZE = 16384;

    @RequiredArgsConstructor
    private static class WriterThread implements Runnable {
        private final MergeContentJoinAction mergeContentJoinAction;
        private final PipedInputStream pipedInputStream;
        private final List<DeltaFile> joinedDeltaFiles;
        private final MergeContentJoinParameters params;

        private boolean connected;

        @Override
        public void run() {
            try (PipedOutputStream pipedOutputStream = new PipedOutputStream(pipedInputStream)) {
                markConnected();

                if (params.getArchiveType() == null) {
                    binaryConcatenate(pipedOutputStream, joinedDeltaFiles);
                    return;
                }

                switch (params.getArchiveType()) {
                    case TAR -> archiveTar(pipedOutputStream, joinedDeltaFiles);
                    case ZIP -> archiveZip(pipedOutputStream, joinedDeltaFiles);
                    case AR -> archiveAr(pipedOutputStream, joinedDeltaFiles);

                    case TAR_XZ -> {
                        try (XZCompressorOutputStream xz = new XZCompressorOutputStream(pipedOutputStream)) {
                            archiveTar(xz, joinedDeltaFiles);
                        }
                    }
                    case TAR_GZIP -> {
                        try (GzipCompressorOutputStream gzip = new GzipCompressorOutputStream(pipedOutputStream)) {
                            archiveTar(gzip, joinedDeltaFiles);
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private synchronized void markConnected() {
            connected = true;
            notifyAll();
        }

        private synchronized void waitForPipeConnection() {
            while (!connected) {
                try {
                    wait();
                } catch (InterruptedException ignored) {}
            }
        }

        private interface ContentWriter {
            void write(DeltaFile joinedDeltaFile) throws ObjectStorageException, IOException;
        }

        private void binaryConcatenate(OutputStream outputStream, List<DeltaFile> joinedDeltaFiles) throws IOException {
            writeContent(joinedDeltaFiles, joinedDeltaFile -> writeContent(joinedDeltaFile, outputStream));
        }

        private void writeContent(List<DeltaFile> joinedDeltaFiles, ContentWriter contentWriter) throws IOException {
            List<String> errorMessages = new ArrayList<>();

            joinedDeltaFiles.forEach(joinedDeltaFile -> {
                try {
                    contentWriter.write(joinedDeltaFile);
                } catch (ObjectStorageException | IOException e) {
                    errorMessages.add(e.getMessage());
                }
            });

            if (!errorMessages.isEmpty()) {
                throw new IOException(String.join("\n", errorMessages));
            }
        }

        private void writeContent(DeltaFile joinedDeltaFile, OutputStream outputStream)
                throws IOException, ObjectStorageException {
            try (InputStream contentStream = mergeContentJoinAction.load(
                    joinedDeltaFile.getLastProtocolLayer().getContentReference())) {
                byte[] buffer;
                do {
                    buffer = contentStream.readNBytes(CONTENT_READ_BUFFER_SIZE);
                    outputStream.write(buffer);
                } while (buffer.length > 0);
            }
        }

        private void archiveTar(OutputStream outputStream, List<DeltaFile> joinedDeltaFiles) throws IOException {
            archive(outputStream, joinedDeltaFiles, TarArchiveOutputStream::new,
                    (fileName, fileSize) -> {
                        TarArchiveEntry archiveEntry = new TarArchiveEntry(fileName);
                        archiveEntry.setSize(fileSize);
                        return archiveEntry;
                    });
        }

        private void archiveZip(OutputStream outputStream, List<DeltaFile> joinedDeltaFiles) throws IOException {
            archive(outputStream, joinedDeltaFiles, ZipArchiveOutputStream::new,
                    (fileName, fileSize) -> {
                        ZipArchiveEntry archiveEntry = new ZipArchiveEntry(fileName);
                        archiveEntry.setSize(fileSize);
                        return archiveEntry;
                    });
        }

        private void archiveAr(OutputStream outputStream, List<DeltaFile> joinedDeltaFiles) throws IOException {
            archive(outputStream, joinedDeltaFiles, ArArchiveOutputStream::new, ArArchiveEntry::new);
        }

        private void archive(OutputStream outputStream, List<DeltaFile> joinedDeltaFiles,
                Function<OutputStream, ArchiveOutputStream> archiveOutputStreamSupplier,
                BiFunction<String, Long, ArchiveEntry> archiveEntrySupplier) throws IOException {
            ArchiveOutputStream archiveOutputStream = archiveOutputStreamSupplier.apply(outputStream);

            writeContent(joinedDeltaFiles, joinedDeltaFile -> {
                ContentReference contentReference = joinedDeltaFile.getLastProtocolLayer().getContentReference();
                ArchiveEntry archiveEntry = archiveEntrySupplier.apply(
                        joinedDeltaFile.getSourceInfo().getFilename(), contentReference.getSize());
                archiveOutputStream.putArchiveEntry(archiveEntry);
                writeContent(joinedDeltaFile, archiveOutputStream);
                archiveOutputStream.closeArchiveEntry();
            });

            archiveOutputStream.finish();
        }
    }

    public MergeContentJoinAction() {
        super("Merges content from multiple ingested files to a single file using binary concatenation, TAR, ZIP, " +
                "AR, TAR.GZ, or TAR.XZ");
    }

    @Override
    protected JoinResultType join(DeltaFile deltaFile, List<DeltaFile> joinedDeltaFiles, ActionContext context,
            MergeContentJoinParameters params) {
        String fileName = context.getDid() +
                (params.getArchiveType() == null ? "" : ("." + params.getArchiveType().getValue()));
        String mediaType = params.getArchiveType() == null ?
                MediaType.APPLICATION_OCTET_STREAM : params.getArchiveType().getMediaType();

        SourceInfo sourceInfo = deltaFile.getSourceInfo();
        sourceInfo.setFilename(fileName);
        if (params.getReinjectFlow() != null) {
            sourceInfo.setFlow(params.getReinjectFlow());
        }

        try (PipedInputStream pipedInputStream = new PipedInputStream()) {
            WriterThread writerThread = new WriterThread(this, pipedInputStream, joinedDeltaFiles, params);
            new Thread(writerThread).start();
            writerThread.waitForPipeConnection();

            JoinResult joinResult = new JoinResult(context, sourceInfo);
            joinResult.saveContent(pipedInputStream, fileName, mediaType);

            return joinResult;
        } catch (IOException e) {
            return new ErrorResult(context, "Unable to write joined content", e);
        }
    }

    private InputStream load(ContentReference contentReference) throws ObjectStorageException {
        return loadContentAsInputStream(contentReference);
    }
}
