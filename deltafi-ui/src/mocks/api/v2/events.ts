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
  return [
    {
      "id": "e91b456e-f91f-4254-a1fb-5312tggr45",
      "severity": "success",
      "content": "This is a successful test message",
      "summary": "Successful message",
      "timestamp": "2023-03-16T13:00:00.000Z",
      "source": "myapp",
      "notification": true,
      "acknowledged": false
    },
    {
      "id": "d3a4b4c6-7f8e-4d7c-a08f-e0e20f562fb5",
      "severity": "warn",
      "content": "This is a warning test message",
      "summary": "Warning message",
      "timestamp": "2023-03-16T14:00:00.000Z",
      "source": "myapp",
      "notification": true,
      "acknowledged": false
    },
    {
      "id": "a6b7c8d9-e0f1-2g3h-4i5j-6k7l8m9n0o1p",
      "severity": "error",
      "content": "This is an error test message",
      "summary": "Error message",
      "timestamp": "2023-03-16T15:00:00.000Z",
      "source": "myapp",
      "notification": true,
      "acknowledged": false
    },
    {
      "id": "1a2b3c4d-5e6f-7g8h-9i0j-k1l2m3n4o5p6",
      "severity": "info",
      "content": "This is an informational test message",
      "summary": "Informational message",
      "timestamp": "2023-03-16T16:00:00.000Z",
      "source": "myapp",
      "notification": true,
      "acknowledged": false
    },
  ];
};
export default generateData();
