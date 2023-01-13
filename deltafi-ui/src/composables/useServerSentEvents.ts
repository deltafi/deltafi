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

import ReconnectingEventSource from "reconnecting-eventsource";
import { ref, Ref } from "vue";

const serverSentEvents = new ReconnectingEventSource("/api/v1/sse");

export default function useServerSentEvents() {
  const connectionStatus = ref('CONNECTING') as Ref<'CONNECTING' | 'CONNECTED' | 'DISCONNECTED'>;

  serverSentEvents.addEventListener('open', () => {
    connectionStatus.value = 'CONNECTED';
  });

  serverSentEvents.addEventListener('error', () => {
    if (connectionStatus.value === 'CONNECTED') {
      connectionStatus.value = 'DISCONNECTED';
    }
  });

  return {
    serverSentEvents,
    connectionStatus
  };
}
