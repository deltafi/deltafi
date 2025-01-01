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
package org.deltafi.test.action;

import lombok.Data;
import lombok.experimental.SuperBuilder;

/**
 * @deprecated Use the DeltaFiTestRunner to save content that will be made available to the action under test
 */
@Deprecated
@Data
@SuperBuilder
public class IOContent {
    String name;
    String contentType;
    long offset;

    // if content is set, test data will not be loaded from disk
    byte[] content;
}
