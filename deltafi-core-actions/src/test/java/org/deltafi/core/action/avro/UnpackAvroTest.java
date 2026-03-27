/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.action.avro;

import org.apache.avro.Schema;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.assertj.core.api.Assertions;
import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.test.asserters.ErrorResultAssert;
import org.deltafi.test.asserters.TransformResultAssert;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

class UnpackAvroTest {
    private static final Schema SCHEMA = new Schema.Parser().parse("""
            {
              "type": "record",
              "name": "Message",
              "namespace": "org.deltafi.test",
              "fields": [
                { "name": "id", "type": "int" },
                { "name": "text", "type": "string" }
              ]
            }
            """);

    private final UnpackAvro action = new UnpackAvro();
    private final DeltaFiTestRunner runner = DeltaFiTestRunner.setup();

    @Test
    void unpacksAvroIntoMetadataAndContent() throws Exception {
        UnpackAvroParameters params = new UnpackAvroParameters();
        TransformInput input = TransformInput.builder()
                .content(List.of(saveAvroContent("messages.avro")))
                .build();

        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert.assertThat(result)
                .hasContentCount(2)
                .hasContentMatchingAt(0, content -> {
                    Assertions.assertThat(content.getName()).isEqualTo("messages.content.ndjson");
                    Assertions.assertThat(content.getMediaType()).isEqualTo("application/x-ndjson");
                    Assertions.assertThat(content.loadString())
                            .isEqualTo("{\"id\":1,\"text\":\"hello\"}\n{\"id\":2,\"text\":\"world\"}");
                    return true;
                })
                .hasContentMatchingAt(1, content -> {
                    Assertions.assertThat(content.getName()).isEqualTo("messages.container-metadata.json");
                    Assertions.assertThat(content.loadString())
                            .contains("\"source\":\"" + Base64.getEncoder().encodeToString("sensor-a".getBytes(StandardCharsets.UTF_8)) + "\"");
                    return true;
                });
        Assertions.assertThat(((TransformResult) result).getMetadata())
                .containsEntry("avroRecordCount", "2")
                .doesNotContainKey("avroCodec");
    }

    @Test
    void canOptionallyWriteSchemaFile() throws Exception {
        UnpackAvroParameters params = new UnpackAvroParameters();
        params.setIncludeSchemaFile(true);

        TransformInput input = TransformInput.builder()
                .content(List.of(saveAvroContent("messages.avro")))
                .build();

        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert.assertThat(result)
                .hasContentCount(3)
                .hasContentMatchingAt(0, content -> "messages.content.ndjson".equals(content.getName()))
                .hasContentMatchingAt(1, content -> {
                    Assertions.assertThat(content.getName()).isEqualTo("messages.schema.json");
                    Assertions.assertThat(content.getMediaType()).isEqualTo("application/json");
                    Assertions.assertThat(content.loadString())
                            .contains("\"name\":\"Message\"")
                            .doesNotContain("recordCount")
                            .doesNotContain("codec");
                    return true;
                })
                .hasContentMatchingAt(2, content -> "messages.container-metadata.json".equals(content.getName()));
    }

    @Test
    void canWriteIndividualJsonFilesPerRecord() throws Exception {
        UnpackAvroParameters params = new UnpackAvroParameters();
        params.setRecordOutputMode(AvroRecordOutputMode.INDIVIDUAL_JSON);

        TransformInput input = TransformInput.builder()
                .content(List.of(saveAvroContent("messages.avro")))
                .build();

        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert.assertThat(result)
                .hasContentCount(3)
                .hasContentMatchingAt(0, "messages.1.json", "application/json", "{\"id\":1,\"text\":\"hello\"}")
                .hasContentMatchingAt(1, "messages.2.json", "application/json", "{\"id\":2,\"text\":\"world\"}")
                .hasContentMatchingAt(2, content -> "messages.container-metadata.json".equals(content.getName()));
    }

    @Test
    void canWriteJsonArrayContent() throws Exception {
        UnpackAvroParameters params = new UnpackAvroParameters();
        params.setRecordOutputMode(AvroRecordOutputMode.JSON_ARRAY);

        TransformInput input = TransformInput.builder()
                .content(List.of(saveAvroContent("messages.avro")))
                .build();

        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert.assertThat(result)
                .hasContentCount(2)
                .hasContentMatchingAt(0, content -> {
                    Assertions.assertThat(content.getName()).isEqualTo("messages.content.json");
                    Assertions.assertThat(content.getMediaType()).isEqualTo("application/json");
                    Assertions.assertThat(content.loadString())
                            .isEqualTo("[{\"id\":1,\"text\":\"hello\"},\n{\"id\":2,\"text\":\"world\"}]");
                    return true;
                })
                .hasContentMatchingAt(1, content -> "messages.container-metadata.json".equals(content.getName()));
    }

    @Test
    void retainsExistingContentWhenRequested() throws Exception {
        UnpackAvroParameters params = new UnpackAvroParameters();
        params.setRetainExistingContent(true);

        TransformInput input = TransformInput.builder()
                .content(List.of(saveAvroContent("messages.avro")))
                .build();

        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert.assertThat(result)
                .hasContentCount(3)
                .hasContentMatchingAt(0, content -> "messages.avro".equals(content.getName()))
                .hasContentMatchingAt(1, content -> "messages.content.ndjson".equals(content.getName()))
                .hasContentMatchingAt(2, content -> "messages.container-metadata.json".equals(content.getName()));
    }

    @Test
    void passesThroughUnselectedContent() throws Exception {
        UnpackAvroParameters params = new UnpackAvroParameters();
        params.setFilePatterns(List.of("*.avro"));

        ActionContent selected = saveAvroContent("messages.avro");
        ActionContent unselected = runner.saveContent("plain text", "notes.txt", "text/plain");
        TransformInput input = TransformInput.builder()
                .content(List.of(selected, unselected))
                .build();

        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert.assertThat(result)
                .hasContentCount(3)
                .hasContentMatchingAt(0, content -> "messages.content.ndjson".equals(content.getName()))
                .hasContentMatchingAt(1, content -> "messages.container-metadata.json".equals(content.getName()))
                .hasContentMatchingAt(2, content -> "notes.txt".equals(content.getName()));
    }

    @Test
    void onlyUnpacksFirstSelectedContent() throws Exception {
        UnpackAvroParameters params = new UnpackAvroParameters();
        params.setFilePatterns(List.of("*.avro"));

        ActionContent first = saveAvroContent("messages-1.avro");
        ActionContent second = saveAvroContent("messages-2.avro");
        TransformInput input = TransformInput.builder()
                .content(List.of(first, second))
                .build();

        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert.assertThat(result)
                .hasContentCount(3)
                .hasContentMatchingAt(0, content -> "messages-1.content.ndjson".equals(content.getName()))
                .hasContentMatchingAt(1, content -> "messages-1.container-metadata.json".equals(content.getName()))
                .hasContentMatchingAt(2, content -> "messages-2.avro".equals(content.getName()));
    }

    @Test
    void usesContentAsBaseNameForBareAvroFilename() throws Exception {
        TransformInput input = TransformInput.builder()
                .content(List.of(saveAvroContent(".avro")))
                .build();

        ResultType result = action.transform(runner.actionContext(), new UnpackAvroParameters(), input);

        TransformResultAssert.assertThat(result)
                .hasContentCount(2)
                .hasContentMatchingAt(0, content -> "content.content.ndjson".equals(content.getName()))
                .hasContentMatchingAt(1, content -> "content.container-metadata.json".equals(content.getName()));
    }

    @Test
    void errorsOnInvalidSelectedContent() {
        TransformInput input = TransformInput.builder()
                .content(List.of(runner.saveContent("not avro", "broken.avro", "application/octet-stream")))
                .build();

        ResultType result = action.transform(runner.actionContext(), new UnpackAvroParameters(), input);

        ErrorResultAssert.assertThat(result).hasCause("Error unpacking Avro content at index 0");
    }

    private ActionContent saveAvroContent(String name) throws IOException {
        return ActionContent.saveContent(runner.actionContext(), createAvroBytes(), name, "application/avro");
    }

    private byte[] createAvroBytes() throws IOException {
        GenericData.Record first = new GenericData.Record(SCHEMA);
        first.put("id", 1);
        first.put("text", "hello");

        GenericData.Record second = new GenericData.Record(SCHEMA);
        second.put("id", 2);
        second.put("text", "world");

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             DataFileWriter<GenericData.Record> writer = new DataFileWriter<>(new GenericDatumWriter<>(SCHEMA))) {
            writer.setMeta("source", "sensor-a");
            writer.create(SCHEMA, outputStream);
            writer.append(first);
            writer.append(second);
            writer.flush();
            return outputStream.toByteArray();
        }
    }
}
