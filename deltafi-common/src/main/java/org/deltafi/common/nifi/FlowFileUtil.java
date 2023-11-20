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
package org.deltafi.common.nifi;

import org.apache.nifi.util.*;
import org.deltafi.common.stream.PipelineBlockingInputStream;

import java.io.*;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static org.deltafi.common.nifi.ContentType.*;

public class FlowFileUtil {
    public static PipelineBlockingInputStream packageFlowFileV1(Map<String, String> attributes, InputStream in,
                                                                long fileSize, ExecutorService executorService) throws IOException {
        PipelineBlockingInputStream pipelineBlockingInputStream = new PipelineBlockingInputStream();
        PipedOutputStream pipedOutputStream = new PipedOutputStream(pipelineBlockingInputStream);

        executorService.submit(() -> {
            try (pipedOutputStream) {
                packageFlowFile(new FlowFilePackagerV1(), attributes, in, fileSize, pipedOutputStream);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                pipelineBlockingInputStream.unblock();
            }
        });

        return pipelineBlockingInputStream;
    }

    public static byte[] packageFlowFileV2(Map<String, String> attributes, InputStream in, long fileSize)
            throws IOException {
        return packageFlowFile(new FlowFilePackagerV2(), attributes, in, fileSize);
    }

    public static byte[] packageFlowFileV3(Map<String, String> attributes, InputStream in, long fileSize)
            throws IOException {
        return packageFlowFile(new FlowFilePackagerV3(), attributes, in, fileSize);
    }

    private static byte[] packageFlowFile(FlowFilePackager flowFilePackager, Map<String, String> attributes,
            InputStream in, long fileSize) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            flowFilePackager.packageFlowFile(in, out, attributes, fileSize);
            return out.toByteArray();
        }
    }

    private static void packageFlowFile(FlowFilePackager flowFilePackager, Map<String, String> attributes,
                                        InputStream in, long fileSize, OutputStream out) throws IOException {
        flowFilePackager.packageFlowFile(in, out, attributes, fileSize);
    }

    public static FlowFile unpackageFlowFile(String contentType, InputStream in, ExecutorService executorService)
            throws IOException {
        return switch (contentType) {
            case APPLICATION_FLOWFILE, APPLICATION_FLOWFILE_V_1 ->
                    unpackageFlowFile(new FlowFileTwoStepUnpackagerV1(), in, executorService);
            case APPLICATION_FLOWFILE_V_2 -> unpackageFlowFile(new FlowFileTwoStepUnpackagerV2(), in, executorService);
            case APPLICATION_FLOWFILE_V_3 -> unpackageFlowFile(new FlowFileTwoStepUnpackagerV3(), in, executorService);
            default -> throw new IllegalStateException("Unexpected value: " + contentType);
        };
    }

    private static FlowFile unpackageFlowFile(FlowFileTwoStepUnpackager flowFileUnpackager, InputStream in,
                                              ExecutorService executorService) throws IOException {
        PipelineBlockingInputStream pipelineBlockingInputStream = new PipelineBlockingInputStream();
        PipedOutputStream pipedOutputStream = new PipedOutputStream(pipelineBlockingInputStream);
        final Map<String, String> attributes = flowFileUnpackager.unpackageAttributes(in);
        executorService.submit(() -> {
            try (pipedOutputStream) {
                flowFileUnpackager.unpackageContent(in, pipedOutputStream);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                pipelineBlockingInputStream.unblock();
            }
        });

        return new FlowFile(attributes, pipelineBlockingInputStream);
    }
}
