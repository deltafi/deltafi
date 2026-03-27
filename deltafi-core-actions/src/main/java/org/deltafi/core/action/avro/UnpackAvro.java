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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.avro.Schema;
import org.apache.avro.file.DataFileStream;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.io.JsonEncoder;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.io.Writer;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionOptions;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class UnpackAvro extends TransformAction<UnpackAvroParameters> {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String AVRO_SCHEMA_KEY = "avro.schema";
    private static final String AVRO_CODEC_KEY = "avro.codec";
    private static final String NDJSON_MEDIA_TYPE = "application/x-ndjson";
    private static final String JSON_ARRAY_MEDIA_TYPE = MediaType.APPLICATION_JSON;

    public UnpackAvro() {
        super(ActionOptions.builder()
                .description("Unpacks the first selected embedded-schema Avro object container file into schema, container metadata, and decoded record content.")
                .inputSpec(ActionOptions.InputSpec.builder()
                        .contentSummary("""
                                Input content to act on may be selected (or inversely selected using the exclude
                                parameters) with contentIndexes, mediaTypes, and/or filePatterns. Selected content must
                                be an Avro object container file with an embedded schema.

                                If multiple contents are selected, only the first selected content is unpacked. To
                                unpack multiple Avro files independently, split the DeltaFile before this action.

                                This action does not support raw binary Avro that relies on an external schema, or
                                Confluent-style wire formats that carry a schema registry identifier instead of a full
                                embedded Avro container header.
                                """)
                        .build())
                .outputSpec(ActionOptions.OutputSpec.builder()
                        .contentSummary("""
                                Each selected Avro content is replaced by derived contents unless
                                retainExistingContent is true.

                                By default, derived contents include decoded record content and container metadata
                                JSON. An optional schema JSON content may also be written when includeSchemaFile is
                                true.

                                The container metadata JSON is a flat map of custom Avro header metadata keys to Base64
                                values because Avro metadata values are binary.

                                Decoded Avro records can be written as newline-delimited JSON, a single JSON array, or
                                multiple numbered JSON contents, because a single Avro file may contain multiple
                                records rather than a single payload.

                                If multiple contents are selected, only the first selected content is unpacked and all
                                remaining content is passed through unchanged.

                                New content names are built from the input content name with a trailing '.avro'
                                removed when present, then schema/container metadata suffixes, contentSuffix,
                                jsonArraySuffix, or numbered JSON naming is applied.
                                """)
                        .metadataDescriptions(List.of(
                                ActionOptions.KeyedDescription.builder()
                                        .key("avroRecordCount")
                                        .description("Number of decoded Avro records written for the selected content")
                                        .build(),
                                ActionOptions.KeyedDescription.builder()
                                        .key("avroCodec")
                                        .description("Codec from the Avro object container file header, when present")
                                        .build()))
                        .build())
                .errors("On selected content that is not a readable embedded-schema Avro object container file")
                .build());
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull UnpackAvroParameters params,
                                         @NotNull TransformInput input) {
        TransformResult result = new TransformResult(context);
        boolean unpackedSelectedContent = false;

        for (int i = 0; i < input.getContent().size(); i++) {
            ActionContent content = input.content(i);

            if (!params.contentSelected(i, content) || unpackedSelectedContent) {
                result.addContent(content);
                continue;
            }

            if (params.isRetainExistingContent()) {
                result.addContent(content);
            }

            try {
                unpackContent(result, content, params);
                unpackedSelectedContent = true;
            } catch (Exception e) {
                return new ErrorResult(context, "Error unpacking Avro content at index " + i, e);
            }
        }

        return result;
    }

    private void unpackContent(TransformResult result, ActionContent content, UnpackAvroParameters params) throws IOException {
        String unpackBaseName = unpackBaseName(content.getName());
        AvroExtractionSummary summary;

        if (params.getRecordOutputMode() == AvroRecordOutputMode.INDIVIDUAL_JSON) {
            summary = saveIndividualRecordContent(result, content, unpackBaseName, params);
        } else if (params.getRecordOutputMode() == AvroRecordOutputMode.JSON_ARRAY) {
            JsonArrayRecordsWriter writer = new JsonArrayRecordsWriter(content);
            result.saveContent(
                    writer,
                    unpackBaseName + params.getJsonArraySuffix(),
                    JSON_ARRAY_MEDIA_TYPE);
            summary = writer.getSummary();
        } else {
            AvroRecordsWriter writer = new AvroRecordsWriter(content);
            result.saveContent(
                    writer,
                    unpackBaseName + params.getContentSuffix(),
                    NDJSON_MEDIA_TYPE);
            summary = writer.getSummary();
        }

        result.addMetadata("avroRecordCount", Integer.toString(summary.recordCount()));
        if (summary.codec() != null) {
            result.addMetadata("avroCodec", summary.codec());
        }

        if (params.isIncludeSchemaFile()) {
            result.saveContent(
                    buildSchemaJson(summary),
                    unpackBaseName + params.getSchemaFilenameSuffix(),
                    MediaType.APPLICATION_JSON);
        }
        if (params.isIncludeContainerMetadataFile()) {
            result.saveContent(
                    buildContainerMetadataJson(summary),
                    unpackBaseName + params.getContainerMetadataFilenameSuffix(),
                    MediaType.APPLICATION_JSON);
        }
    }

    private String buildSchemaJson(AvroExtractionSummary summary) throws IOException {
        return OBJECT_MAPPER.writeValueAsString(OBJECT_MAPPER.readTree(summary.schema().toString()));
    }

    private String buildContainerMetadataJson(AvroExtractionSummary summary) throws IOException {
        return OBJECT_MAPPER.writeValueAsString(summary.customMetadataBase64());
    }

    private String unpackBaseName(String name) {
        if (name == null || name.isBlank() || ".avro".equalsIgnoreCase(name)) {
            return "content";
        }

        String lowerName = name.toLowerCase(Locale.ROOT);
        return lowerName.endsWith(".avro") ? name.substring(0, name.length() - 5) : name;
    }

    private AvroExtractionSummary saveIndividualRecordContent(TransformResult result, ActionContent content, String unpackBaseName,
                                                              UnpackAvroParameters params) throws IOException {
        try (InputStream inputStream = content.loadInputStream();
             DataFileStream<Object> dataFileStream = new DataFileStream<>(inputStream, new GenericDatumReader<>())) {
            Schema schema = dataFileStream.getSchema();
            GenericDatumWriter<Object> datumWriter = new GenericDatumWriter<>(schema);
            AvroExtractionSummary summary = extractionSummary(dataFileStream);
            int recordIndex = 1;

            while (dataFileStream.hasNext()) {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                JsonEncoder encoder = EncoderFactory.get().jsonEncoder(schema, outputStream);
                datumWriter.write(dataFileStream.next(), encoder);
                encoder.flush();
                result.saveContent(
                        outputStream.toString(),
                        unpackBaseName + params.getIndividualRecordSeparator() + recordIndex + params.getIndividualRecordSuffix(),
                        MediaType.APPLICATION_JSON);
                recordIndex++;
                summary.incrementRecordCount();
            }

            return summary;
        }
    }

    private static AvroExtractionSummary extractionSummary(DataFileStream<Object> dataFileStream) {
        Map<String, String> customMetadataBase64 = new LinkedHashMap<>();
        for (String key : dataFileStream.getMetaKeys()) {
            if (!AVRO_SCHEMA_KEY.equals(key) && !AVRO_CODEC_KEY.equals(key)) {
                customMetadataBase64.put(key, Base64.getEncoder().encodeToString(dataFileStream.getMeta(key)));
            }
        }
        return new AvroExtractionSummary(dataFileStream.getSchema(), dataFileStream.getMetaString(AVRO_CODEC_KEY),
                customMetadataBase64);
    }

    private abstract static class RecordsWriter implements Writer {
        private final ActionContent actionContent;
        protected AvroExtractionSummary summary;

        private RecordsWriter(ActionContent actionContent) {
            this.actionContent = actionContent;
        }

        protected AvroExtractionSummary getSummary() {
            return summary;
        }

        protected DataFileStream<Object> openDataFileStream() throws IOException {
            return new DataFileStream<>(actionContent.loadInputStream(), new GenericDatumReader<>());
        }
    }

    private static class AvroRecordsWriter extends RecordsWriter {
        private AvroRecordsWriter(ActionContent actionContent) {
            super(actionContent);
        }

        @Override
        public void write(OutputStream outputStream) throws IOException {
            try (DataFileStream<Object> dataFileStream = openDataFileStream()) {
                Schema schema = dataFileStream.getSchema();
                GenericDatumWriter<Object> datumWriter = new GenericDatumWriter<>(schema);
                JsonEncoder encoder = EncoderFactory.get().jsonEncoder(schema, outputStream);
                summary = extractionSummary(dataFileStream);

                while (dataFileStream.hasNext()) {
                    datumWriter.write(dataFileStream.next(), encoder);
                    encoder.flush();
                    summary.incrementRecordCount();
                }
                outputStream.flush();
            }
        }
    }

    private static class JsonArrayRecordsWriter extends RecordsWriter {
        private JsonArrayRecordsWriter(ActionContent actionContent) {
            super(actionContent);
        }

        @Override
        public void write(OutputStream outputStream) throws IOException {
            try (DataFileStream<Object> dataFileStream = openDataFileStream()) {
                Schema schema = dataFileStream.getSchema();
                GenericDatumWriter<Object> datumWriter = new GenericDatumWriter<>(schema);
                JsonEncoder encoder = EncoderFactory.get().jsonEncoder(schema, outputStream);
                summary = extractionSummary(dataFileStream);

                outputStream.write('[');
                boolean first = true;
                while (dataFileStream.hasNext()) {
                    if (!first) {
                        outputStream.write(',');
                    }
                    datumWriter.write(dataFileStream.next(), encoder);
                    encoder.flush();
                    summary.incrementRecordCount();
                    first = false;
                }
                outputStream.write(']');
                outputStream.flush();
            }
        }
    }

    private static class AvroExtractionSummary {
        private final Schema schema;
        private final String codec;
        private final Map<String, String> customMetadataBase64;
        private int recordCount;

        private AvroExtractionSummary(Schema schema, String codec, Map<String, String> customMetadataBase64) {
            this.schema = schema;
            this.codec = codec;
            this.customMetadataBase64 = customMetadataBase64;
        }

        private void incrementRecordCount() {
            recordCount++;
        }

        private Schema schema() {
            return schema;
        }

        private String codec() {
            return codec;
        }

        private Map<String, String> customMetadataBase64() {
            return customMetadataBase64;
        }

        private int recordCount() {
            return recordCount;
        }
    }
}
