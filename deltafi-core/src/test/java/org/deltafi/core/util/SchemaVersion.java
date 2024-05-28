/*
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

import org.bson.Document;
import org.deltafi.core.types.DeltaFile;
import org.deltafi.core.repo.DeltaFileRepo;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SchemaVersion {
    // TODO: make a v8 reference
    private static final String DELTAFILE_JSON_V8 = """
        {
            "_id" : "v6",
            "parentDids" : [ ],
            "childDids" : [ ],
            "requeueCount" : 0,
            "ingressBytes" : NumberLong(36),
            "referencedBytes" : NumberLong(36),
            "totalBytes" : NumberLong(36),
            "stage" : "COMPLETE",
            "inFlight" : false,
            "terminal" : true,
            "contentDeletable" : true,
            "actions" : [
                {
                    "name" : "IngressAction",
                    "type" : "INGRESS",
                    "flow" : "",
                    "state" : "COMPLETE",
                    "created" : ISODate("2023-05-10T10:38:56.521Z"),
                    "modified" : ISODate("2023-05-10T10:38:56.637Z"),
                    "attempt" : 1,
                    "content" : [
                        {
                            "name" : "smoke-f7bc6fc1-0d56-4c5b-9e58-7b758f522ea2",
                            "mediaType" : "application/octet-stream",
                            "segments" : [
                                {
                                    "uuid" : "cf06a93c-d8c4-4784-8269-279b642f6903",
                                    "offset" : NumberLong(0),
                                    "size" : NumberLong(36),
                                    "did" : "f8cea8af-185e-4d96-80c7-68c47a8f5609"
                                }
                            ]
                        }
                    ],
                    "metadata": {},
                    "deleteMetadataKeys": [],
                    "domains" : [],
                    "enrichments" : []
                },
                {
                    "name" : "smoke.SmokeTransformAction",
                    "type" : "TRANSFORM",
                    "flow" : "smoke",
                    "state" : "COMPLETE",
                    "created" : ISODate("2023-05-10T10:38:56.639Z"),
                    "queued" : ISODate("2023-05-10T10:38:56.639Z"),
                    "start" : ISODate("2023-05-10T10:38:56.823Z"),
                    "stop" : ISODate("2023-05-10T10:38:56.833Z"),
                    "modified" : ISODate("2023-05-10T10:38:56.896Z"),
                    "attempt" : 1,
                    "content" : [
                        {
                            "name" : "smoke-f7bc6fc1-0d56-4c5b-9e58-7b758f522ea2",
                            "mediaType" : "application/octet-stream",
                            "segments" : [
                                {
                                    "uuid" : "df06a93c-d8c4-4784-8269-279b642f6903",
                                    "offset" : NumberLong(0),
                                    "size" : NumberLong(36),
                                    "did" : "f8cea8af-185e-4d96-80c7-68c47a8f5609"
                                }
                            ]
                        }
                    ],
                    "metadata": {},
                    "deleteMetadataKeys": [],
                    "domains" : [],
                    "enrichments" : []
                },
                {
                    "name" : "smoke.SmokeLoadAction",
                    "type" : "LOAD",
                    "flow" : "smoke",
                    "state" : "COMPLETE",
                    "created" : ISODate("2023-05-10T10:38:56.639Z"),
                    "queued" : ISODate("2023-05-10T10:38:56.639Z"),
                    "start" : ISODate("2023-05-10T10:38:56.823Z"),
                    "stop" : ISODate("2023-05-10T10:38:56.833Z"),
                    "modified" : ISODate("2023-05-10T10:38:56.896Z"),
                    "attempt" : 1
                    "content" : [],
                    "metadata": {},
                    "deleteMetadataKeys": [],
                    "domains" : [
                        {
                            "name" : "binary",
                            "mediaType" : "text/plain"
                        }
                    ],
                    "enrichments" : []
                },
                {
                    "name" : "smoke.SmokeEnrichAction",
                    "type" : "TRANSFORM",
                    "flow" : "smoke",
                    "state" : "COMPLETE",
                    "created" : ISODate("2023-05-10T10:38:56.639Z"),
                    "queued" : ISODate("2023-05-10T10:38:56.639Z"),
                    "start" : ISODate("2023-05-10T10:38:56.823Z"),
                    "stop" : ISODate("2023-05-10T10:38:56.833Z"),
                    "modified" : ISODate("2023-05-10T10:38:56.896Z"),
                    "attempt" : 1
                    "content" : [],
                    "metadata": {},
                    "deleteMetadataKeys": [],
                    "domains" : [],
                    "enrichments" : [
                        {
                            "name" : "binaryEnrichment",
                            "value" : "binary enrichment value",
                            "mediaType" : "text/plain"
                        }
                    ]
                },
                {
                    "name" : "smoke.SmokeFormatAction",
                    "type" : "FORMAT",
                    "flow" : "smoke",
                    "state" : "COMPLETE",
                    "created" : ISODate("2023-05-10T10:38:56.639Z"),
                    "queued" : ISODate("2023-05-10T10:38:56.639Z"),
                    "start" : ISODate("2023-05-10T10:38:56.823Z"),
                    "stop" : ISODate("2023-05-10T10:38:56.833Z"),
                    "modified" : ISODate("2023-05-10T10:38:56.896Z"),
                    "attempt" : 1,
                    "content" : [
                        {
                            "name" : "smoke-f7bc6fc1-0d56-4c5b-9e58-7b758f522ea2",
                            "mediaType" : "application/octet-stream",
                            "segments" : [
                                {
                                    "uuid" : "ef06a93c-d8c4-4784-8269-279b642f6903",
                                    "offset" : NumberLong(0),
                                    "size" : NumberLong(36),
                                    "did" : "f8cea8af-185e-4d96-80c7-68c47a8f5609"
                                }
                            ]
                        }],
                    "metadata": {},
                    "deleteMetadataKeys": [],
                    "domains" : [],
                    "enrichments" : []
                }
            ],
            "sourceInfo" : {
                "filename" : "smoke-f7bc6fc1-0d56-4c5b-9e58-7b758f522ea2",
                "flow" : "smoke",
                "metadata" : {},
                "processingType" : "NORMALIZATION"
            },
            "annotations" : {
              "keyA" : "valueA",
              "keyB" : "valueB",
            },
            "annotationKeys" : [ "keyA", "keyB" ],
            "egress" : [
                {
                    "flow" : "smoke"
                }
            ],
            "created" : ISODate("2023-05-10T10:38:56.521Z"),
            "modified" : ISODate("2023-05-10T10:38:57.402Z"),
            "egressed" : true,
            "filtered" : false,
            "version" : NumberLong(7),
            "_class" : "org.deltafi.common.types.DeltaFile"
            "schemaVersion" : 6
        }""";

    private static final Document DELTAFILE_LATEST = Document.parse(DELTAFILE_JSON_V8);

    public static final Map<Integer, Document> deltaFileDocs = Map.of(
            8, DELTAFILE_LATEST);

    public static void assertConverted(DeltaFileRepo deltaFileRepo, MongoTemplate mongoTemplate, int version) {
        Document document = deltaFileDocs.get(version);
        mongoTemplate.insert(document, "deltaFile");
        DeltaFile actual = deltaFileRepo.findById(UUID.fromString(document.getString("_id"))).orElseThrow();
        assertEquals(version, actual.getSchemaVersion());
        mongoTemplate.insert(DELTAFILE_LATEST, "deltaFile");
        DeltaFile expected = deltaFileRepo.findById(UUID.fromString(DELTAFILE_LATEST.getString("_id"))).orElseThrow();

        actual.setDid(expected.getDid());
        actual.setSchemaVersion(expected.getSchemaVersion());
        assertEquals(expected, actual);
    }

    public static void assertDeleted(DeltaFileRepo deltaFileRepo, MongoTemplate mongoTemplate, int version) {
        mongoTemplate.insert(deltaFileDocs.get(version), "deltaFile");
        List<DeltaFile> deltaFiles = deltaFileRepo.findForDiskSpaceDelete(1, null, 1);
        assertEquals(1, deltaFiles.size());
        assertEquals(3, deltaFiles.getFirst().referencedSegments().size());
    }
}
