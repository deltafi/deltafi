/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

import useGraphQL from "./useGraphQL";

interface TypeRef {
  kind: string;
  name: string | null;
  ofType: TypeRef | null;
}

interface FieldArg {
  name: string;
  type: TypeRef;
}

interface IntrospectedField {
  name: string;
  type: TypeRef;
  args: FieldArg[];
}

interface IntrospectedType {
  name: string;
  kind: string;
  fields: IntrospectedField[] | null;
}

// Module-level cache shared across all composable instances
const typeCache = new Map<string, IntrospectedType>();
let fullSchemaLoaded = false;

const INTROSPECTION_QUERY = `
  query IntrospectSchema {
    __schema {
      types {
        name
        kind
        fields {
          name
          type {
            kind
            name
            ofType {
              kind
              name
              ofType {
                kind
                name
                ofType {
                  kind
                  name
                  ofType {
                    kind
                    name
                    ofType {
                      kind
                      name
                      ofType {
                        kind
                        name
                      }
                    }
                  }
                }
              }
            }
          }
          args {
            name
            type {
              kind
              name
              ofType {
                kind
                name
              }
            }
          }
        }
      }
    }
  }
`;

function unwrapType(typeRef: TypeRef): { name: string; kind: string } {
  let current = typeRef;
  while (current.ofType) {
    current = current.ofType;
  }
  return { name: current.name!, kind: current.kind };
}

function hasRequiredArgs(field: IntrospectedField): boolean {
  return field.args.some((arg) => arg.type.kind === "NON_NULL");
}

function isLeafKind(kind: string): boolean {
  return kind === "SCALAR" || kind === "ENUM";
}

function buildFieldSelection(
  typeName: string,
  visited: Set<string>,
  maxDepth: number,
  currentDepth: number,
  skipFieldsWithRequiredArgs: boolean
): Record<string, any> | null {
  if (currentDepth > maxDepth) return null;

  const type = typeCache.get(typeName);
  if (!type || !type.fields) return null;

  const selection: Record<string, any> = {};

  for (const field of type.fields) {
    // Skip fields that start with __ (introspection meta-fields)
    if (field.name.startsWith("__")) continue;

    // Skip fields with required arguments since we don't know what values to pass
    if (skipFieldsWithRequiredArgs && hasRequiredArgs(field)) continue;

    const { name: fieldTypeName, kind } = unwrapType(field.type);

    if (isLeafKind(kind)) {
      selection[field.name] = true;
    } else if (kind === "OBJECT") {
      // Prevent circular references
      if (visited.has(fieldTypeName)) continue;

      const childVisited = new Set(visited);
      childVisited.add(fieldTypeName);

      const nested = buildFieldSelection(
        fieldTypeName,
        childVisited,
        maxDepth,
        currentDepth + 1,
        skipFieldsWithRequiredArgs
      );

      if (nested && Object.keys(nested).length > 0) {
        selection[field.name] = nested;
      }
    }
    // Skip INTERFACE, UNION, INPUT_OBJECT types
  }

  return Object.keys(selection).length > 0 ? selection : null;
}

export interface BuildQueryFieldsOptions {
  maxDepth?: number;
  skipFieldsWithRequiredArgs?: boolean;
}

export default function useGraphQLIntrospection() {
  const { response, queryGraphQLWithVariables, loading, errors } = useGraphQL();

  const loadSchema = async (): Promise<void> => {
    if (fullSchemaLoaded) return;

    await queryGraphQLWithVariables(INTROSPECTION_QUERY, {}, "IntrospectSchema");

    const types: IntrospectedType[] = response.value.data.__schema.types;
    for (const type of types) {
      typeCache.set(type.name, type);
    }
    fullSchemaLoaded = true;
  };

  const buildQueryFields = async (
    typeName: string,
    options: BuildQueryFieldsOptions = {}
  ): Promise<Record<string, any>> => {
    const { maxDepth = 5, skipFieldsWithRequiredArgs = true } = options;

    await loadSchema();

    const visited = new Set<string>([typeName]);
    const fields = buildFieldSelection(typeName, visited, maxDepth, 0, skipFieldsWithRequiredArgs);
    return fields || {};
  };

  return { buildQueryFields, loadSchema, loading, errors };
}
