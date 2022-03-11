import { reactive, ref } from 'vue'
import useGraphQL from './useGraphQL'

export default function useDeltaFiles() {
  const { response, queryGraphQL, loading, errors } = useGraphQL();
  const loaded = ref(false);
  const data = reactive({});

  const buildGetDeltaFileQuery = (did: string) => {
    return {
      deltaFile: {
        __args: {
          did: did,
        },
        did: true,
        sourceInfo: {
          filename: true,
          flow: true,
          metadata: {
            key: true,
            value: true,
          },
        },
        stage: true,
        created: true,
        modified: true,
        actions: {
          name: true,
          state: true,
          created: true,
          modified: true,
          errorCause: true,
          errorContext: true,
        },
        domains: {
          name: true,
          value: true,
          mediaType: true,
        },
        enrichment: {
          name: true,
          value: true,
          mediaType: true,
        },
        formattedData: {
          filename: true,
          metadata: {
            key: true,
            value: true,
          },
          formatAction: true,
          egressActions: true,
          contentReference: {
            did: true,
            uuid: true,
            offset: true,
            size: true,
            mediaType: true,
          },
        },
        protocolStack: {
          action: true,
          metadata: {
            key: true,
            value: true,
          },
          content: {
            name: true,
            metadata: {
              key: true,
              value: true
            },
            contentReference: {
              did: true,
              uuid: true,
              offset: true,
              size: true,
              mediaType: true
            }
          },
        },
        markedForDelete: true,
        markedForDeleteReason: true,
        errorAcknowledged: true,
        errorAcknowledgedReason: true,
      }
    }
  };

  const getDeltaFile = async (did: string) => {
    await queryGraphQL(buildGetDeltaFileQuery(did), "getDeltaFile");
    Object.assign(data, response.value.data.deltaFile)
    loaded.value = true;
  }

  return { data, loading, loaded, getDeltaFile, errors };
}