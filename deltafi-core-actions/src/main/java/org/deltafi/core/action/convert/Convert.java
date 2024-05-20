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
package org.deltafi.core.action.convert;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

@Component
public class Convert extends TransformAction<ConvertParameters> {

    private static final CsvMapper CSV_MAPPER = new CsvMapper();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final XmlMapper XML_MAPPER = new XmlMapper();

    public Convert() {
        super("Converts content between different formats. JSON, XML, and CSV are currently supported. " +
                "Provides a best effort conversion as there is not a reliable canonical way to convert between these formats.");
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull ConvertParameters params, @NotNull TransformInput input) {
        TransformResult result = new TransformResult(context);

        for (int index = 0; index < input.getContent().size(); index++) {
            ActionContent content = input.getContent().get(index);

            if (params.getContentIndexes() != null && !params.getContentIndexes().contains(index)) {
                result.addContent(content);
                continue;
            }

            if (matchesPatterns(content, params)) {
                String newContentString;
                try {
                    newContentString = convertContent(content.loadString(), params.getInputFormat(), params.getOutputFormat(), params);
                } catch (JsonProcessingException e) {
                    return new ErrorResult(context, "Unable to convert content", e);
                } catch (IOException e) {
                    return new ErrorResult(context, "Unable to read content", e);
                }

                if (params.isRetainExistingContent()) {
                    result.addContent(content);
                }

                String newFilename = getNewFilename(content.getName(), params.getOutputFormat());
                result.saveContent(newContentString, newFilename, params.getOutputFormat().getMediaType());
            } else {
                result.addContent(content);
            }
        }

        return result;
    }

    private String convertContent(String content, DataFormat inputFormat, DataFormat outputFormat, ConvertParameters params) throws IOException {
        ObjectMapper inputMapper = getMapper(inputFormat);

        JsonNode jsonNode;
        if (inputFormat == DataFormat.CSV) {
            CsvSchema schema = CsvSchema.emptySchema().withHeader(); // Use the first row as headers
            try (MappingIterator<Map<String, String>> it = CSV_MAPPER.readerFor(Map.class).with(schema).readValues(content)) {
                List<Map<String, String>> rows = it.readAll();
                jsonNode = OBJECT_MAPPER.valueToTree(rows);
            }
        } else {
            jsonNode = inputMapper.readTree(content);
        }

        ObjectMapper outputMapper = getMapper(outputFormat);

        // Special handling if the output format is XML, add a root element
        if (outputFormat == DataFormat.XML) {
            if (jsonNode.isArray()) {
                ObjectNode wrapperNode = JsonNodeFactory.instance.objectNode();
                wrapperNode.set(params.getXmlListEntryTag(), jsonNode);
                jsonNode = wrapperNode;
            }

            return outputMapper.writer().withRootName(params.getXmlRootTag()).writeValueAsString(jsonNode);
        }

        if (outputFormat == DataFormat.CSV) {
            Set<String> uniqueKeys = new HashSet<>();

            if (jsonNode.size() == 1) {
                for (JsonNode element : jsonNode) {
                    jsonNode = element;
                }
            }

            // Collect unique keys from all elements in the array or from the object itself to build the schema
            if (jsonNode.isArray()) {
                for (JsonNode element : jsonNode) {
                    uniqueKeys.addAll(getKeys(element));
                }
            } else {
                uniqueKeys.addAll(getKeys(jsonNode));
            }

            CsvSchema.Builder schemaBuilder = CsvSchema.builder().setUseHeader(params.isCsvWriteHeader());
            for (String key : uniqueKeys) {
                schemaBuilder.addColumn(key);
            }

            CsvSchema schema = schemaBuilder.build();

            // Convert the original JSON content to a List of Maps for CSV conversion
            JavaType toValueType = CSV_MAPPER.getTypeFactory().constructParametricType(List.class, Map.class);
            List<Map<String, String>> rows = new ObjectMapper().convertValue(jsonNode, toValueType);

            return CSV_MAPPER.writer(schema).writeValueAsString(rows);
        }

        return outputMapper.writeValueAsString(jsonNode);
    }

    private boolean matchesPatterns(ActionContent content, ConvertParameters params) {
        return (params.getMediaTypes() == null || params.getMediaTypes().isEmpty() ||
                params.getMediaTypes().stream().anyMatch(type -> content.getMediaType().matches(type.replace("*", ".*")))) &&
                (params.getFilePatterns() == null || params.getFilePatterns().isEmpty() ||
                        params.getFilePatterns().stream().anyMatch(pattern -> content.getName().matches(pattern.replace("*", ".*"))));
    }

    private ObjectMapper getMapper(DataFormat format) {
        return switch (format) {
            case JSON -> OBJECT_MAPPER;
            case XML -> XML_MAPPER;
            case CSV -> CSV_MAPPER;
        };
    }

    private String getNewFilename(String originalFilename, DataFormat outputFormat) {
        int lastDotIndex = originalFilename.lastIndexOf('.');
        String baseFilename = lastDotIndex > -1 ? originalFilename.substring(0, lastDotIndex) : originalFilename;
        return baseFilename + "." + outputFormat.name().toLowerCase();
    }

    private Set<String> getKeys(JsonNode jsonNode) {
        Set<String> keys = new HashSet<>();
        Iterator<String> fieldNames = jsonNode.fieldNames();
        while (fieldNames.hasNext()) {
            keys.add(fieldNames.next());
        }
        return keys;
    }
}
