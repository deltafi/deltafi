/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.content.Segment;
import org.deltafi.common.types.ProcessingType;
import org.deltafi.core.types.IngressResult;

import javax.ws.rs.core.MediaType;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

public class Constants {
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    public static final OffsetDateTime START_TIME = OffsetDateTime.of(2021, 7, 11, 13, 44, 22, 183, ZoneOffset.UTC);
    public static final OffsetDateTime STOP_TIME = OffsetDateTime.of(2021, 7, 11, 13, 44, 22, 184, ZoneOffset.UTC);

    public static final String INGRESS_FLOW_NAME = "sampleIngress";
    public static final String EGRESS_FLOW_NAME = "sampleEgress";
    public static final String TRANSFORM_FLOW_NAME = "sampleTransform";

    public static final String CONTENT = "STARLORD was here";
    public static final String METADATA = "{\"key\": \"value\"}";
    public static final String FILENAME = "incoming.txt";
    public static final String FLOW = "theFlow";
    public static final String MEDIA_TYPE = MediaType.APPLICATION_OCTET_STREAM;
    public static final String USERNAME = "myname";
    public static final String DID = "did";
    public static final ContentReference CONTENT_REFERENCE = new ContentReference(MEDIA_TYPE, new Segment(FILENAME, 0, CONTENT.length(), DID));
    public static final IngressResult INGRESS_RESULT = new IngressResult(FLOW, FILENAME, DID, CONTENT_REFERENCE, ProcessingType.NORMALIZATION);

    public final static Map<String, String> TRANSFORM_METADATA = Map.of("sampleType", "sample-type", "sampleVersion", "2.1");
    public final static Map<String, String> LOAD_METADATA = Map.of("loadSampleType", "load-sample-type", "loadSampleVersion", "2.2");
    public final static Map<String, String> LOAD_WRONG_METADATA = Map.of("loadSampleType", "wrong-sample-type", "loadSampleVersion", "2.2");
}
