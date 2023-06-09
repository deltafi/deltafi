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
import org.deltafi.common.types.DeltaFile;
import org.deltafi.core.repo.DeltaFileRepo;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SchemaVersion {
    private static final String DELTAFILE_JSON_V0 = """
        {
            "_id" : "v0",
            "parentDids" : [ ],
            "childDids" : [ ],
            "requeueCount" : 0,
            "ingressBytes" : NumberLong(36),
            "referencedBytes" : NumberLong(36),
            "totalBytes" : NumberLong(36),
            "stage" : "COMPLETE",
            "actions" : [
                {
                    "name" : "IngressAction",
                    "state" : "COMPLETE",
                    "created" : ISODate("2023-05-10T10:38:56.521Z"),
                    "modified" : ISODate("2023-05-10T10:38:56.637Z"),
                    "attempt" : 1
                },
                {
                    "name" : "smoke.SmokeTransformAction",
                    "state" : "COMPLETE",
                    "created" : ISODate("2023-05-10T10:38:56.639Z"),
                    "queued" : ISODate("2023-05-10T10:38:56.639Z"),
                    "start" : ISODate("2023-05-10T10:38:56.823Z"),
                    "stop" : ISODate("2023-05-10T10:38:56.833Z"),
                    "modified" : ISODate("2023-05-10T10:38:56.896Z"),
                    "attempt" : 1
                },
                {
                    "name" : "smoke.SmokeFormatAction",
                    "state" : "COMPLETE",
                    "created" : ISODate("2023-05-10T10:38:56.639Z"),
                    "queued" : ISODate("2023-05-10T10:38:56.639Z"),
                    "start" : ISODate("2023-05-10T10:38:56.823Z"),
                    "stop" : ISODate("2023-05-10T10:38:56.833Z"),
                    "modified" : ISODate("2023-05-10T10:38:56.896Z"),
                    "attempt" : 1
                }
            ],
            "sourceInfo" : {
                "filename" : "smoke-f7bc6fc1-0d56-4c5b-9e58-7b758f522ea2",
                "flow" : "smoke",
                "metadata" : {
                },
                "processingType" : "NORMALIZATION"
            },
            "protocolStack" : [
                {
                    "action" : "IngressAction",
                    "content" : [
                        {
                            "name" : "smoke-f7bc6fc1-0d56-4c5b-9e58-7b758f522ea2",
                            "contentReference" : {
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
                        }
                    ],
                    "metadata" : {}
                },
                {
                    "action" : "smoke.SmokeTransformAction",
                    "content" : [
                        {
                            "name" : "smoke-f7bc6fc1-0d56-4c5b-9e58-7b758f522ea2",
                            "contentReference" : {
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
                        }
                    ],
                    "metadata" : {}
                }
            ],
            "domains" : [
                {
                    "name" : "binary",
                    "mediaType" : "text/plain"
                }
            ],
            "indexedMetadata" : {
              "keyA" : "valueA",
              "keyB" : "valueB",
            },
            "indexedMetadataKeys" : [ "keyA", "keyB"],
            "enrichment" : [
                {
                    "name" : "binaryEnrichment",
                    "value" : "binary enrichment value",
                    "mediaType" : "text/plain"
                }
            ],
            "egress" : [
                {
                    "flow" : "smoke"
                }
            ],
            "formattedData" : [
                {
                    "filename" : "smoke-f7bc6fc1-0d56-4c5b-9e58-7b758f522ea2",
                    "formatAction" : "smoke.SmokeFormatAction",
                    "contentReference" : {
                        "mediaType" : "application/octet-stream",
                        "segments" : [
                            {
                                "uuid" : "ef06a93c-d8c4-4784-8269-279b642f6903",
                                "offset" : NumberLong(0),
                                "size" : NumberLong(36),
                                "did" : "f8cea8af-185e-4d96-80c7-68c47a8f5609"
                            }
                        ]
                    },
                    "metadata" : {
                    },
                    "egressActions" : [
                        "smoke.SmokeEgressAction"
                    ],
                    "validateActions" : [
                        "smoke.SmokeValidateAction"
                    ]
                }
            ],
            "created" : ISODate("2023-05-10T10:38:56.521Z"),
            "modified" : ISODate("2023-05-10T10:38:57.402Z"),
            "egressed" : true,
            "filtered" : false,
            "version" : NumberLong(7),
            "_class" : "org.deltafi.common.types.DeltaFile"
        }""";

    private static final String DELTAFILE_JSON_V1 = """
        {
            "_id" : "v1",
            "parentDids" : [ ],
            "childDids" : [ ],
            "requeueCount" : 0,
            "ingressBytes" : NumberLong(36),
            "referencedBytes" : NumberLong(36),
            "totalBytes" : NumberLong(36),
            "stage" : "COMPLETE",
            "actions" : [
                {
                    "name" : "IngressAction",
                    "state" : "COMPLETE",
                    "created" : ISODate("2023-05-10T10:38:56.521Z"),
                    "modified" : ISODate("2023-05-10T10:38:56.637Z"),
                    "attempt" : 1
                },
                {
                    "name" : "smoke.SmokeTransformAction",
                    "state" : "COMPLETE",
                    "created" : ISODate("2023-05-10T10:38:56.639Z"),
                    "queued" : ISODate("2023-05-10T10:38:56.639Z"),
                    "start" : ISODate("2023-05-10T10:38:56.823Z"),
                    "stop" : ISODate("2023-05-10T10:38:56.833Z"),
                    "modified" : ISODate("2023-05-10T10:38:56.896Z"),
                    "attempt" : 1
                },
                {
                    "name" : "smoke.SmokeFormatAction",
                    "state" : "COMPLETE",
                    "created" : ISODate("2023-05-10T10:38:56.639Z"),
                    "queued" : ISODate("2023-05-10T10:38:56.639Z"),
                    "start" : ISODate("2023-05-10T10:38:56.823Z"),
                    "stop" : ISODate("2023-05-10T10:38:56.833Z"),
                    "modified" : ISODate("2023-05-10T10:38:56.896Z"),
                    "attempt" : 1
                }
            ],
            "sourceInfo" : {
                "filename" : "smoke-f7bc6fc1-0d56-4c5b-9e58-7b758f522ea2",
                "flow" : "smoke",
                "metadata" : {
                },
                "processingType" : "NORMALIZATION"
            },
            "protocolStack" : [
                {
                    "action" : "IngressAction",
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
                    "metadata" : {
                    }
                },
                {
                    "action" : "smoke.SmokeTransformAction",
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
                    "metadata" : {
                    }
                }
            ],
            "domains" : [
                {
                    "name" : "binary",
                    "mediaType" : "text/plain"
                }
            ],
            "indexedMetadata" : {
              "keyA" : "valueA",
              "keyB" : "valueB",
            },
            "indexedMetadataKeys" : [ "keyA", "keyB" ],
            "enrichment" : [
                {
                    "name" : "binaryEnrichment",
                    "value" : "binary enrichment value",
                    "mediaType" : "text/plain"
                }
            ],
            "egress" : [
                {
                    "flow" : "smoke"
                }
            ],
            "formattedData" : [
                {
                    "formatAction" : "smoke.SmokeFormatAction",
                    "content" : {
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
                    },
                    "metadata" : {
                    },
                    "egressActions" : [
                        "smoke.SmokeEgressAction"
                    ],
                    "validateActions" : [
                        "smoke.SmokeValidateAction"
                    ]
                }
            ],
            "created" : ISODate("2023-05-10T10:38:56.521Z"),
            "modified" : ISODate("2023-05-10T10:38:57.402Z"),
            "egressed" : true,
            "filtered" : false,
            "version" : NumberLong(7),
            "_class" : "org.deltafi.common.types.DeltaFile"
            "schemaVersion" : 1
        }""";

    private static final String DELTAFILE_JSON_V2 = """
        {
            "_id" : "v2",
            "parentDids" : [ ],
            "childDids" : [ ],
            "requeueCount" : 0,
            "ingressBytes" : NumberLong(36),
            "referencedBytes" : NumberLong(36),
            "totalBytes" : NumberLong(36),
            "stage" : "COMPLETE",
            "actions" : [
                {
                    "name" : "IngressAction",
                    "state" : "COMPLETE",
                    "created" : ISODate("2023-05-10T10:38:56.521Z"),
                    "modified" : ISODate("2023-05-10T10:38:56.637Z"),
                    "attempt" : 1
                },
                {
                    "name" : "smoke.SmokeTransformAction",
                    "state" : "COMPLETE",
                    "created" : ISODate("2023-05-10T10:38:56.639Z"),
                    "queued" : ISODate("2023-05-10T10:38:56.639Z"),
                    "start" : ISODate("2023-05-10T10:38:56.823Z"),
                    "stop" : ISODate("2023-05-10T10:38:56.833Z"),
                    "modified" : ISODate("2023-05-10T10:38:56.896Z"),
                    "attempt" : 1
                },
                {
                    "name" : "smoke.SmokeFormatAction",
                    "state" : "COMPLETE",
                    "created" : ISODate("2023-05-10T10:38:56.639Z"),
                    "queued" : ISODate("2023-05-10T10:38:56.639Z"),
                    "start" : ISODate("2023-05-10T10:38:56.823Z"),
                    "stop" : ISODate("2023-05-10T10:38:56.833Z"),
                    "modified" : ISODate("2023-05-10T10:38:56.896Z"),
                    "attempt" : 1
                }
            ],
            "sourceInfo" : {
                "filename" : "smoke-f7bc6fc1-0d56-4c5b-9e58-7b758f522ea2",
                "flow" : "smoke",
                "metadata" : {
                },
                "processingType" : "NORMALIZATION"
            },
            "protocolStack" : [
                {
                    "action" : "IngressAction",
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
                    "metadata" : {
                    }
                },
                {
                    "action" : "smoke.SmokeTransformAction",
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
                    "metadata" : {
                    }
                }
            ],
            "domains" : [
                {
                    "name" : "binary",
                    "mediaType" : "text/plain"
                }
            ],
            "annotations" : {
              "keyA" : "valueA",
              "keyB" : "valueB",
            },
            "annotationKeys" : [ "keyA", "keyB" ],
            "enrichment" : [
                {
                    "name" : "binaryEnrichment",
                    "value" : "binary enrichment value",
                    "mediaType" : "text/plain"
                }
            ],
            "egress" : [
                {
                    "flow" : "smoke"
                }
            ],
            "formattedData" : [
                {
                    "formatAction" : "smoke.SmokeFormatAction",
                    "content" : {
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
                    },
                    "metadata" : {
                    },
                    "egressActions" : [
                        "smoke.SmokeEgressAction"
                    ],
                    "validateActions" : [
                        "smoke.SmokeValidateAction"
                    ]
                }
            ],
            "created" : ISODate("2023-05-10T10:38:56.521Z"),
            "modified" : ISODate("2023-05-10T10:38:57.402Z"),
            "egressed" : true,
            "filtered" : false,
            "version" : NumberLong(7),
            "_class" : "org.deltafi.common.types.DeltaFile"
            "schemaVersion" : 2
        }""";

    private static final String DELTAFILE_JSON_V3 = """
        {
            "_id" : "v3",
            "parentDids" : [ ],
            "childDids" : [ ],
            "requeueCount" : 0,
            "ingressBytes" : NumberLong(36),
            "referencedBytes" : NumberLong(36),
            "totalBytes" : NumberLong(36),
            "stage" : "COMPLETE",
            "actions" : [
                {
                    "name" : "IngressAction",
                    "type" : "INGRESS",
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
                    "deleteMetadataKeys": []
                },
                {
                    "name" : "smoke.SmokeTransformAction",
                    "type" : "TRANSFORM",
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
                    "deleteMetadataKeys": []
                },
                {
                    "name" : "smoke.SmokeFormatAction",
                    "type" : "FORMAT",
                    "state" : "COMPLETE",
                    "created" : ISODate("2023-05-10T10:38:56.639Z"),
                    "queued" : ISODate("2023-05-10T10:38:56.639Z"),
                    "start" : ISODate("2023-05-10T10:38:56.823Z"),
                    "stop" : ISODate("2023-05-10T10:38:56.833Z"),
                    "modified" : ISODate("2023-05-10T10:38:56.896Z"),
                    "attempt" : 1,
                    "content" : [],
                    "metadata": {},
                    "deleteMetadataKeys": []
                }
            ],
            "sourceInfo" : {
                "filename" : "smoke-f7bc6fc1-0d56-4c5b-9e58-7b758f522ea2",
                "flow" : "smoke",
                "metadata" : {},
                "processingType" : "NORMALIZATION"
            },
            "domains" : [
                {
                    "name" : "binary",
                    "mediaType" : "text/plain"
                }
            ],
            "annotations" : {
              "keyA" : "valueA",
              "keyB" : "valueB",
            },
            "annotationKeys" : [ "keyA", "keyB" ],
            "enrichment" : [
                {
                    "name" : "binaryEnrichment",
                    "value" : "binary enrichment value",
                    "mediaType" : "text/plain"
                }
            ],
            "egress" : [
                {
                    "flow" : "smoke"
                }
            ],
            "formattedData" : [
                {
                    "formatAction" : "smoke.SmokeFormatAction",
                    "content" : {
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
                    },
                    "metadata" : {
                    },
                    "egressActions" : [
                        "smoke.SmokeEgressAction"
                    ],
                    "validateActions" : [
                        "smoke.SmokeValidateAction"
                    ]
                }
            ],
            "created" : ISODate("2023-05-10T10:38:56.521Z"),
            "modified" : ISODate("2023-05-10T10:38:57.402Z"),
            "egressed" : true,
            "filtered" : false,
            "version" : NumberLong(7),
            "_class" : "org.deltafi.common.types.DeltaFile"
            "schemaVersion" : 3
        }""";

    private static final String DELTAFILE_JSON_V4 = """
        {
            "_id" : "v4",
            "parentDids" : [ ],
            "childDids" : [ ],
            "requeueCount" : 0,
            "ingressBytes" : NumberLong(36),
            "referencedBytes" : NumberLong(36),
            "totalBytes" : NumberLong(36),
            "stage" : "COMPLETE",
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
                    "deleteMetadataKeys": []
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
                    "deleteMetadataKeys": []
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
                    "deleteMetadataKeys": []
                }
            ],
            "sourceInfo" : {
                "filename" : "smoke-f7bc6fc1-0d56-4c5b-9e58-7b758f522ea2",
                "flow" : "smoke",
                "metadata" : {},
                "processingType" : "NORMALIZATION"
            },
            "domains" : [
                {
                    "name" : "binary",
                    "mediaType" : "text/plain"
                }
            ],
            "annotations" : {
              "keyA" : "valueA",
              "keyB" : "valueB",
            },
            "annotationKeys" : [ "keyA", "keyB" ],
            "enrichment" : [
                {
                    "name" : "binaryEnrichment",
                    "value" : "binary enrichment value",
                    "mediaType" : "text/plain"
                }
            ],
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
            "schemaVersion" : 4
        }""";

    private static final Document DELTAFILE_DOC_V0 = Document.parse(DELTAFILE_JSON_V0);
    private static final Document DELTAFILE_DOC_V1 = Document.parse(DELTAFILE_JSON_V1);
    private static final Document DELTAFILE_DOC_V2 = Document.parse(DELTAFILE_JSON_V2);
    private static final Document DELTAFILE_DOC_V3 = Document.parse(DELTAFILE_JSON_V3);
    private static final Document DELTAFILE_LATEST = Document.parse(DELTAFILE_JSON_V4);

    public static final Map<Integer, Document> deltaFileDocs = Map.of(
            0, DELTAFILE_DOC_V0,
            1, DELTAFILE_DOC_V1,
            2, DELTAFILE_DOC_V2,
            3, DELTAFILE_DOC_V3,
            4, DELTAFILE_LATEST);

    public static void assertConverted(DeltaFileRepo deltaFileRepo, MongoTemplate mongoTemplate, int version) {
        Document document = deltaFileDocs.get(version);
        mongoTemplate.insert(document, "deltaFile");
        DeltaFile actual = deltaFileRepo.findById(document.getString("_id")).orElseThrow();
        assertEquals(version, actual.getSchemaVersion());
        mongoTemplate.insert(DELTAFILE_LATEST, "deltaFile");
        DeltaFile expected = deltaFileRepo.findById(DELTAFILE_LATEST.getString("_id")).orElseThrow();

        actual.setDid(expected.getDid());
        actual.setSchemaVersion(expected.getSchemaVersion());
        assertEquals(expected, actual);
    }

    public static void assertDeleted(DeltaFileRepo deltaFileRepo, MongoTemplate mongoTemplate, int version) {
        mongoTemplate.insert(deltaFileDocs.get(version), "deltaFile");
        List<DeltaFile> deltaFiles = deltaFileRepo.findForDelete(1, null, "policyName", 1);
        assertEquals(1, deltaFiles.size());
        assertEquals(3, deltaFiles.get(0).referencedSegments().size());
    }
}
