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
package org.deltafi.core.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import graphql.scalars.id.UUIDScalar;
import graphql.schema.Coercing;

import javax.ws.rs.core.MediaType;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Constants {
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    public static final Map<Class<?>, Coercing<?, ?>> SCALARS = Map.of(UUID.class, UUIDScalar.INSTANCE.getCoercing());

    public static final OffsetDateTime START_TIME = OffsetDateTime.of(2021, 7, 11, 13, 44, 22, 183, ZoneOffset.UTC);
    public static final OffsetDateTime STOP_TIME = OffsetDateTime.of(2021, 7, 11, 13, 44, 22, 184, ZoneOffset.UTC);

    public static final String REST_DATA_SOURCE_NAME = "sampleRestDataSource";
    public static final String TIMED_DATA_SOURCE_NAME = "sampleTimedDataSource";
    public static final String TIMED_DATA_SOURCE_WITH_ANNOTATION_CONFIG_NAME = "sampleTimedDataSourceAnnot";
    public static final String TIMED_DATA_SOURCE_ERROR_NAME = "sampleTimedDataSourceError";
    public static final String ON_ERROR_DATA_SOURCE_NAME = "sampleOnErrorDataSource";
    public static final String DATA_SINK_FLOW_NAME = "sampleEgress";
    public static final String TRANSFORM_FLOW_NAME = "sampleTransform";
    public static final String MISSING_PUBLISH_TOPIC = "missingPublishTopic";

    public static final String CONTENT_DATA = "STARLORD was here";
    public static final String METADATA = "{\"key\": \"value\"}";
    public static final String FILENAME = "incoming.txt";
    public static final String MEDIA_TYPE = MediaType.APPLICATION_OCTET_STREAM;
    public static final String USERNAME = "myname";

    public static final Map<String, String> SOURCE_METADATA = new HashMap<>(Map.of("AuthorizedBy", "XYZ", "removeMe", "whatever"));
    public static final Map<String, String> TRANSFORM_METADATA = Map.of("sampleType", "sample-type", "sampleVersion", "2.1");
}
