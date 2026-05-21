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
// ABOUTME: Backwards-compatibility Jackson module that deserializes Spring DataSize values.
// ABOUTME: Deprecated bridge kept until 3.0 so existing plugin parameter fields still deserialize.
package org.deltafi.common.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.deltafi.common.constant.DeprecatedSince;
import org.springframework.util.unit.DataSize;

import java.io.IOException;

/**
 * Jackson module that adds deserialization support for {@link org.springframework.util.unit.DataSize}
 * values, mirroring {@link DataSizeModule} for the pure-Java {@code org.deltafi.common.util.DataSize}.
 *
 * @deprecated Kept only so existing plugins whose action parameter fields are typed
 * {@code org.springframework.util.unit.DataSize} continue to deserialize. Migrate those fields to
 * {@code org.deltafi.common.util.DataSize}. This bridge will be removed in 3.0.
 */
@Deprecated(since = DeprecatedSince.VERSION_2_55_1, forRemoval = true)
public class SpringDataSizeModule extends SimpleModule {
    /** Create a SpringDataSizeModule and register the Spring DataSize deserializer */
    public SpringDataSizeModule() {
        super("SpringDataSizeModule");
        addDeserializer(DataSize.class, new SpringDataSizeDeserializer());
    }

    /** Deserializer that parses Spring DataSize strings like "10MB" */
    public static class SpringDataSizeDeserializer extends JsonDeserializer<DataSize> {
        @Override
        public DataSize deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return DataSize.parse(p.getText());
        }
    }
}
