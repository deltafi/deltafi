/*
  The MIT License

  Copyright (c) 2017-2019 EclipseSource Munich
  https://github.com/eclipsesource/jsonforms

  Permission is hereby granted, free of charge, to any person obtaining a copy
  of this software and associated documentation files (the "Software"), to deal
  in the Software without restriction, including without limitation the rights
  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
  copies of the Software, and to permit persons to whom the Software is
  furnished to do so, subject to the following conditions:

  The above copyright notice and this permission notice shall be included in
  all copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
  THE SOFTWARE.
*/
import _ from "lodash";
import startCase from "lodash/startCase";
import keys from "lodash/keys";
import { ControlElement, createControlElement, isGroup, isLayout, JsonSchema, LabelElement, Layout, UISchemaElement } from "@jsonforms/core";

/**
 * Creates a new ILayout.
 * @param layoutType The type of the laoyut
 * @returns the new ILayout
 */
const createLayout = (layoutType: string): Layout => ({
  type: layoutType,
  elements: [],
});

/**
 * Wraps the given {@code uiSchema} in a Layout if there is none already.
 * @param uischema The ui schema to wrap in a layout.
 * @param layoutType The type of the layout to create.
 * @returns the wrapped uiSchema.
 */
const wrapInLayoutIfNecessary = (uischema: UISchemaElement, layoutType: string): Layout => {
  if (!_.isEmpty(uischema) && !isLayout(uischema)) {
    const verticalLayout: Layout = createLayout(layoutType);
    verticalLayout.elements.push(uischema);

    return verticalLayout;
  }

  return uischema as Layout;
};

/**
 * Adds the given {@code labelName} to the {@code layout} if it exists
 * @param layout
 *      The layout which is to receive the label
 * @param labelName
 *      The name of the schema
 */
const addLabel = (layout: Layout, labelName: string) => {
  if (!_.isEmpty(labelName)) {
    const fixedLabel = startCase(labelName);
    if (isGroup(layout)) {
      layout.label = fixedLabel;
    } else {
      // add label with name
      const label: LabelElement = {
        type: "Label",
        text: fixedLabel,
      };
      layout.elements.push(label);
    }
  }
};

/**
 * Returns whether the given {@code jsonSchema} is a combinator ({@code oneOf}, {@code anyOf}, {@code allOf}) at the root level
 * @param jsonSchema
 *      the schema to check
 */
const isCombinator = (jsonSchema: JsonSchema): boolean => {
  return !_.isEmpty(jsonSchema) && (!_.isEmpty(jsonSchema.oneOf) || !_.isEmpty(jsonSchema.anyOf) || !_.isEmpty(jsonSchema.allOf));
};

const generateDeepUISchema = (jsonSchema: JsonSchema, schemaElements: UISchemaElement[], currentRef: string, schemaName: string, layoutType: string, rootSchema?: JsonSchema): UISchemaElement => {
  if (!_.isEmpty(jsonSchema) && jsonSchema.$ref !== undefined) {
    return generateDeepUISchema(resolveSchema(rootSchema ? rootSchema : jsonSchema, jsonSchema.$ref), schemaElements, currentRef, schemaName, layoutType, rootSchema);
  }

  if (isCombinator(jsonSchema)) {
    const controlObject: ControlElement = createControlElement(currentRef);
    schemaElements.push(controlObject);
    return controlObject;
  }

  const types = deriveTypes(jsonSchema);
  if (types.length === 0) {
    //@ts-ignore
    return null;
  }

  if (types.length > 1) {
    const controlObject: ControlElement = createControlElement(currentRef);
    schemaElements.push(controlObject);
    return controlObject;
  }

  if (types[0] === "object") {
    const layout: Layout = createLayout(layoutType);
    schemaElements.push(layout);

    if (jsonSchema.properties && keys(jsonSchema.properties).length > 1) {
      addLabel(layout, schemaName);
    }

    if (!_.isEmpty(jsonSchema.properties)) {
      // traverse properties
      const nextRef: string = currentRef + "/properties";
      Object.keys(jsonSchema.properties).map((propName) => {
        // @ts-ignore
        let value = jsonSchema.properties[propName];
        const ref = `${nextRef}/${propName}`;
        if (value.$ref !== undefined) {
          // @ts-ignore
          value = resolveSchema(rootSchema, value.$ref);
        }
        generateDeepUISchema(value, layout.elements, ref, propName, layoutType, rootSchema);
      });
    }

    return layout;
  }

  if (types[0] === "array") {
    const layout: Layout = createLayout("Control");
    // @ts-ignore
    delete layout.elements;
    // @ts-ignore
    layout.scope = currentRef;

    schemaElements.push(layout);

    const ref = "#";
    const propName = "";

    if (!Array.isArray(jsonSchema.items)) {
      const value = jsonSchema.items;
      const inArrayUiSchema = generateDeepUISchema(
        // @ts-ignore
        value,
        [],
        ref,
        propName,
        layoutType,
        rootSchema
      );
      if (inArrayUiSchema) {
        layout.options = {
          detail: inArrayUiSchema,
        };
      }
    }
    return layout;
  }

  switch (types[0]) {
    case "object": // object items will be handled by the object control itself
    /* falls through */
    case "array": // array items will be handled by the array control itself
    /* falls through */
    case "string":
    /* falls through */
    case "number":
    /* falls through */
    case "integer":
    /* falls through */
    case "boolean":
      const controlObject: ControlElement = createControlElement(currentRef); // eslint-disable-line
      schemaElements.push(controlObject);

      return controlObject;
    default:
      throw new Error("Unknown type: " + JSON.stringify(jsonSchema));
  }
};

const deriveTypes = (jsonSchema: JsonSchema): string[] => {
  if (_.isEmpty(jsonSchema)) {
    return [];
  }
  if (!_.isEmpty(jsonSchema.type) && typeof jsonSchema.type === "string") {
    return [jsonSchema.type];
  }
  if (_.isArray(jsonSchema.type)) {
    return jsonSchema.type;
  }
  if (!_.isEmpty(jsonSchema.properties) || !_.isEmpty(jsonSchema.additionalProperties)) {
    return ["object"];
  }
  if (!_.isEmpty(jsonSchema.items)) {
    return ["array"];
  }

  if (!_.isEmpty(jsonSchema.allOf)) {
    const allOfType = _.find(jsonSchema.allOf, (schema: JsonSchema) => deriveTypes(schema).length !== 0);

    if (allOfType) {
      return deriveTypes(allOfType);
    }
  }
  // ignore all remaining cases
  return [];
};

export const resolveSchema = (schema: JsonSchema, schemaPath: string, rootSchema?: JsonSchema): JsonSchema => {
  if (_.isEmpty(schema)) {
    // @ts-ignore
    return undefined;
  }
  const validPathSegments = schemaPath.split("/");
  const invalidSegment = (pathSegment: string) => pathSegment === "#" || pathSegment === undefined || pathSegment === "";
  const resultSchema = validPathSegments.reduce((curSchema, pathSegment) => {
    curSchema = curSchema === undefined || curSchema.$ref === undefined ? curSchema : resolveSchema(schema, curSchema.$ref);
    return invalidSegment(pathSegment) ? curSchema : _.get(curSchema, pathSegment);
  }, schema);
  // TODO: because schema is already scoped we might end up with refs pointing
  // outside of the current schema. It would be better if we'd always could deal
  // with absolute paths here, so that we don't need to keep two different
  // schemas around
  if (resultSchema !== undefined && resultSchema.$ref !== undefined) {
    try {
      return retrieveResolvableSchema(schema, resultSchema.$ref);
    } catch (e) {
      // @ts-ignore
      return retrieveResolvableSchema(rootSchema, resultSchema.$ref);
    }
  }

  return resultSchema;
};

/**
 * Normalizes the schema and resolves the given ref.
 *
 * @param {JsonSchema} full the JSON schema to resolved the reference against
 * @param {string} reference the reference to be resolved
 * @returns {JsonSchema} the resolved sub-schema
 */
// disable rule because resolve is mutually recursive
// tslint:disable:only-arrow-functions
function retrieveResolvableSchema(full: JsonSchema, reference: string): JsonSchema {
  // tslint:enable:only-arrow-functions
  const child = resolveSchema(full, reference);
  const allRefs = findAllRefs(child);
  const innerSelfReference = allRefs[reference];
  if (innerSelfReference !== undefined) {
    innerSelfReference.$ref = "#";
  }

  return child;
}

/**
 * Map for storing refs and the respective schemas they are pointing to.
 */
export interface ReferenceSchemaMap {
  [ref: string]: JsonSchema;
}

const isObjectSchema = (schema: JsonSchema): boolean => {
  return schema.properties !== undefined;
};
const isArraySchema = (schema: JsonSchema): boolean => {
  return schema.type === "array" && schema.items !== undefined;
};

/**
 * Finds all references inside the given schema.
 *
 * @param schema The {@link JsonSchema} to find the references in
 * @param result The initial result map, default: empty map (this parameter is used for recursion
 *               inside the function)
 * @param resolveTuples Whether arrays of tuples should be considered; default: false
 */
export const findAllRefs = (schema: JsonSchema, result: ReferenceSchemaMap = {}, resolveTuples = false): ReferenceSchemaMap => {
  if (isObjectSchema(schema)) {
    // @ts-ignore
    Object.keys(schema.properties).forEach((key) =>
      // @ts-ignore
      findAllRefs(schema.properties[key], result)
    );
  }
  if (isArraySchema(schema)) {
    if (Array.isArray(schema.items)) {
      if (resolveTuples) {
        const items: JsonSchema[] = schema.items;
        items.forEach((child) => findAllRefs(child, result));
      }
    } else {
      // @ts-ignore
      findAllRefs(schema.items, result);
    }
  }
  if (Array.isArray(schema.anyOf)) {
    const anyOf: JsonSchema[] = schema.anyOf;
    anyOf.forEach((child) => findAllRefs(child, result));
  }
  if (schema.$ref !== undefined) {
    result[schema.$ref] = schema;
  }

  return result;
};

/**
 * Generate a deep nested default UI schema.
 * @param {JsonSchema} jsonSchema the JSON schema to generated a UI schema for
 * @param {string} layoutType the desired layout type for the root layout
 *        of the generated UI schema
 */
export const generateDeepDefaultUISchema = (jsonSchema: JsonSchema, layoutType = "VerticalLayout", prefix = "#", rootSchema = jsonSchema): UISchemaElement => wrapInLayoutIfNecessary(generateDeepUISchema(jsonSchema, [], prefix, "", layoutType, rootSchema), layoutType);
