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
package org.deltafi.common.resource;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Objects;

public class Resource {
    public static String read(String path) throws IOException {
        return new String(Objects.requireNonNull(Resource.class.getResourceAsStream(path)).readAllBytes());
    }

    public static String read(String path, Charset charset) throws IOException {
        return new String(Objects.requireNonNull(Resource.class.getResourceAsStream(path)).readAllBytes(), charset);
    }
}
