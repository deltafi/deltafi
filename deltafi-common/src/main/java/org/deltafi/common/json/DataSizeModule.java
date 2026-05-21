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
// ABOUTME: Jackson module that registers a deserializer for DataSize values.
// ABOUTME: Allows action parameters to use DataSize fields parsed from strings like "10MB".
package org.deltafi.common.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.deltafi.common.util.DataSize;

import java.io.IOException;

/**
 * Jackson module that adds deserialization support for {@link DataSize} values
 */
public class DataSizeModule extends SimpleModule {
    /** Create a DataSizeModule and register the DataSize deserializer */
    public DataSizeModule() {
        super("DataSizeModule");
        addDeserializer(DataSize.class, new DataSizeDeserializer());
    }

    /** Deserializer that parses DataSize strings like "10MB" */
    public static class DataSizeDeserializer extends JsonDeserializer<DataSize> {
        @Override
        public DataSize deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return DataSize.parse(p.getText());
        }
    }
}
