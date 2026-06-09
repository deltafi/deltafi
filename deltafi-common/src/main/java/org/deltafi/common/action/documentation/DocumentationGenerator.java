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
package org.deltafi.common.action.documentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.ActionDescriptor;
import org.deltafi.common.types.ActionOptions;
import org.deltafi.common.util.MarkdownBuilder;

import java.util.*;

/**
 * Generates Markdown documentation from action descriptors and their JSON schemas.
 * <p>
 * This Markdown is the single source of action documentation, rendered as-is by every client
 * (markdown-it in the UI drawer and the docs site, glamour in the TUI). The layout is a title +
 * type chip, the description, a Parameters table that links structured fields to a Types section
 * (one table per named/anonymous structured type), then Input/Output/Filters/Errors/Notes/Details.
 */
@Slf4j
public class DocumentationGenerator {

    private static final String PROPERTIES = "properties";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // cells are pre-formatted (backticks/links), so columns are not JSON-escaped; Req is centered
    private static final List<MarkdownBuilder.ColumnDef> PARAMETER_TABLE_COLUMNS = List.of(
            new MarkdownBuilder.ColumnDef("Name", false, false),
            new MarkdownBuilder.ColumnDef("Type", false, false),
            new MarkdownBuilder.ColumnDef("Req", true, false),
            new MarkdownBuilder.ColumnDef("Default", false, false),
            new MarkdownBuilder.ColumnDef("Description", false, false));
    private static final List<MarkdownBuilder.ColumnDef> CONTENT_SPEC_TABLE_COLUMNS = List.of(
            new MarkdownBuilder.ColumnDef("#", false, false),
            new MarkdownBuilder.ColumnDef("Name", false, false),
            new MarkdownBuilder.ColumnDef("Media Type", false, false),
            new MarkdownBuilder.ColumnDef("Description", false, false));
    private static final List<MarkdownBuilder.ColumnDef> KEYED_TABLE_COLUMNS = List.of(
            new MarkdownBuilder.ColumnDef("Key", false, false),
            new MarkdownBuilder.ColumnDef("Description", false, false));

    private DocumentationGenerator() {
    }

    /**
     * Generate Markdown documentation for an action.
     *
     * @param actionDescriptor the ActionDescriptor describing the action
     * @return the generated Markdown documentation
     */
    // promotes the deprecated ActionDescriptor.description into actionOptions for descriptors
    // emitted by older plugins; remove in 3.0 with the description field.
    @SuppressWarnings({"deprecation", "removal"})
    public static String generateActionDocs(ActionDescriptor actionDescriptor) {
        if (actionDescriptor.getActionOptions() == null) {
            if (actionDescriptor.getDescription() == null) {
                return null;
            }
            actionDescriptor.setActionOptions(ActionOptions.builder().description(actionDescriptor.getDescription()).build());
        }

        ActionOptions options = actionDescriptor.getActionOptions();
        MarkdownBuilder docs = new MarkdownBuilder();

        String name = actionDescriptor.getName();
        int lastDotIndex = name.lastIndexOf('.');
        docs.append("# ").append(lastDotIndex != -1 ? name.substring(lastDotIndex + 1) : name).append('\n');
        if (actionDescriptor.getType() != null) {
            docs.append('`').append(actionDescriptor.getType().name()).append("`  ·  `").append(name).append("`\n\n");
        } else {
            docs.append('`').append(name).append("`\n\n");
        }
        if (options.getDescription() != null && !options.getDescription().isBlank()) {
            docs.append(options.getDescription()).append("\n\n");
        }

        appendParameters(docs, actionDescriptor.getSchema());

        if (options.getInputSpec() != null) {
            docs.append("## Input\n\n");
            appendIO(docs, options.getInputSpec());
        }

        if (options.getOutputSpec() != null) {
            docs.append("## Output\n\n");
            if (options.getOutputSpec().isPassthrough()) {
                docs.append("_Content is passed through unchanged._\n\n");
            } else {
                appendIO(docs, options.getOutputSpec());
            }
            appendKeyed(docs, "Annotations", options.getOutputSpec().getAnnotationsSummary(),
                    options.getOutputSpec().getAnnotationDescriptions());
        }

        appendConditions(docs, "Filters", options.getFilters());
        appendConditions(docs, "Errors", options.getErrors());

        if (options.getNotes() != null && !options.getNotes().isEmpty()) {
            docs.append("## Notes\n\n");
            for (String note : options.getNotes()) {
                docs.append("- ").append(note).append('\n');
            }
            docs.append('\n');
        }

        if (options.getDetails() != null) {
            docs.append("## Details\n\n").append(options.getDetails()).append('\n');
        }

        return docs.build();
    }

    // --- parameters + types ---

    private static void appendParameters(MarkdownBuilder docs, Map<String, Object> schema) {
        docs.append("## Parameters\n\n");
        if (schema == null || schema.isEmpty()) {
            docs.append("_No parameters._\n\n");
            return;
        }

        JsonNode schemaNode = OBJECT_MAPPER.convertValue(schema, JsonNode.class);
        JsonNode defs = schemaNode.get("$defs");

        List<List<String>> rows = paramRows(schemaNode, defs, true, null);
        if (rows.isEmpty()) {
            docs.append("_No parameters._\n\n");
            return;
        }
        appendTable(docs, PARAMETER_TABLE_COLUMNS, rows);

        appendTypes(docs, schemaNode, defs);
    }

    /**
     * Each named ($defs) structured type is documented once, plus a card for any anonymous top-level
     * object/union parameter. A structured field links into this section rather than re-documenting
     * its inner fields at every use site.
     */
    private static void appendTypes(MarkdownBuilder docs, JsonNode schemaNode, JsonNode defs) {
        List<TypeCard> cards = new ArrayList<>();

        if (defs != null) {
            defs.properties().forEach(entry -> {
                if (isStructuredDef(entry.getValue())) {
                    cards.add(buildType(entry.getKey(), entry.getValue(), defs));
                }
            });
        }

        JsonNode propertiesNode = schemaNode.get(PROPERTIES);
        if (propertiesNode != null) {
            propertiesNode.properties().forEach(entry -> {
                if (refTarget(entry.getValue()) != null) {
                    return;
                }
                JsonNode resolved = resolveSchema(entry.getValue(), defs);
                if (unionNode(resolved, defs) != null || objectWithProperties(resolved, defs) != null) {
                    cards.add(buildType(entry.getKey(), resolved, defs));
                }
            });
        }

        if (cards.isEmpty()) {
            return;
        }

        docs.append("## Types\n\n");
        for (TypeCard card : cards) {
            docs.append("### ").append(card.title()).append("\n\n");
            if (card.variants() != null) {
                for (Variant variant : card.variants()) {
                    docs.append("**").append(variant.label()).append("**\n\n");
                    appendTable(docs, PARAMETER_TABLE_COLUMNS, variant.rows());
                }
            } else {
                appendTable(docs, PARAMETER_TABLE_COLUMNS, card.rows());
            }
        }
    }

    private static TypeCard buildType(String name, JsonNode typeSchema, JsonNode defs) {
        JsonNode union = unionNode(typeSchema, defs);
        if (union != null) {
            List<Variant> variants = new ArrayList<>();
            union.forEach(branchRef -> {
                JsonNode branch = resolveSchema(branchRef, defs);
                String discriminator = findDiscriminatorProperty(branch);
                variants.add(new Variant(variantLabel(branch, discriminator), paramRows(branch, defs, false, discriminator)));
            });
            return new TypeCard(name, null, variants);
        }
        JsonNode object = objectWithProperties(typeSchema, defs);
        return new TypeCard(name, object != null ? paramRows(object, defs, false, null) : List.of(), null);
    }

    /**
     * Build the rows of a parameter/field table. When {@code linkAnonymous} is set, an anonymous
     * top-level object/union field links to its card in the Types section (named by the field).
     */
    private static List<List<String>> paramRows(JsonNode objectSchema, JsonNode defs, boolean linkAnonymous, String exclude) {
        JsonNode propertiesNode = objectSchema.get(PROPERTIES);
        if (propertiesNode == null) {
            return List.of();
        }

        Set<String> required = new HashSet<>();
        JsonNode requiredNode = objectSchema.get("required");
        if (requiredNode != null) {
            requiredNode.forEach(node -> required.add(node.textValue()));
        }

        List<List<String>> rows = new ArrayList<>();
        propertiesNode.properties().forEach(entry -> {
            String fieldName = entry.getKey();
            if (fieldName.equals(exclude)) {
                return;
            }
            JsonNode fieldSchema = entry.getValue();
            JsonNode defaultNode = fieldSchema.get("default");
            String defaultValue = defaultNode != null ? defaultNode.asText() : "";
            String description = extractDescription(fieldSchema);
            rows.add(List.of(
                    "`" + escapePipes(fieldName) + "`",
                    typeCell(fieldName, fieldSchema, defs, linkAnonymous),
                    required.contains(fieldName) ? "✔" : "",
                    defaultValue.isEmpty() ? "" : "`" + escapePipes(defaultValue) + "`",
                    description != null ? escapePipes(singleLine(description)) : ""));
        });
        return rows;
    }

    /**
     * The Type-column cell for a field: a named structured type (or list of it) links to its Types
     * card; an anonymous top-level object/union links to a card named by the field; everything else
     * is a compact type expression. The visible text is wrapped in backticks.
     */
    private static String typeCell(String fieldName, JsonNode fieldSchema, JsonNode defs, boolean linkAnonymous) {
        String text;
        String ref = null;

        RefTarget refTarget = refTarget(fieldSchema);
        if (refTarget != null && isStructuredDef(defs == null ? null : defs.get(refTarget.name()))) {
            text = refTarget.list() ? "[" + refTarget.name() + "]" : refTarget.name();
            ref = refTarget.name();
        } else {
            JsonNode resolved = resolveSchema(fieldSchema, defs);
            text = scalarExpr(resolved, defs);
            if (linkAnonymous && (unionNode(resolved, defs) != null || objectWithProperties(resolved, defs) != null)) {
                ref = fieldName;
                if (text.equals("object")) {
                    text = fieldName;
                } else if (text.equals("[object]")) {
                    text = "[" + fieldName + "]";
                }
            }
        }

        text = escapePipes(text);
        return ref != null ? "[`" + text + "`](#" + slug(ref) + ")" : "`" + text + "`";
    }

    /** Escape literal pipes so type expressions like "string (A | B)" and free text don't break a Markdown table. */
    private static String escapePipes(String value) {
        return value.replace("|", "\\|");
    }

    /** Mirror GitHub/VitePress heading anchors: lowercase, runs of non-alphanumerics to a single hyphen. */
    private static String slug(String value) {
        StringBuilder builder = new StringBuilder();
        boolean previousHyphen = false;
        for (char c : value.toLowerCase(Locale.ROOT).toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                builder.append(c);
                previousHyphen = false;
            } else if (!previousHyphen) {
                builder.append('-');
                previousHyphen = true;
            }
        }
        int start = 0;
        int end = builder.length();
        while (start < end && builder.charAt(start) == '-') {
            start++;
        }
        while (end > start && builder.charAt(end - 1) == '-') {
            end--;
        }
        return builder.substring(start, end);
    }

    private record TypeCard(String title, List<List<String>> rows, List<Variant> variants) {
    }

    private record Variant(String label, List<List<String>> rows) {
    }

    // --- schema helpers (shared with the type-expression logic) ---

    /** Extract a field's description, preferring the field-level allOf override over the type's own description. */
    private static String extractDescription(JsonNode fieldSchema) {
        JsonNode allOfNode = fieldSchema.get("allOf");
        if (allOfNode != null && allOfNode.isArray() && !allOfNode.isEmpty()) {
            // First element is the type definition, a second element (if present) carries the field description
            JsonNode descriptionNode = allOfNode.size() > 1 ? allOfNode.get(1).get("description") : null;
            if (descriptionNode == null) {
                descriptionNode = allOfNode.get(0).get("description");
            }
            return descriptionNode != null ? descriptionNode.textValue() : null;
        }
        JsonNode descriptionNode = fieldSchema.get("description");
        return descriptionNode != null ? descriptionNode.textValue() : null;
    }

    /** The named type a field references (directly, allOf-wrapped, or as array items), with whether it is a list. */
    private static RefTarget refTarget(JsonNode fieldSchema) {
        JsonNode node = fieldSchema;
        JsonNode allOf = node.get("allOf");
        if (allOf != null && allOf.isArray() && !allOf.isEmpty()) {
            node = allOf.get(0);
        }
        JsonNode ref = node.get("$ref");
        if (ref != null) {
            return new RefTarget(defName(ref), false);
        }
        if ("array".equals(text(node, "type"))) {
            JsonNode items = node.get("items");
            if (items != null) {
                JsonNode itemsAllOf = items.get("allOf");
                JsonNode itemNode = (itemsAllOf != null && itemsAllOf.isArray() && !itemsAllOf.isEmpty()) ? itemsAllOf.get(0) : items;
                JsonNode itemsRef = itemNode.get("$ref");
                if (itemsRef != null) {
                    return new RefTarget(defName(itemsRef), true);
                }
            }
        }
        return null;
    }

    private static String defName(JsonNode refNode) {
        String ref = refNode.textValue();
        return ref.substring(ref.lastIndexOf('/') + 1);
    }

    /** A def is structured (and so worth its own Types subsection + link) when it is an object with fields or a union. */
    private static boolean isStructuredDef(JsonNode defNode) {
        if (defNode == null) {
            return false;
        }
        if (defNode.has("anyOf") || defNode.has("oneOf")) {
            return true;
        }
        return "object".equals(text(defNode, "type")) && defNode.has(PROPERTIES);
    }

    private record RefTarget(String name, boolean list) {
    }

    /** A compact type expression for a scalar/enum/array/map field. */
    private static String scalarExpr(JsonNode node, JsonNode defs) {
        String type = text(node, "type");
        if ("array".equals(type)) {
            return "[" + itemExpr(node.get("items"), defs) + "]";
        }
        if ("object".equals(type)) {
            JsonNode additionalProperties = node.get("additionalProperties");
            if (additionalProperties != null && additionalProperties.isObject()) {
                return "{string: " + itemExpr(additionalProperties, defs) + "}";
            }
            return "object";
        }
        if ("string".equals(type) && node.has("enum")) {
            List<String> values = new ArrayList<>();
            node.get("enum").forEach(value -> values.add(value.asText()));
            return "string (" + String.join(" | ", values) + ")";
        }
        return type != null ? type : "object";
    }

    /** The type expression for an array element or map value. */
    private static String itemExpr(JsonNode itemSchema, JsonNode defs) {
        if (itemSchema == null) {
            return "object";
        }
        RefTarget ref = refTarget(itemSchema);
        if (ref != null) {
            return ref.name();
        }
        return scalarExpr(resolveSchema(itemSchema, defs), defs);
    }

    /** Collapse a (possibly multi-line) description to a single line. */
    private static String singleLine(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    /**
     * @return the resolved schema of an array's items, or null if the node is not an array.
     */
    private static JsonNode arrayItems(JsonNode node, JsonNode defs) {
        return "array".equals(text(node, "type")) ? resolveSchema(node.get("items"), defs) : null;
    }

    /**
     * @return the anyOf/oneOf array for a union node (directly or via array items), or null if not a union.
     */
    private static JsonNode unionNode(JsonNode node, JsonNode defs) {
        if (node == null) {
            return null;
        }
        JsonNode candidate = node.has("anyOf") || node.has("oneOf") ? node : arrayItems(node, defs);
        if (candidate == null) {
            return null;
        }
        JsonNode anyOf = candidate.get("anyOf");
        return anyOf != null ? anyOf : candidate.get("oneOf");
    }

    /**
     * @return the object schema (directly or via array items) when it has a properties node, else null.
     */
    private static JsonNode objectWithProperties(JsonNode node, JsonNode defs) {
        if (node == null) {
            return null;
        }
        if ("object".equals(text(node, "type")) && node.has(PROPERTIES)) {
            return node;
        }
        JsonNode items = arrayItems(node, defs);
        if (items != null && "object".equals(text(items, "type")) && items.has(PROPERTIES)) {
            return items;
        }
        return null;
    }

    /**
     * Unwrap an allOf type wrapper and resolve a $ref into $defs to get a field's effective schema.
     */
    private static JsonNode resolveSchema(JsonNode fieldSchema, JsonNode defs) {
        if (fieldSchema == null) {
            return null;
        }
        JsonNode node = fieldSchema;
        JsonNode allOf = node.get("allOf");
        if (allOf != null && allOf.isArray() && !allOf.isEmpty()) {
            node = allOf.get(0);
        }
        JsonNode ref = node.get("$ref");
        if (ref != null && defs != null) {
            String defName = ref.textValue().substring(ref.textValue().lastIndexOf('/') + 1);
            JsonNode resolved = defs.get(defName);
            if (resolved != null) {
                return resolved;
            }
        }
        return node;
    }

    /**
     * The discriminator is the lone property pinned to a const value (matches the UI CombinatorRenderer).
     */
    private static String findDiscriminatorProperty(JsonNode branch) {
        JsonNode properties = branch == null ? null : branch.get(PROPERTIES);
        if (properties == null) {
            return null;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (field.getValue().has("const")) {
                return field.getKey();
            }
        }
        return null;
    }

    private static String variantLabel(JsonNode branch, String discriminator) {
        if (discriminator != null) {
            JsonNode constNode = branch.get(PROPERTIES).get(discriminator).get("const");
            if (constNode != null) {
                return constNode.asText();
            }
        }
        JsonNode title = branch.get("title");
        return title != null ? title.asText() : "Option";
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null ? null : value.textValue();
    }

    // --- input/output/conditions ---

    private static void appendIO(MarkdownBuilder docs, ActionOptions.InputSpec ioSpec) {
        if (ioSpec.getContentSummary() != null || ioSpec.getContentSpecs() != null) {
            docs.append("### Content\n\n");
            if (ioSpec.getContentSummary() != null) {
                docs.append(ioSpec.getContentSummary()).append("\n\n");
            } else {
                List<List<String>> rows = new ArrayList<>();
                int index = 0;
                for (ActionOptions.ContentSpec contentSpec : ioSpec.getContentSpecs()) {
                    rows.add(List.of(
                            Integer.toString(index++),
                            contentSpec.getName() != null ? escapePipes(contentSpec.getName()) : "",
                            "`" + escapePipes(contentSpec.getMediaType()) + "`",
                            contentSpec.getDescription() != null ? escapePipes(singleLine(contentSpec.getDescription())) : ""));
                }
                appendTable(docs, CONTENT_SPEC_TABLE_COLUMNS, rows);
            }
        }
        appendKeyed(docs, "Metadata", ioSpec.getMetadataSummary(), ioSpec.getMetadataDescriptions());
    }

    private static void appendKeyed(MarkdownBuilder docs, String label, String summary,
                                    List<ActionOptions.KeyedDescription> keyedDescriptions) {
        if (summary == null && (keyedDescriptions == null || keyedDescriptions.isEmpty())) {
            return;
        }
        docs.append("### ").append(label).append("\n\n");
        if (summary != null) {
            docs.append(summary).append("\n\n");
            return;
        }
        List<List<String>> rows = new ArrayList<>();
        for (ActionOptions.KeyedDescription keyedDescription : keyedDescriptions) {
            rows.add(List.of(
                    "`" + escapePipes(keyedDescription.getKey()) + "`",
                    keyedDescription.getDescription() != null ? escapePipes(singleLine(keyedDescription.getDescription())) : ""));
        }
        appendTable(docs, KEYED_TABLE_COLUMNS, rows);
    }

    private static void appendConditions(MarkdownBuilder docs, String label,
                                         List<ActionOptions.DescriptionWithConditions> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return;
        }
        docs.append("## ").append(label).append("\n\n");
        for (ActionOptions.DescriptionWithConditions condition : conditions) {
            docs.append("- ").append(condition.getDescription()).append('\n');
            if (condition.getConditions() != null) {
                for (String nested : condition.getConditions()) {
                    docs.append("    - ").append(nested).append('\n');
                }
            }
        }
        docs.append('\n');
    }

    /** Append a markdown table followed by a blank line (no-op when there are no rows). */
    private static void appendTable(MarkdownBuilder docs, List<MarkdownBuilder.ColumnDef> columns, List<List<String>> rows) {
        if (rows.isEmpty()) {
            return;
        }
        docs.addTable(columns, rows);
        docs.append('\n');
    }
}
