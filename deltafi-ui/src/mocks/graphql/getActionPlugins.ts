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

export default {
  actionPlugins: {
    "org.deltafi.testjig.action.SmokeTestTransformAction": "deltafi-testjig",
    "org.deltafi.testjig.action.PassthroughEgressAction": "deltafi-testjig",
    "org.deltafi.helloworld.action.HelloWorldJavaAction": "deltafi-hello-world-java",
    "org.deltafi.core.action.DecompressAction": "deltafi-core-actions",
    "org.deltafi.core.action.JoltTransformAction": "deltafi-core-actions",
  },
};
