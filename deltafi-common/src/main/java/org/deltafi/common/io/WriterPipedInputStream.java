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
package org.deltafi.common.io;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * An InputStream providing data written to a supplied Writer running in a separate thread.
 */
@Slf4j
public class WriterPipedInputStream extends PipedInputStream {
    private static final int DEFAULT_BUFFER_SIZE = 512 * 1024;

    /**
     * Creates a WriterPipedInputStream with a default buffer size, starting a separate thread for writing.
     *
     * @param writer the Writer that will write bytes to be available to this WriterPipedInputStream
     * @param executorService the ExecutorService used to submit the writing thread
     * @return the WriterPipedInputStream
     * @throws IOException if an I/O error occurs
     */
    public static WriterPipedInputStream create(Writer writer, ExecutorService executorService) throws IOException {
        return create(writer, executorService, DEFAULT_BUFFER_SIZE);
    }

    /**
     * Creates a WriterPipedInputStream, starting a separate thread for writing.
     *
     * @param writer the Writer that will write bytes to be available to this WriterPipedInputStream
     * @param executorService the ExecutorService used to submit the writing thread
     * @param bufferSize the buffer size
     * @return the WriterPipedInputStream
     * @throws IOException if an I/O error occurs
     */
    public static WriterPipedInputStream create(Writer writer, ExecutorService executorService, int bufferSize)
            throws IOException {
        WriterPipedInputStream writerPipedInputStream = new WriterPipedInputStream(writer, executorService, bufferSize);
        writerPipedInputStream.runPipeWriter();
        return writerPipedInputStream;
    }

    private final Writer writer;
    private final ExecutorService executorService;
    private final PipedOutputStream pipedOutputStream;

    private Future<?> future;

    protected WriterPipedInputStream(Writer writer, ExecutorService executorService) throws IOException {
        this(writer, executorService, DEFAULT_BUFFER_SIZE);
    }

    protected WriterPipedInputStream(Writer writer, ExecutorService executorService, int bufferSize) throws IOException {
        super(bufferSize);

        this.writer = writer;
        this.executorService = executorService;

        pipedOutputStream = new PipedOutputStream(this);
    }

    protected void runPipeWriter() {
        future = executorService.submit(() -> {
            try (pipedOutputStream) {
                writer.write(pipedOutputStream);
            } catch (IOException e) {
                if (!e.getMessage().equals("Pipe closed")) {
                    log.warn("Write failed", e);
                }
            }
        });
    }

    @Override
    public void close() throws IOException {
        super.close();
        if (future != null) {
            future.cancel(true);
        }
    }
}
