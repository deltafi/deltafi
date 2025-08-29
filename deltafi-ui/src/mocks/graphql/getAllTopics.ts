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

const generateData = () => {
  return [
    {
      "name": "blackhole",
      "publishers": [
        {
          "name": "decompress",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        },
        {
          "name": "devFlow1",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        },
        {
          "name": "devFlow2",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        },
        {
          "name": "devFlow3",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        },
        {
          "name": "devFlow4",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        },
        {
          "name": "jolt",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        },
        {
          "name": "json-subscriber",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        },
        {
          "name": "xml-subscriber",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        }
      ],
      "subscribers": [
        {
          "name": "blackhole-data-sink",
          "type": "DATA_SINK",
          "state": "RUNNING",
          "condition": null
        }
      ]
    },
    {
      "name": "decompress-and-join",
      "publishers": [
        {
          "name": "decompress-join-source",
          "type": "REST_DATA_SOURCE",
          "state": "RUNNING",
          "condition": null
        }
      ],
      "subscribers": [
        {
          "name": "decompress-and-join-transform",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        }
      ]
    },
    {
      "name": "devFlow1",
      "publishers": [
        {
          "name": "dev-flow-1-rest-data-source",
          "type": "REST_DATA_SOURCE",
          "state": "RUNNING",
          "condition": null
        }
      ],
      "subscribers": [
        {
          "name": "devFlow1",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        }
      ]
    },
    {
      "name": "devFlow2",
      "publishers": [
        {
          "name": "dev-flow-2-rest-data-source",
          "type": "REST_DATA_SOURCE",
          "state": "RUNNING",
          "condition": null
        }
      ],
      "subscribers": [
        {
          "name": "devFlow2",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        }
      ]
    },
    {
      "name": "devFlow3",
      "publishers": [
        {
          "name": "dev-flow-3-rest-data-source",
          "type": "REST_DATA_SOURCE",
          "state": "RUNNING",
          "condition": null
        }
      ],
      "subscribers": [
        {
          "name": "devFlow3",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        }
      ]
    },
    {
      "name": "devFlow4",
      "publishers": [
        {
          "name": "dev-flow-4-rest-data-source",
          "type": "REST_DATA_SOURCE",
          "state": "RUNNING",
          "condition": null
        }
      ],
      "subscribers": [
        {
          "name": "devFlow4",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        }
      ]
    },
    {
      "name": "generated-data",
      "publishers": [
        {
          "name": "gen-data",
          "type": "TIMED_DATA_SOURCE",
          "state": "STOPPED",
          "condition": null
        }
      ],
      "subscribers": [
        {
          "name": "gen-data-route-all-matching",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        },
        {
          "name": "gen-data-route-or-detect",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        },
        {
          "name": "gen-data-route-or-error",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        },
        {
          "name": "gen-data-route-or-filter",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        }
      ]
    },
    {
      "name": "hello-world-java-data",
      "publishers": [
        {
          "name": "hello-world-java-timed-data-source",
          "type": "TIMED_DATA_SOURCE",
          "state": "RUNNING",
          "condition": null
        }
      ],
      "subscribers": [
        {
          "name": "hello-world-java-data-route-or-filter",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        }
      ]
    },
    {
      "name": "hello-world-java-egress",
      "publishers": [
        {
          "name": "hello-world-java-join",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        },
        {
          "name": "hello-world-java-transform",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        },
        {
          "name": "hello-world-java-transform-many",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        }
      ],
      "subscribers": [
        {
          "name": "hello-world-java-data-sink",
          "type": "DATA_SINK",
          "state": "RUNNING",
          "condition": null
        }
      ]
    },
    {
      "name": "hello-world-java-join",
      "publishers": [
        {
          "name": "hello-world-java-data-route-or-filter",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": "metadata['hello'] == 'join'"
        },
        {
          "name": "hello-world-java-join-rest-data-source",
          "type": "REST_DATA_SOURCE",
          "state": "RUNNING",
          "condition": null
        }
      ],
      "subscribers": [
        {
          "name": "hello-world-java-join",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        }
      ]
    },
    {
      "name": "hello-world-java-transform",
      "publishers": [
        {
          "name": "hello-world-java-data-route-or-filter",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": "metadata['hello'] == 'transform'"
        }
      ],
      "subscribers": [
        {
          "name": "hello-world-java-transform",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        }
      ]
    },
    {
      "name": "hello-world-java-transform-many",
      "publishers": [
        {
          "name": "hello-world-java-transform-many-rest-data-source",
          "type": "REST_DATA_SOURCE",
          "state": "RUNNING",
          "condition": null
        }
      ],
      "subscribers": [
        {
          "name": "hello-world-java-transform-many",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        }
      ]
    },
    {
      "name": "hello-world-python-data",
      "publishers": [
        {
          "name": "hello-world-python-timed-data-source",
          "type": "TIMED_DATA_SOURCE",
          "state": "RUNNING",
          "condition": null
        }
      ],
      "subscribers": [
        {
          "name": "hello-world-python-data-route-or-filter",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        }
      ]
    },
    {
      "name": "hello-world-python-egress",
      "publishers": [
        {
          "name": "hello-world-python-join",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        },
        {
          "name": "hello-world-python-transform",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        },
        {
          "name": "hello-world-python-transform-many",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        }
      ],
      "subscribers": [
        {
          "name": "hello-world-python-data-sink",
          "type": "DATA_SINK",
          "state": "RUNNING",
          "condition": null
        }
      ]
    },
    {
      "name": "hello-world-python-join",
      "publishers": [
        {
          "name": "hello-world-python-data-route-or-filter",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": "metadata['hello'] == 'join'"
        },
        {
          "name": "hello-world-python-join-rest-data-source",
          "type": "REST_DATA_SOURCE",
          "state": "RUNNING",
          "condition": null
        }
      ],
      "subscribers": [
        {
          "name": "hello-world-python-join",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        }
      ]
    },
    {
      "name": "hello-world-python-transform",
      "publishers": [
        {
          "name": "hello-world-python-data-route-or-filter",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": "metadata['hello'] == 'transform'"
        }
      ],
      "subscribers": [
        {
          "name": "hello-world-python-transform",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        }
      ]
    },
    {
      "name": "hello-world-python-transform-many",
      "publishers": [
        {
          "name": "hello-world-python-transform-many-rest-data-source",
          "type": "REST_DATA_SOURCE",
          "state": "RUNNING",
          "condition": null
        }
      ],
      "subscribers": [
        {
          "name": "hello-world-python-transform-many",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        }
      ]
    },
    {
      "name": "json",
      "publishers": [
        {
          "name": "gen-data-route-all-matching",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": "hasMediaType('application/json')"
        },
        {
          "name": "gen-data-route-or-detect",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": "hasMediaType('application/json')"
        },
        {
          "name": "gen-data-route-or-error",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": "hasMediaType('application/json')"
        },
        {
          "name": "gen-data-route-or-filter",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": "hasMediaType('application/json')"
        }
      ],
      "subscribers": [
        {
          "name": "json-subscriber",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        }
      ]
    },
    {
      "name": "just-decompress",
      "publishers": [
        {
          "name": "decompress-rest",
          "type": "REST_DATA_SOURCE",
          "state": "RUNNING",
          "condition": null
        }
      ],
      "subscribers": [
        {
          "name": "decompress",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        }
      ]
    },
    {
      "name": "malware-threat-csv",
      "publishers": [
        {
          "name": "malware-threat-rest-data-source",
          "type": "REST_DATA_SOURCE",
          "state": "RUNNING",
          "condition": null
        }
      ],
      "subscribers": [
        {
          "name": "malware-threat-transform",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        }
      ]
    },
    {
      "name": "needs-a-jolt",
      "publishers": [],
      "subscribers": [
        {
          "name": "jolt",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        }
      ]
    },
    {
      "name": "passthrough",
      "publishers": [
        {
          "name": "gen-data-route-all-matching",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        },
        {
          "name": "passthrough-rest-data-source",
          "type": "REST_DATA_SOURCE",
          "state": "STOPPED",
          "condition": null
        }
      ],
      "subscribers": [
        {
          "name": "passthrough-transform",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        }
      ]
    },
    {
      "name": "passthrough-egress",
      "publishers": [
        {
          "name": "decompress-and-join-transform",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        },
        {
          "name": "passthrough-transform",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        }
      ],
      "subscribers": [
        {
          "name": "passthrough-data-sink",
          "type": "DATA_SINK",
          "state": "RUNNING",
          "condition": null
        }
      ]
    },
    {
      "name": "smoke-egress",
      "publishers": [
        {
          "name": "smoke-test-transform",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        }
      ],
      "subscribers": [
        {
          "name": "smoke-test-data-sink",
          "type": "DATA_SINK",
          "state": "RUNNING",
          "condition": null
        }
      ]
    },
    {
      "name": "smoke-transform",
      "publishers": [
        {
          "name": "smoke-rest-data-source",
          "type": "REST_DATA_SOURCE",
          "state": "STOPPED",
          "condition": null
        },
        {
          "name": "smoke-timed-data-source",
          "type": "TIMED_DATA_SOURCE",
          "state": "RUNNING",
          "condition": null
        }
      ],
      "subscribers": [
        {
          "name": "smoke-test-transform",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        }
      ]
    },
    {
      "name": "stix-1_x",
      "publishers": [
        {
          "name": "stix-1_x-rest-data-source",
          "type": "REST_DATA_SOURCE",
          "state": "STOPPED",
          "condition": null
        }
      ],
      "subscribers": [
        {
          "name": "stix-elevator-transform",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        }
      ]
    },
    {
      "name": "stix-2_1",
      "publishers": [
        {
          "name": "malware-threat-transform",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        },
        {
          "name": "stix-2_1-rest-data-source",
          "type": "REST_DATA_SOURCE",
          "state": "STOPPED",
          "condition": null
        },
        {
          "name": "stix-elevator-transform",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        }
      ],
      "subscribers": [
        {
          "name": "stix-2_1-validate",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        }
      ]
    },
    {
      "name": "stix-2_1-validated",
      "publishers": [
        {
          "name": "stix-2_1-validate",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        }
      ],
      "subscribers": [
        {
          "name": "stix-2_1-data-sink",
          "type": "DATA_SINK",
          "state": "RUNNING",
          "condition": null
        }
      ]
    },
    {
      "name": "unknown-media-type",
      "publishers": [
        {
          "name": "gen-data-route-or-detect",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        }
      ],
      "subscribers": []
    },
    {
      "name": "xml",
      "publishers": [
        {
          "name": "gen-data-route-or-detect",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": "hasMediaType('application/xml')"
        },
        {
          "name": "gen-data-route-or-error",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": "hasMediaType('application/xml')"
        },
        {
          "name": "gen-data-route-or-filter",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": "hasMediaType('application/xml')"
        }
      ],
      "subscribers": [
        {
          "name": "xml-subscriber",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        }
      ]
    },
    {
      "name": "decompress-rest",
      "publishers": [
        {
          "name": "decompress-rest",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        }
      ],
      "subscribers": []
    },
    {
      "name": "detect-media-type",
      "publishers": [
        {
          "name": "gen-data-route-or-detect",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        }
      ],
      "subscribers": []
    },
    // modify-metadata
    {
      "name": "modify-metadata",
      "publishers": [
        {
          "name": "modify-metadata",
          "type": "TRANSFORM",
          "state": "RUNNING",
          "condition": null
        }
      ],
      "subscribers": []
    }
  ];
};

export default {
  getAllTopics: generateData(),
};
