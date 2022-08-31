/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>

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
  return {
    id: "bundle--e33ffe07-2f4c-48d8-b0af-ee2619d765cf",
    objects: [
      {
        dst_port: 80,
        dst_ref: "ipv4-addr--96d215a5-eb23-527a-ae22-9507313d32ca",
        extensions: {
          "http-request-ext": {
            message_body_data_ref: "artifact--b59d53f1-00da-508c-be83-638974fd12eb",
            message_body_length: 200,
            request_header: {
              Accept: "text/plain",
              "Accept-Charset": "utf-8",
              "Accept-Datetime": "Thu, 31 May 2007 20:35:00 GMT",
              "Accept-Encoding": "gzip, deflate",
              Authorization: "Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==",
              "Cache-Control": "no-cache",
              Connection: "close",
              Cookie: "PHPSESSID=r2t5uvjq435r4q7ib3vtdjq120",
              "User-Agent": "Mozilla/5.0 (Windows; U; Windows NT 5.1; en-US; rv:1.6) Gecko/20040113",
            },
            request_method: "get",
            request_value: "/download.html",
            request_version: "http/1.1",
          },
        },
        id: "network-traffic--c24a5769-0072-591d-8712-83661431fc12",
        protocols: ["ipv4", "udp", "dccp", "tcp", "https"],
        src_port: 5525,
        src_ref: "domain-name--bedb4899-d24b-5401-bc86-8f6b4cc18ec7",
        start: "2011-08-05T07:14:55.000000Z",
        type: "network-traffic",
      },
      {
        id: "domain-name--bedb4899-d24b-5401-bc86-8f6b4cc18ec7",
        type: "domain-name",
        value: "example.com",
      },
      {
        id: "ipv4-addr--96d215a5-eb23-527a-ae22-9507313d32ca",
        type: "ipv4-addr",
        value: "198.49.123.10",
      },
      {
        id: "artifact--b59d53f1-00da-508c-be83-638974fd12eb",
        payload_bin: "YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFh",
        type: "artifact",
      },
      {
        created: "2019-08-21T13:10:48.439Z",
        first_observed: "2019-08-21T13:10:48.439Z",
        id: "observed-data--1aec7752-29e1-4018-806c-7a9a21ddb20e",
        last_observed: "2019-08-21T13:10:48.439Z",
        modified: "2019-08-21T13:10:48.439Z",
        number_observed: 1,
        object_refs: ["network-traffic--c24a5769-0072-591d-8712-83661431fc12", "domain-name--bedb4899-d24b-5401-bc86-8f6b4cc18ec7", "ipv4-addr--96d215a5-eb23-527a-ae22-9507313d32ca", "artifact--b59d53f1-00da-508c-be83-638974fd12eb"],
        spec_version: "2.1",
        type: "observed-data",
      },
    ],
    type: "bundle",
  };
};

export default generateData();
