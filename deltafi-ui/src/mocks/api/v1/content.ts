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

const mockedData: Record<string, string> = {
  'application/json': '{"type":"bundle","id":"bundle--44af6c39-c09b-49c5-9de2-394224b04982","objects":[{"type":"indicator","spec_version":"2.1","pattern_type":"stix","id":"indicator--33fe3b22-0201-47cf-85d0-97c02164528d","created":"2014-05-08T09:00:00.000Z","modified":"2014-05-08T09:00:00.000Z","name":"IP Address for known C2 channel","description":"Test description C2 channel.","indicator_types":["malicious-activity"],"pattern":"[ipv4-addr:value = \'10.0.0.0\']","valid_from":"2014-05-08T09:00:00.000000Z"}]}',
  'application/xml': '<?xml version="1.0" encoding="UTF-8"?><stix:STIX_Package timestamp="2014-05-08T09:00:00.000000Z" version="1.2"><stix:Exploit_Targets><stixCommon:Exploit_Target xsi:type="et:ExploitTargetType" id="example:et-48a276f7-a8d7-bba2-3575-e8a63fcd488" timestamp="2014-05-08T09:00:00.000000Z"><et:Title>Javascript vulnerability in MSIE 6-11</et:Title><et:Vulnerability><et:CVE_ID>CVE-2013-3893</et:CVE_ID><et:Source>MITRE</et:Source><et:Discovered_DateTime>2013-09-18T06:06:47</et:Discovered_DateTime><et:References><stixCommon:Reference>https://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2018-4878</stixCommon:Reference></et:References></et:Vulnerability></stixCommon:Exploit_Target></stix:Exploit_Targets></stix:STIX_Package>',
  'application/octet-stream': window.atob('clCKJVGNbyZ1bnZRHUs59WGKOgmRhsr0KqrghGM2gvssztCfvTdHk/1+M9vysdVYzjqgNnngIst8NVDR6Ae0Pv2kznEqDZNGp7pZhMQhPvBIw1WcLDiu9OZMopzkX+ttoZK8Okr6vJby65Knz1DBGuQQqf+BIEWNdcRn+T0/roI='),
  'text/plain': 'This is some plain text content.'
}

const generateData = (content: any) => {
  if (content.mediaType in mockedData) {
    return mockedData[content.mediaType];
  } else {
    return `No mocked content with this media type: ${content.mediaType}`;
  }
};

export default generateData;
