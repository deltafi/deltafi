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
package org.deltafi.common.action.documentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.ActionDescriptor;
import org.deltafi.common.types.ActionOptions;
import org.deltafi.common.util.MarkdownBuilder;

import java.util.*;

@Slf4j
public class DocumentationGenerator {
    private static final List<MarkdownBuilder.ColumnDef> PARAMETER_TABLE_COLUMNS =
            List.of(new MarkdownBuilder.ColumnDef("Name", false, true),
                    new MarkdownBuilder.ColumnDef("Description", false, true),
                    new MarkdownBuilder.ColumnDef("Allowed Values", false, false),
                    new MarkdownBuilder.ColumnDef("Required", true, false),
                    new MarkdownBuilder.ColumnDef("Default", true, true));

    /**
     * Generate Markdown documentation for an action.
     * @param actionDescriptor the ActionDescriptor describing the action
     * @return the generated Markdown documentation
     */
    public static String generateActionDocs(ActionDescriptor actionDescriptor) {
        // Maintain backward compatibility
        if (actionDescriptor.getActionOptions() == null) {
            if (actionDescriptor.getDescription() == null) {
                return null;
            }
            actionDescriptor.setActionOptions(
                    ActionOptions.builder().description(actionDescriptor.getDescription()).build());
        }

        MarkdownBuilder docs = new MarkdownBuilder();

        int lastDotIndex = actionDescriptor.getName().lastIndexOf('.');
        docs.append("# ").append(lastDotIndex != -1 ?
                actionDescriptor.getName().substring(lastDotIndex + 1) : actionDescriptor.getName()).append('\n');
        docs.append(actionDescriptor.getActionOptions().getDescription()).append("\n\n");

        docs.append(generateParametersMarkdown(actionDescriptor.getSchema()));
        docs.append('\n');

        if (actionDescriptor.getActionOptions().getInputSpec() != null) {
            docs.append("## Input\n");
            docs.append(generateContentMarkdown(actionDescriptor.getActionOptions().getInputSpec()));
            docs.append(generateMetadataMarkdown(actionDescriptor.getActionOptions().getInputSpec()));
        }

        if (actionDescriptor.getActionOptions().getOutputSpec() != null) {
            docs.append("## Output\n");
            if (actionDescriptor.getActionOptions().getOutputSpec().isPassthrough()) {
                docs.append("Content is passed through unchanged.\n");
            } else {
                docs.append(generateContentMarkdown(actionDescriptor.getActionOptions().getOutputSpec()));
            }
            docs.append(generateMetadataMarkdown(actionDescriptor.getActionOptions().getOutputSpec()));
            docs.append(generateAnnotationsMarkdown(actionDescriptor.getActionOptions().getOutputSpec()));
        }

        if (actionDescriptor.getActionOptions().getFilters() != null) {
            docs.append("## Filters\n");
            for (ActionOptions.DescriptionWithConditions filter : actionDescriptor.getActionOptions().getFilters()) {
                docs.append("* ").append(filter.getDescription()).append('\n');
                if (filter.getConditions() != null) {
                    for (String condition : filter.getConditions()) {
                        docs.append("    * ").append(condition).append('\n');
                    }
                }
            }
            docs.append('\n');
        }

        if (actionDescriptor.getActionOptions().getErrors() != null) {
            docs.append("## Errors\n");
            for (ActionOptions.DescriptionWithConditions error : actionDescriptor.getActionOptions().getErrors()) {
                docs.append("* ").append(error.getDescription()).append('\n');
                if (error.getConditions() != null) {
                    for (String condition : error.getConditions()) {
                        docs.append("    * ").append(condition).append('\n');
                    }
                }
            }
            docs.append('\n');
        }

        if (actionDescriptor.getActionOptions().getNotes() != null) {
            docs.append("## Notes\n");
            for (String note : actionDescriptor.getActionOptions().getNotes()) {
                docs.append("* ").append(note).append('\n');
            }
            docs.append('\n');
        }

        if (actionDescriptor.getActionOptions().getDetails() != null) {
            docs.append("## Details\n");
            docs.append(actionDescriptor.getActionOptions().getDetails());
            docs.append('\n');
        }

        return docs.build();
    }

    private static String generateParametersMarkdown(Map<String, Object> schema) {
        MarkdownBuilder docs = new MarkdownBuilder();

        docs.append("## Parameters\n");
        List<PropertyInfo> propertyInfoList = getPropertyInfo(schema);
        if (propertyInfoList.isEmpty()) {
            docs.append("None\n");
        } else {
            docs.addTable(PARAMETER_TABLE_COLUMNS, generateParameterRows(propertyInfoList));
        }

        return docs.build();
    }

    @Data
    private static class PropertyInfo {
        private String name;
        private PropertyType propertyType;
        private String description;
        private boolean required;
        private String defaultValue;
    }

    @Data
    private static class PropertyType {
        private String type;
        private TreeSet<String> enumValues;
        private String arrayType;
        private String mapType;
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static List<PropertyInfo> getPropertyInfo(Map<String, Object> schema) {
        if (schema.isEmpty()) {
            return Collections.emptyList();
        }

        List<PropertyInfo> propertyInfoList = new ArrayList<>();

        JsonNode schemaNode = OBJECT_MAPPER.convertValue(schema, JsonNode.class);

        Map<String, PropertyType> defs = new HashMap<>();
        JsonNode defsNode = schemaNode.get("$defs");
        if (defsNode != null) {
            defsNode.fields().forEachRemaining(
                    fieldEntry -> defs.put(fieldEntry.getKey(), getPropertyType(fieldEntry.getValue())));
        }

        JsonNode propertiesNode = schemaNode.get("properties");
        if (propertiesNode == null) {
            return Collections.emptyList();
        }

        Set<String> requiredProperties = new HashSet<>();
        JsonNode requiredNode = schemaNode.get("required");
        if (requiredNode != null) {
            requiredNode.iterator().forEachRemaining(requiredPropertyJsonNode ->
                    requiredProperties.add(requiredPropertyJsonNode.textValue()));
        }

        propertiesNode.fields().forEachRemaining(fieldEntry -> {
            PropertyInfo propertyInfo = new PropertyInfo();
            propertyInfo.setName(fieldEntry.getKey());
            JsonNode fieldSchema = fieldEntry.getValue();

            // Handle allOf wrapper (used when custom type has field-level description)
            JsonNode allOfNode = fieldSchema.get("allOf");
            if (allOfNode != null && allOfNode.isArray() && !allOfNode.isEmpty()) {
                // First element contains the type definition, second may have field description
                JsonNode typeSchema = allOfNode.get(0);
                JsonNode typeNode = typeSchema.get("type");
                if (typeNode != null) {
                    propertyInfo.setPropertyType(getPropertyType(typeSchema));
                } else {
                    JsonNode xDeltafiType = typeSchema.get("x-deltafi-type");
                    PropertyType fallbackType = new PropertyType();
                    fallbackType.setType(xDeltafiType != null ? xDeltafiType.textValue() : "unknown");
                    propertyInfo.setPropertyType(fallbackType);
                }
                // Get description from second allOf element if present, or from first
                JsonNode descriptionNode = allOfNode.size() > 1 ? allOfNode.get(1).get("description") : null;
                if (descriptionNode == null) {
                    descriptionNode = typeSchema.get("description");
                }
                if (descriptionNode != null) {
                    propertyInfo.setDescription(descriptionNode.textValue());
                }
            } else {
                JsonNode typeNode = fieldSchema.get("type");
                if (typeNode != null) {
                    propertyInfo.setPropertyType(getPropertyType(fieldSchema));
                } else {
                    JsonNode refNode = fieldSchema.get("$ref");
                    if (refNode != null) {
                        String defName = refNode.textValue().substring(8); // skip "#/$defs/"
                        propertyInfo.setPropertyType(defs.get(defName));
                    } else {
                        // Handle custom types that may have x-deltafi-type or other extensions
                        PropertyType fallbackType = new PropertyType();
                        JsonNode xDeltafiType = fieldSchema.get("x-deltafi-type");
                        if (xDeltafiType != null) {
                            fallbackType.setType(xDeltafiType.textValue());
                        } else {
                            fallbackType.setType("unknown");
                        }
                        propertyInfo.setPropertyType(fallbackType);
                    }
                }
                JsonNode descriptionNode = fieldSchema.get("description");
                if (descriptionNode != null) {
                    propertyInfo.setDescription(descriptionNode.textValue());
                }
            }
            JsonNode defaultNode = fieldSchema.get("default");
            if (defaultNode != null) {
                propertyInfo.setDefaultValue(defaultNode.asText());
            }
            propertyInfo.setRequired(requiredProperties.contains(fieldEntry.getKey()));
            propertyInfoList.add(propertyInfo);
        });

        return propertyInfoList;
    }

    private static PropertyType getPropertyType(JsonNode defJsonNode) {
        PropertyType propertyType = new PropertyType();

        JsonNode typeNode = defJsonNode.get("type");
        propertyType.setType(typeNode.textValue());
        if (typeNode.textValue().equals("string")) {
            JsonNode enumNode = defJsonNode.get("enum");
            if (enumNode != null) {
                TreeSet<String> enumValues = new TreeSet<>();
                enumNode.iterator().forEachRemaining(enumValueJsonNode ->
                        enumValues.add(enumValueJsonNode.textValue()));
                propertyType.setEnumValues(enumValues);
            }
        } else if (typeNode.textValue().equals("array")) {
            JsonNode itemsNode = defJsonNode.get("items");
            JsonNode itemsTypeNode = itemsNode.get("type");
            propertyType.setArrayType(itemsTypeNode.textValue());
        } else if (typeNode.textValue().equals("object")) {
            JsonNode additionalPropertiesNode = defJsonNode.get("additionalProperties");
            if (additionalPropertiesNode != null) {
                JsonNode additionalPropertiesTypeNode = additionalPropertiesNode.get("type");
                if (additionalPropertiesTypeNode != null) {
                    propertyType.setMapType(additionalPropertiesTypeNode.textValue());
                }
            }
        }

        return propertyType;
    }

    private static List<List<String>> generateParameterRows(List<PropertyInfo> propertyInfoList) {
        List<List<String>> parameterRows = new ArrayList<>();

        for (PropertyInfo propertyInfo : propertyInfoList) {
            List<String> parameterRow = new ArrayList<>();
            parameterRow.add(propertyInfo.getName());
            parameterRow.add(propertyInfo.getDescription() != null ? propertyInfo.getDescription() : "No description");
            if (propertyInfo.getPropertyType().getEnumValues() != null) {
                parameterRow.add(String.join("<br/>", propertyInfo.getPropertyType().getEnumValues()));
            } else if (propertyInfo.getPropertyType().getArrayType() != null) {
                parameterRow.add(propertyInfo.getPropertyType().getArrayType() + " (list)");
            } else if (propertyInfo.getPropertyType().getMapType() != null) {
                parameterRow.add(propertyInfo.getPropertyType().getMapType() + " (map)");
            } else {
                parameterRow.add(propertyInfo.getPropertyType().getType());
            }
            parameterRow.add(propertyInfo.isRequired() ? "✔" : "");
            parameterRow.add(propertyInfo.getDefaultValue() != null ? propertyInfo.getDefaultValue() : "");
            parameterRows.add(parameterRow);
        }

        return parameterRows;
    }

    private static final List<String> CONTENT_SPEC_TABLE_COLUMNS = List.of("Index", "Name", "Media Type", "Description");

    private static String generateContentMarkdown(ActionOptions.InputSpec ioSpec) {
        MarkdownBuilder docs = new MarkdownBuilder();

        if (ioSpec.getContentSummary() != null) {
            docs.append("### Content\n");
            docs.append(ioSpec.getContentSummary()).append("\n\n");
        } else if (ioSpec.getContentSpecs() != null) {
            docs.append("### Content\n");
            docs.addSimpleTable(CONTENT_SPEC_TABLE_COLUMNS, generateContentSpecRows(ioSpec.getContentSpecs()));
            docs.append('\n');
        }

        return docs.build();
    }

    private static List<List<String>> generateContentSpecRows(List<ActionOptions.ContentSpec> contentSpecs) {
        List<List<String>> contentSpecRows = new ArrayList<>();
        int inputIndex = 0;
        for (ActionOptions.ContentSpec contentSpec : contentSpecs) {
            contentSpecRows.add(List.of(Integer.toString(inputIndex++), contentSpec.getName(),
                    contentSpec.getMediaType(), contentSpec.getDescription()));
        }
        return contentSpecRows;
    }

    private static final List<String> KEYED_TABLE_COLUMNS = List.of("Key", "Description");

    private static String generateMetadataMarkdown(ActionOptions.InputSpec ioSpec) {
        MarkdownBuilder docs = new MarkdownBuilder();

        if (ioSpec.getMetadataSummary() != null) {
            docs.append("### Metadata\n");
            docs.append(ioSpec.getMetadataSummary()).append("\n\n");
        } else if (ioSpec.getMetadataDescriptions() != null) {
            docs.append("### Metadata\n");
            docs.addSimpleTable(KEYED_TABLE_COLUMNS, generateRows(ioSpec.getMetadataDescriptions()));
            docs.append('\n');
        }

        return docs.build();
    }

    private static List<List<String>> generateRows(List<ActionOptions.KeyedDescription> keyedDescriptions) {
        List<List<String>> rows = new ArrayList<>();
        for (ActionOptions.KeyedDescription keyedDescription : keyedDescriptions) {
            rows.add(List.of(keyedDescription.getKey(), keyedDescription.getDescription()));
        }
        return rows;
    }

    private static String generateAnnotationsMarkdown(ActionOptions.OutputSpec ioSpec) {
        MarkdownBuilder docs = new MarkdownBuilder();

        if (ioSpec.getAnnotationsSummary() != null) {
            docs.append("### Annotations\n");
            docs.append(ioSpec.getAnnotationsSummary()).append("\n\n");
        } else if (ioSpec.getAnnotationDescriptions() != null) {
            docs.append("### Annotations\n");
            docs.addSimpleTable(KEYED_TABLE_COLUMNS, generateRows(ioSpec.getAnnotationDescriptions()));
            docs.append('\n');
        }

        return docs.build();
    }
}