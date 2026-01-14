/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>

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
// ABOUTME: Utility functions for computing DeltaFile state from flows and actions.
// ABOUTME: Provides metadata and content resolution across action chains.

export type FlowInput = {
  metadata?: Record<string, any>;
  content?: Array<{ name: string; mediaType: string; size: number }>;
};

export type Action = {
  metadata?: Record<string, any>;
  deleteMetadataKeys?: string[];
  content?: Array<{ name: string; mediaType: string; size: number }>;
};

export type Flow = {
  name: string;
  input?: FlowInput;
  actions?: Action[];
};

/**
 * Compute the output metadata for a flow by applying all action modifications.
 * Starts with input metadata, applies each action's metadata additions,
 * then removes any deleteMetadataKeys.
 */
export const computeOutputMetadata = (flow: Flow): Record<string, any> => {
  if (!flow) return {};
  const result = { ...(flow.input?.metadata || {}) };
  for (const action of flow.actions || []) {
    if (action.metadata) Object.assign(result, action.metadata);
    for (const key of action.deleteMetadataKeys || []) delete result[key];
  }
  return result;
};

/**
 * Get the output content for a flow.
 * Returns the last action's content if any action produced content,
 * otherwise returns the input content.
 */
export const computeOutputContent = (flow: Flow): Array<{ name: string; mediaType: string; size: number }> => {
  if (!flow) return [];
  let content = flow.input?.content || [];
  for (const action of flow.actions || []) {
    if (action.content && action.content.length > 0) {
      content = action.content;
    }
  }
  return content;
};
