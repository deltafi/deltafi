import ReconnectingEventSource from "reconnecting-eventsource";
import { ref, Ref } from "vue";

const serverSentEvents = new ReconnectingEventSource("/api/v1/events");

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