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
package org.deltafi.core.action.egress;

/**
 * Policy for handling null content in egress actions.
 */
public enum NoContentPolicy {
    /**
     * Filter the DeltaFile when content is null (return FilterResult)
     */
    FILTER,
    
    /**
     * Return an error when content is null (return ErrorResult)
     */
    ERROR,
    
    /**
     * Send empty content when content is null (continue with zero data)
     */
    SEND_EMPTY
}
