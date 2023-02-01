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
  console.log('yay')
  return [{
    "_id": "e91b456e-f91f-4254-a1fb-56dda810853c",
    "severity": "info",
    "content": "Blah",
    "summary": "This is a warning!",
    "timestamp": "2023-01-03T15:42:06.285Z",
    "source": "grafana",
    "notification": true,
    "acknowledged": false
  },
  {
    "_id": "e91b456e-f91f-4254-a1fb-5457884",
    "severity": "info",
    "content": "Blah blah blah",
    "summary": "This is a warning!",
    "timestamp": "2023-01-05T15:42:06.285Z",
    "source": "grafana",
    "notification": true,
    "acknowledged": false
  },
  {
    "_id": "e91b456e-f91f-4254-a1fb-34ere4454",
    "severity": "warn",
    "content": "Blah",
    "summary": "This is a warning!",
    "timestamp": "2023-01-03T15:42:06.285Z",
    "source": "grafana",
    "notification": true,
    "acknowledged": true
  },
  {
    "_id": "e91b456e-f91f-4254-a1fb-5312tggr45",
    "severity": "success",
    "content": "Blah blah blah",
    "summary": "This is a warning!",    
    "timestamp": "2023-01-05T15:42:06.285Z",
    "source": "grafana",
    "notification": true,
    "acknowledged": true
  }];
};
export default generateData();
