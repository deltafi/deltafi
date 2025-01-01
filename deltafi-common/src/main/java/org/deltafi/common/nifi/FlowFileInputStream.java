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
package org.deltafi.common.nifi;

import org.apache.nifi.util.FlowFilePackager;
import org.apache.nifi.util.FlowFilePackagerV1;
import org.deltafi.common.io.WriterPipedInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * An InputStream providing a FlowFile for supplied content and attributes that is written in a separate thread.
 */
public class FlowFileInputStream extends WriterPipedInputStream {
    /**
     * Creates a FlowFileInputStream that packages the content in the provided InputStream with the supplied attributes
     * in a FlowFile V1, starting a separate thread for writing.
     *
     * @param inputStream the InputStream containing the content
     * @param attributes the Map of attributes to include
     * @param fileSize the size of the content
     * @param executorService the ExecutorService used to submit the writing thread
     * @return the FlowFileInputStream
     * @throws IOException if an I/O error occurs
     */
    public static FlowFileInputStream create(InputStream inputStream, Map<String, String> attributes, long fileSize,
            ExecutorService executorService) throws IOException {
        return create(new FlowFilePackagerV1(), inputStream, attributes, fileSize, executorService);
    }

    /**
     * Creates a FlowFileInputStream that packages the content in the provided InputStream with the supplied attributes
     * using the supplied FlowFilePackager, starting a separate thread for writing.
     *
     * @param flowFilePackager the FlowFilePackager used to package the content and attributes
     * @param inputStream the InputStream containing the content
     * @param attributes the Map of attributes to include
     * @param fileSize the size of the content
     * @param executorService the ExecutorService used to submit the writing thread
     * @return the FlowFileInputStream
     * @throws IOException if an I/O error occurs
     */
    public static FlowFileInputStream create(FlowFilePackager flowFilePackager, InputStream inputStream,
            Map<String, String> attributes, long fileSize, ExecutorService executorService) throws IOException {
        FlowFileInputStream flowFileInputStream = new FlowFileInputStream(flowFilePackager, inputStream, attributes,
                fileSize, executorService);
        flowFileInputStream.runPipeWriter();
        return flowFileInputStream;
    }

    private final InputStream inputStream;

    private FlowFileInputStream(FlowFilePackager flowFilePackager, InputStream inputStream,
            Map<String, String> attributes, long fileSize, ExecutorService executorService) throws IOException {
        super(outputStream -> flowFilePackager.packageFlowFile(inputStream, outputStream, attributes, fileSize),
                executorService);
        this.inputStream = inputStream;
    }

    @Override
    public void close() throws IOException {
        super.close();
        inputStream.close();
    }
}
