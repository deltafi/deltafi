/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>

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
  const data = {
    plugins: [
      {
        pluginCoordinates: {
          groupId: "org.deltafi",
          artifactId: "system-plugin",
          version: "2.0-beta6"
        },
        actions: [],
        actionKitVersion: "2.0-beta6"
      },
      {
        pluginCoordinates: {
          groupId: "org.deltafi",
          artifactId: "deltafi-core-actions",
          version: "2.0-beta6"
        },
        actions: [
          {
            name: "org.deltafi.core.action.split.Split",
            type: "TRANSFORM",
            description: "Splits a DeltaFile with multiple pieces of content into multiple DeltaFiles",
            schema: {
              type: "object",
              additionalProperties: false
            }
          },
          {
            name: "org.deltafi.core.action.compress.Compress",
            type: "TRANSFORM",
            description: "Compresses each supplied content to .gz, or .xz",
            schema: {
              type: "object",
              properties: {
                compressType: {
                  type: "string",
                  enum: [
                    "gz",
                    "xz",
                    "z"
                  ],
                  description: "Compress type: gz or xz"
                },
                mediaType: {
                  type: "string",
                  description: "Sets the media type of the new content to the specified value. Otherwise, will be based on compressType"
                }
              },
              required: [
                "compressType"
              ],
              additionalProperties: false
            }
          },
          {
            name: "org.deltafi.core.action.compress.Decompress",
            type: "TRANSFORM",
            description: "Decompresses each supplied content from .gz, .xz, or .Z",
            schema: {
              type: "object",
              properties: {
                compressType: {
                  type: "string",
                  enum: [
                    "gz",
                    "xz",
                    "z"
                  ],
                  description: "Compress type: gzip, xz, Z"
                }
              },
              additionalProperties: false
            }
          },
          {
            name: "org.deltafi.core.action.mediatype.ModifyMediaType",
            type: "TRANSFORM",
            description: "Modify content mediaTypes based on pattern or content index",
            schema: {
              type: "object",
              properties: {
                errorOnMissingIndex: {
                  type: "boolean",
                  description: "If true, throw an exception if a content is missing an index specified in indexMediaTypeMap",
                  default: false
                },
                indexMediaTypeMap: {
                  type: "object",
                  additionalProperties: {
                    type: "string"
                  },
                  description: "A map of indexes to media types. Used to update the media type of specific content by index."
                },
                mediaTypeMap: {
                  type: "object",
                  additionalProperties: {
                    type: "string"
                  },
                  description: "A map of old to new media types. Supports wildcards (*) in the old media types. These will be applied before and overridden by the indexMediaTypeMap values, if present."
                }
              },
              additionalProperties: false
            }
          },
          {
            name: "org.deltafi.core.action.mediatype.DetectMediaType",
            type: "TRANSFORM",
            description: "Detect and set mediaType for each content, using Tika. In the case of detection errors, the existing mediaType is retained.",
            schema: {
              type: "object",
              additionalProperties: false
            }
          },
          {
            name: "org.deltafi.core.action.archive.Archive",
            type: "TRANSFORM",
            description: "Archives content to .ar, .tar, .tar.gz, .tar.xz, or .zip",
            schema: {
              type: "object",
              properties: {
                appendSuffix: {
                  type: "boolean",
                  description: "Append the archiveType suffix to new content name(s)"
                },
                archiveType: {
                  type: "string",
                  enum: [
                    "ar",
                    "tar",
                    "tar.gz",
                    "tar.xz",
                    "tar.Z",
                    "zip"
                  ],
                  description: "Archive type: ar, tar, tar.gz, tar.xz, or zip"
                },
                mediaType: {
                  type: "string",
                  description: "Sets the media type of the new content to the specified value. Otherwise, will be based on archiveType"
                }
              },
              required: [
                "archiveType"
              ],
              additionalProperties: false
            }
          },
          {
            name: "org.deltafi.core.action.archive.Unarchive",
            type: "TRANSFORM",
            description: "Unarchives .ar, .tar, .tar.gz, .tar.xz, .tar.Z, or .zip",
            schema: {
              type: "object",
              properties: {
                archiveType: {
                  type: "string",
                  enum: [
                    "ar",
                    "tar",
                    "tar.gz",
                    "tar.xz",
                    "tar.Z",
                    "zip"
                  ],
                  description: "Archive type: ar, tar, tar.gz, tar.xz, tar.Z, or zip"
                }
              },
              additionalProperties: false
            }
          },
          {
            name: "org.deltafi.core.action.merge.Merge",
            type: "TRANSFORM",
            description: "Merge multiple pieces of content into one blob",
            schema: {
              type: "object",
              properties: {
                mediaType: {
                  type: "string",
                  description: "Optional mediaType to apply to the merged file. If blank, will use the mediaType of the first file merged"
                },
                mergedFilename: {
                  type: "string",
                  description: "Name of the merged file. Use of {{filename}} will be dynamically replaced with the filename of the first file merged. If blank, filename of the first file merged is used"
                }
              },
              additionalProperties: false
            }
          },
          {
            name: "org.deltafi.core.action.egress.RestPostEgress",
            type: "EGRESS",
            description: "Egresses to a REST endpoint",
            schema: {
              type: "object",
              properties: {
                metadataKey: {
                  type: "string",
                  description: "Send metadata as JSON in this HTTP header field"
                },
                retryCount: {
                  type: "integer",
                  description: "Number of times to retry a failing HTTP request",
                  default: 3
                },
                retryDelayMs: {
                  type: "integer",
                  description: "Number milliseconds to wait for an HTTP retry",
                  default: 150
                },
                url: {
                  type: "string",
                  description: "The URL to post the DeltaFile to"
                }
              },
              required: [
                "metadataKey",
                "url"
              ],
              additionalProperties: false
            }
          },
          {
            name: "org.deltafi.core.action.egress.FilterEgress",
            type: "EGRESS",
            description: "Filters on egress",
            schema: {
              type: "object",
              additionalProperties: false
            }
          },
          {
            name: "org.deltafi.core.action.egress.FlowfileEgress",
            type: "EGRESS",
            description: "Egresses content and attributes in a NiFi V1 FlowFile (application/flowfile)",
            schema: {
              type: "object",
              properties: {
                retryCount: {
                  type: "integer",
                  description: "Number of times to retry a failing HTTP request",
                  default: 3
                },
                retryDelayMs: {
                  type: "integer",
                  description: "Number milliseconds to wait for an HTTP retry",
                  default: 150
                },
                url: {
                  type: "string",
                  description: "The URL to post the DeltaFile to"
                }
              },
              required: [
                "url"
              ],
              additionalProperties: false
            }
          },
          {
            name: "org.deltafi.core.action.egress.DeltaFiEgress",
            type: "EGRESS",
            description: "Egresses to another DeltaFi",
            schema: {
              type: "object",
              properties: {
                flow: {
                  type: "string",
                  description: "Name of the flow on the receiving DeltaFi"
                },
                retryCount: {
                  type: "integer",
                  description: "Number of times to retry a failing HTTP request",
                  default: 3
                },
                retryDelayMs: {
                  type: "integer",
                  description: "Number milliseconds to wait for an HTTP retry",
                  default: 150
                },
                url: {
                  type: "string",
                  description: "The URL to post the DeltaFile to"
                }
              },
              required: [
                "url"
              ],
              additionalProperties: false
            }
          },
          {
            name: "org.deltafi.core.action.ingress.SmokeTestIngress",
            type: "TIMED_INGRESS",
            description: "Create smoke test DeltaFiles",
            schema: {
              type: "object",
              properties: {
                content: {
                  type: "string",
                  description: "The content to attach to the DeltaFile. If null, random data of size contentSize will be added to the deltaFile"
                },
                contentSize: {
                  type: "integer",
                  description: "The size in bytes of the random content to attach to the DeltaFile. Ignored if content is set",
                  default: 500
                },
                mediaType: {
                  type: "string",
                  description: "The content's mediaType. If null, the default is application/text.",
                  default: "application/text"
                },
                metadata: {
                  type: "object",
                  additionalProperties: {
                    type: "string"
                  },
                  description: "Metadata to add to each smoke-generated DeltaFile"
                }
              },
              additionalProperties: false
            }
          },
          {
            name: "org.deltafi.core.action.ingress.SftpIngress",
            type: "TIMED_INGRESS",
            description: "Poll an SFTP server for files to ingress",
            schema: {
              type: "object",
              properties: {
                directory: {
                  type: "string",
                  description: "The directory to poll"
                },
                fileRegex: {
                  type: "string",
                  description: "A regular expression that files must match to be ingressed"
                },
                host: {
                  type: "string",
                  description: "The SFTP server host name"
                },
                password: {
                  type: "string",
                  description: "The password. If not set, will use the private key from a configured keystore."
                },
                port: {
                  type: "integer",
                  description: "The SFTP server port"
                },
                username: {
                  type: "string",
                  description: "The user name"
                }
              },
              required: [
                "directory",
                "fileRegex",
                "host",
                "port",
                "username"
              ],
              additionalProperties: false
            }
          },
          {
            name: "org.deltafi.core.action.ingress.RestIngress",
            type: "TIMED_INGRESS",
            description: "Poll a REST server for a file to ingress",
            schema: {
              type: "object",
              properties: {
                headers: {
                  type: "object",
                  additionalProperties: {
                    type: "string"
                  },
                  description: "The headers to include in the request"
                },
                url: {
                  type: "string",
                  description: "The REST url to poll"
                }
              },
              required: [
                "url"
              ],
              additionalProperties: false
            }
          },
          {
            name: "org.deltafi.core.action.jolt.JoltTransform",
            type: "TRANSFORM",
            description: "Apply Jolt transformation to JSON content",
            schema: {
              type: "object",
              properties: {
                contentIndexes: {
                  description: "List of content indexes to consider. If empty, all content is considered.",
                  type: "array",
                  items: {
                    type: "integer"
                  }
                },
                filePatterns: {
                  description: "List of file patterns to consider. Supports wildcards (*) and if empty, all filenames are considered.",
                  type: "array",
                  items: {
                    type: "string"
                  }
                },
                joltSpec: {
                  type: "string",
                  description: "Jolt transformation specification provided as a JSON string."
                },
                mediaTypes: {
                  description: "List of allowed media types. Supports wildcards (*) and defaults to 'application/json' if empty.",
                  type: "array",
                  items: {
                    type: "string"
                  }
                }
              },
              required: [
                "joltSpec"
              ],
              additionalProperties: false
            }
          },
          {
            name: "org.deltafi.core.action.extract.ExtractXml",
            type: "TRANSFORM",
            description: "Extract XML keys based on XPath and write them to metadata or annotations",
            schema: {
              type: "object",
              properties: {
                allKeysDelimiter: {
                  type: "string",
                  description: "The delimiter to use if handleMultipleKeys is set to DISTINCT or ALL",
                  default: ","
                },
                contentIndexes: {
                  description: "List of content indexes to consider. If empty, all content is considered.",
                  type: "array",
                  items: {
                    type: "integer"
                  }
                },
                errorOnKeyNotFound: {
                  type: "boolean",
                  description: "Whether to return an error if a key is not found. Defaults to false.",
                  default: false
                },
                extractTarget: {
                  type: "string",
                  enum: [
                    "METADATA",
                    "ANNOTATIONS"
                  ],
                  description: "Extract to metadata or annotations.",
                  default: "METADATA"
                },
                filePatterns: {
                  description: "List of file patterns to consider. Supports wildcards (*) and if empty, all filenames are considered.",
                  type: "array",
                  items: {
                    type: "string"
                  }
                },
                handleMultipleKeys: {
                  type: "string",
                  enum: [
                    "FIRST",
                    "LAST",
                    "DISTINCT",
                    "ALL"
                  ],
                  description: "How to handle multiple occurrences of a key. Can be 'FIRST', 'LAST', 'DISTINCT', or 'ALL'. Defaults to ALL, which writes a delimited list.",
                  default: "ALL"
                },
                mediaTypes: {
                  description: "List of allowed media types. Supports wildcards (*) and defaults to application/xml if empty.",
                  default: [
                    "application/xml"
                  ],
                  type: "array",
                  items: {
                    type: "string",
                    default: [
                      "application/xml"
                    ]
                  }
                },
                xpathToKeysMap: {
                  type: "object",
                  additionalProperties: {
                    type: "string"
                  },
                  description: "A map of XPath expressions to keys. Values will be extracted using XPath and added to the corresponding metadata or annotation keys."
                }
              },
              additionalProperties: false
            }
          },
          {
            name: "org.deltafi.core.action.extract.ExtractJson",
            type: "TRANSFORM",
            description: "Extract JSON keys based on JSONPath and write them to metadata or annotations",
            schema: {
              type: "object",
              properties: {
                allKeysDelimiter: {
                  type: "string",
                  description: "The delimiter to use if handleMultipleKeys is set to DISTINCT or ALL",
                  default: ","
                },
                contentIndexes: {
                  description: "List of content indexes to consider. If empty, all content is considered.",
                  type: "array",
                  items: {
                    type: "integer"
                  }
                },
                errorOnKeyNotFound: {
                  type: "boolean",
                  description: "Whether to return an error if a key is not found. Defaults to false.",
                  default: false
                },
                extractTarget: {
                  type: "string",
                  enum: [
                    "METADATA",
                    "ANNOTATIONS"
                  ],
                  description: "Extract to metadata or annotations.",
                  default: "METADATA"
                },
                filePatterns: {
                  description: "List of file patterns to consider. Supports wildcards (*) and if empty, all filenames are considered.",
                  type: "array",
                  items: {
                    type: "string"
                  }
                },
                handleMultipleKeys: {
                  type: "string",
                  enum: [
                    "FIRST",
                    "LAST",
                    "DISTINCT",
                    "ALL"
                  ],
                  description: "How to handle multiple occurrences of a key. Can be 'FIRST', 'LAST', 'DISTINCT', or 'ALL'. Defaults to ALL, which writes a delimited list."
                },
                jsonPathToKeysMap: {
                  type: "object",
                  additionalProperties: {
                    type: "string"
                  },
                  description: "A map of JSONPath expressions to keys. Values will be extracted using JSONPath and added to the corresponding metadata or annotation keys."
                },
                mediaTypes: {
                  description: "List of allowed media types. Supports wildcards (*) and defaults to application/json if empty.",
                  type: "array",
                  items: {
                    type: "string"
                  }
                }
              },
              additionalProperties: false
            }
          },
          {
            name: "org.deltafi.core.action.xslt.XsltTransform",
            type: "TRANSFORM",
            description: "Apply XML transformation using XSLT",
            schema: {
              type: "object",
              properties: {
                contentIndexes: {
                  description: "List of content indexes to consider. If empty, all content is considered.",
                  type: "array",
                  items: {
                    type: "integer"
                  }
                },
                filePatterns: {
                  description: "List of file patterns to consider. Supports wildcards (*) and if empty, all filenames are considered.",
                  type: "array",
                  items: {
                    type: "string"
                  }
                },
                mediaTypes: {
                  description: "List of allowed media types. Supports wildcards (*) and defaults to 'application/xml' if empty.",
                  type: "array",
                  items: {
                    type: "string"
                  }
                },
                xslt: {
                  type: "string",
                  description: "XSLT transformation specification provided as a string."
                }
              },
              required: [
                "xslt"
              ],
              additionalProperties: false
            }
          },
          {
            name: "org.deltafi.core.action.filter.FilterByCriteria",
            type: "TRANSFORM",
            description: "The FilterByCriteria action allows you to filter or pass DeltaFiles based on specific criteria defined using Spring Expression Language (SpEL). The action takes a list of SpEL expressions that are evaluated against the metadata and content. Depending on the configured filter behavior, the action filters if 'ANY', 'ALL', or 'NONE' of the expressions match.\nExamples:\n- To filter if metadata key 'x' is set to 'y': \"metadata['x'] == 'y'\"\n- To filter if 'x' is not 'y' or if 'x' is not present: \"metadata['x'] != 'y' || !metadata.containsKey('x')\"\n- To filter if no content is JSON: \"!content.stream().anyMatch(c -> c.getMediaType.equals('application/json'))",
            schema: {
              type: "object",
              properties: {
                filterBehavior: {
                  type: "string",
                  enum: [
                    "ALL",
                    "ANY",
                    "NONE"
                  ],
                  description: "Specifies the filter behavior. 'ANY' will filter if any expression matches. 'ALL' will filter if all expressions match. 'NONE' will filter if no expression matches. Defaults to ANY.",
                  default: "ANY"
                },
                filterExpressions: {
                  description: "A list of SpEL expressions used to filter the content and metadata.",
                  type: "array",
                  items: {
                    type: "string"
                  }
                }
              },
              required: [
                "filterExpressions"
              ],
              additionalProperties: false
            }
          },
          {
            name: "org.deltafi.core.action.filter.Filter",
            type: "TRANSFORM",
            description: "Action that always filters",
            schema: {
              type: "object",
              additionalProperties: false
            }
          },
          {
            name: "org.deltafi.core.action.delay.Delay",
            type: "TRANSFORM",
            description: "Introduce a set or random delay to a flow",
            schema: {
              type: "object",
              properties: {
                maxDelayMS: {
                  type: "integer",
                  description: "Maximum time to delay processing in ms",
                  default: 0
                },
                minDelayMS: {
                  type: "integer",
                  description: "Minimum time to delay processing in ms",
                  default: 0
                }
              },
              additionalProperties: false
            }
          },
          {
            name: "org.deltafi.core.action.error.Error",
            type: "TRANSFORM",
            description: "Action that errors, optionally when a configurable metadata key is present",
            schema: {
              type: "object",
              properties: {
                message: {
                  type: "string",
                  description: "The error message",
                  default: "Errored by fiat"
                },
                metadataTrigger: {
                  type: "string",
                  description: "If set, will only trigger an error when the metadata key is present. The error message will be the metadata value."
                }
              },
              additionalProperties: false
            }
          },
          {
            name: "org.deltafi.core.action.metadata.ModifyMetadata",
            type: "TRANSFORM",
            description: "Add, modify, copy, and remove metadata",
            schema: {
              type: "object",
              properties: {
                addOrModifyMetadata: {
                  type: "object",
                  additionalProperties: {
                    type: "string"
                  },
                  description: "Key value pairs of metadata to be added or modified"
                },
                copyMetadata: {
                  type: "object",
                  additionalProperties: {
                    type: "string"
                  },
                  description: "Copy existing metadata values. Expressed as a map of old key names to new key names. The new names can be a comma-separated list, to make multiple copies. If the original key does not exist, this will be ignored. Copies will overwrite existing values. Copies are always performed before deletes."
                },
                deleteMetadataKeys: {
                  description: "List of metadata keys to delete",
                  type: "array",
                  items: {
                    type: "string"
                  }
                }
              },
              additionalProperties: false
            }
          },
          {
            name: "org.deltafi.core.action.metadata.MetadataToAnnotation",
            type: "TRANSFORM",
            description: "Saves metadata as annotations",
            schema: {
              type: "object",
              properties: {
                discardPrefix: {
                  type: "string",
                  description: "Remove the prefix from each metadata key before adding annotation"
                },
                metadataPatterns: {
                  description: "List of regex patterns to filter the metadata to include. If empty, all metadata is included.",
                  type: "array",
                  items: {
                    type: "string"
                  }
                }
              },
              additionalProperties: false
            }
          },
          {
            name: "org.deltafi.core.action.metadata.MetadataToContent",
            type: "TRANSFORM",
            description: "Convert metadata to JSON content",
            schema: {
              type: "object",
              properties: {
                filename: {
                  type: "string",
                  description: "Filename for the new content containing the metadata.",
                  default: "metadata.json"
                },
                metadataPatterns: {
                  description: "List of regex patterns to filter the metadata to include. If empty, all metadata is included.",
                  type: "array",
                  items: {
                    type: "string"
                  }
                },
                replaceExistingContent: {
                  type: "boolean",
                  description: "Boolean indicating whether the existing content should remain or be replaced by the new content.",
                  default: false
                }
              },
              additionalProperties: false
            }
          },
          {
            name: "org.deltafi.core.action.convert.Convert",
            type: "TRANSFORM",
            description: "Converts content between different formats. JSON, XML, and CSV are currently supported. Provides a best effort conversion as there is not a reliable canonical way to convert between these formats.",
            schema: {
              type: "object",
              properties: {
                contentIndexes: {
                  description: "List of content indexes to consider. If empty, all content is considered.",
                  type: "array",
                  items: {
                    type: "integer"
                  }
                },
                csvWriteHeader: {
                  type: "boolean",
                  description: "Whether to write a header row when converting to CSV. Defaults to true.",
                  default: true
                },
                filePatterns: {
                  description: "List of file patterns to consider. Supports wildcards (*) and if empty, all filenames are considered.",
                  type: "array",
                  items: {
                    type: "string"
                  }
                },
                inputFormat: {
                  type: "string",
                  enum: [
                    "JSON",
                    "XML",
                    "CSV"
                  ],
                  description: "Format of the input content. Supported formats are JSON, XML, and CSV."
                },
                mediaTypes: {
                  description: "List of allowed media types. Supports wildcards (*) and defaults based on the input format if empty.",
                  type: "array",
                  items: {
                    type: "string"
                  }
                },
                outputFormat: {
                  type: "string",
                  enum: [
                    "JSON",
                    "XML",
                    "CSV"
                  ],
                  description: "Format of the output content. Supported formats are JSON, XML, and CSV."
                },
                retainExistingContent: {
                  type: "boolean",
                  description: "Boolean indicating whether the existing content should be retained or replaced by the new content. Default is false.",
                  default: false
                },
                xmlListEntryTag: {
                  type: "string",
                  description: "Name of the XML tag to use for list entries when converting to XML. Defaults to listEntry.",
                  default: "listEntry"
                },
                xmlRootTag: {
                  type: "string",
                  description: "Name of the root XML tag to use when converting to XML. Defaults to xml.",
                  default: "xml"
                }
              },
              additionalProperties: false
            }
          }
        ],
        actionKitVersion: "2.0-beta6"
      },
      {
        pluginCoordinates: {
          groupId: "org.deltafi.helloworld",
          artifactId: "deltafi-java-hello-world",
          version: "2.0-beta6"
        },
        actions: [
          {
            name: "org.deltafi.helloworld.actions.HelloWorldTransformAction",
            type: "TRANSFORM",
            description: "Add some content noting that we did a really good job",
            schema: {
              type: "object",
              properties: {
                convertToUpper: {
                  type: "boolean"
                }
              },
              additionalProperties: false
            }
          },
          {
            name: "org.deltafi.helloworld.actions.HelloWorldTransformManyAction",
            type: "TRANSFORM",
            description: "Transform N children based on the numberOfChildren parameter",
            schema: {
              type: "object",
              properties: {
                numberOfChildren: {
                  type: "integer"
                }
              },
              additionalProperties: false
            }
          },
          {
            name: "org.deltafi.helloworld.actions.HelloWorldJoiningTransformAction",
            type: "TRANSFORM",
            description: "Transforms a list of content in reverse order into a new, single content",
            schema: {
              type: "object",
              properties: {
                indexThreshold: {
                  type: "integer"
                }
              },
              additionalProperties: false
            }
          },
          {
            name: "org.deltafi.helloworld.actions.HelloWorldEgressAction",
            type: "EGRESS",
            description: "Hello pretends to egress",
            schema: {
              type: "object",
              additionalProperties: false
            }
          },
          {
            name: "org.deltafi.helloworld.actions.HelloWorldTimedIngressAction",
            type: "TIMED_INGRESS",
            description: "Create some DeltaFiles for hello-world consumption",
            schema: {
              type: "object",
              additionalProperties: false
            }
          }
        ],
        actionKitVersion: "2.0-beta6"
      },
      {
        pluginCoordinates: {
          groupId: "org.deltafi.testjig",
          artifactId: "deltafi-testjig",
          version: "2.0-beta6"
        },
        actions: [
          {
            name: "org.deltafi.testjig.action.GenerateDataIngressAction",
            type: "TIMED_INGRESS",
            description: "Create DeltaFiles randomly picking the data type from csv, json, yaml, or xml",
            schema: {
              type: "object",
              additionalProperties: false
            }
          }
        ],
        actionKitVersion: "2.0-beta6"
      },
      {
        pluginCoordinates: {
          groupId: "org.deltafi.stix",
          artifactId: "deltafi-stix",
          version: "2.0b7.dev0+g4f25d95.d20240524"
        },
        actions: [
          {
            name: "org.deltafi.stix.MalwareThreatToStixTransformAction",
            type: "TRANSFORM",
            description: "Convert a custom CSV with malware threat info into a STIX bundle using MITRE ATT&CK knowledge base queries",
            schema: {
              properties: {
                parseContent: {
                  description: "Parse content for input",
                  title: "Parsecontent",
                  type: "boolean"
                },
                preserve: {
                  description: "Preserve custom elements",
                  title: "Preserve",
                  type: "boolean"
                }
              },
              required: [
                "parseContent",
                "preserve"
              ],
              title: "MalwareThreatToStixTransformParameters",
              type: "object"
            }
          },
          {
            name: "org.deltafi.stix.StixBundleEditorAction",
            type: "TRANSFORM",
            description: "Edit STIX Domain Objects within a STIX bundle. Remove parameters are processed first,followed by checks, then add, and finally reference updates.",
            schema: {
              properties: {
                allowed_marking_definitions: {
                  default: [],
                  description: "Verify all marking definitions in the STIX bundle match one of these ids. Default is '[]'.",
                  items: {
                    type: "string"
                  },
                  title: "Allowed Marking Definitions",
                  type: "array"
                },
                filter_on_invalid: {
                  default: false,
                  description: "If an invalid marking definition is found, filter instead of error. Default is 'False'.",
                  title: "Filter On Invalid",
                  type: "boolean"
                },
                add_object: {
                  default: null,
                  description: "JSON string of a STIX Domain Object to add into the STIX bundle. Note: To fix any cross references, set the 'reference_update_*' parameters. Default is None.",
                  title: "Add Object",
                  type: "string"
                },
                remove_id: {
                  default: null,
                  description: "Remove a single object from the STIX bundle which matches the 'id' attribute. Note: This will not alone fix cross references. Default is None.",
                  title: "Remove Id",
                  type: "string"
                },
                remove_types: {
                  default: [],
                  description: "Remove all occurrences of objects matching these type(s) from the STIX bundle. Note: Does not fix cross references.Default is '[]'.",
                  items: {
                    type: "string"
                  },
                  title: "Remove Types",
                  type: "array"
                },
                reference_update_type: {
                  default: null,
                  description: "When doing a reference update, the from/to object types must match this value. 'reference_update_type', 'reference_update_from', and 'reference_update_to' are all required when requesting a reference update. Default is None.",
                  title: "Reference Update Type",
                  type: "string"
                },
                reference_update_from: {
                  default: null,
                  description: "An object id from the original bundle. When both 'reference_update_from' and 'reference_update_to' have been set, the elements in any SDO referencing to 'reference_update_from' will be replaced with 'reference_update_to' provided both refer to the same SDO type and match 'reference_update_type'. 'reference_update_type', 'reference_update_from', and 'reference_update_to' are all required when requesting a reference update. Default is None.",
                  title: "Reference Update From",
                  type: "string"
                },
                reference_update_to: {
                  default: null,
                  description: "An object id from the modified bundle. When both 'reference_update_from' and 'reference_update_to' have been set, the elements in any SDO referencing to 'reference_update_from' will be replaced with 'reference_update_to' provided both refer to the same SDO type and match 'reference_update_type'. 'reference_update_type', 'reference_update_from', and 'reference_update_to' are all required when requesting a reference update. Default is None.",
                  title: "Reference Update To",
                  type: "string"
                }
              },
              title: "StixBundleEditorParameters",
              type: "object"
            }
          },
          {
            name: "org.deltafi.stix.StixElevatorTransformAction",
            type: "TRANSFORM",
            description: "Elevate STIX 1.x (XML) into STIX 2.1 (JSON)",
            schema: {
              properties: {},
              title: "GenericModel",
              type: "object"
            }
          },
          {
            name: "org.deltafi.stix.StixValidateTransformAction",
            type: "TRANSFORM",
            description: "Validate STIX content using OASIS TC CTI STIX 2.1 Validator",
            schema: {
              properties: {
                enabled: {
                  default: true,
                  description: "Enables validation when 'true', including both failing invalid data as errors and logging warning messages, provided that 'warn_only' is 'false'.  If 'warn_only' is 'true', then validation failures will be logged but content will not be failed.  Defaults to 'true'.",
                  title: "Enabled",
                  type: "boolean"
                },
                warn_only: {
                  default: false,
                  description: "When 'true' and 'enabled' is 'true', causes validation failures to be logged instead of failing the content.  Has no affect when when 'enabled' is 'false'.  Defaults to 'false'.",
                  title: "Warn Only",
                  type: "boolean"
                },
                strict: {
                  default: false,
                  description: "Perform strict validation when 'true' to treat warnings as errors and fail validation if any are found else 'false'.  Defaults to 'false'.",
                  title: "Strict",
                  type: "boolean"
                },
                version: {
                  default: "2.1",
                  description: "Perform validation against a specific STIX version.  Defaults to '2.1'.",
                  title: "Version",
                  type: "string"
                }
              },
              title: "StixValidateTransformParameters",
              type: "object"
            }
          }
        ],
        actionKitVersion: "2.0rc1716585255870"
      },
      {
        pluginCoordinates: {
          groupId: "org.deltafi.python-hello-world",
          artifactId: "deltafi-python-hello-world",
          version: "2.0b7.dev0+gfa7ef50.d20240524"
        },
        actions: [
          {
            name: "org.deltafi.python-hello-world.HelloWorldJoiningTransformAction",
            type: "TRANSFORM",
            description: "transform N children based on the number_of_children parameter",
            schema: {
              properties: {
                index_threshold: {
                  description: "Sum of index values before inserting the 'indexThresholdMet' annotation",
                  title: "Index Threshold",
                  type: "integer"
                }
              },
              required: [
                "index_threshold"
              ],
              title: "HelloWorldJoiningTransformParameters",
              type: "object"
            }
          },
          {
            name: "org.deltafi.python-hello-world.HelloWorldEgressAction",
            type: "EGRESS",
            description: "Hello pretends to egress",
            schema: {
              properties: {},
              title: "GenericModel",
              type: "object"
            }
          },
          {
            name: "org.deltafi.python-hello-world.HelloWorldTimedIngressAction",
            type: "TIMED_INGRESS",
            description: "Create some DeltaFiles for hello-world consumption",
            schema: {
              properties: {},
              title: "GenericModel",
              type: "object"
            }
          },
          {
            name: "org.deltafi.python-hello-world.HelloWorldTransformAction",
            type: "TRANSFORM",
            description: "Add some content noting that we did a really good job",
            schema: {
              properties: {
                convert_to_upper: {
                  default: false,
                  description: "If TRUE, converts content to upper-case",
                  title: "Convert To Upper",
                  type: "boolean"
                }
              },
              title: "HelloWorldTransformParameters",
              type: "object"
            }
          },
          {
            name: "org.deltafi.python-hello-world.HelloWorldTransformManyAction",
            type: "TRANSFORM",
            description: "transform N children based on the number_of_children parameter",
            schema: {
              properties: {
                number_of_children: {
                  description: "Number of children transform results to create",
                  title: "Number Of Children",
                  type: "integer"
                }
              },
              required: [
                "number_of_children"
              ],
              title: "HelloWorldTransformManyParameters",
              type: "object"
            }
          }
        ],
        actionKitVersion: "2.0rc1716585255870"
      }
    ]
  }

  return data;
};

export default generateData();
