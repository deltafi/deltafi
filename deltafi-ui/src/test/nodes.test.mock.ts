import { ref } from 'vue';

const nodes = ref([
    {
        metadata: {
            name: "df-dev-01",
            selfLink: "/apis/metrics.k8s.io/v1beta1/nodes/df-dev-01",
            creationTimestamp: "2021-10-05T19:42:40Z"
        },
        timestamp: "2021-10-05T19:42:02Z",
        window: "30s",
        usage: {
            cpu: "1140575898n",
            memory: "5407420Ki"
        }
    },
    {
        metadata: {
            name: "df-dev-02",
            selfLink: "/apis/metrics.k8s.io/v1beta1/nodes/df-dev-02",
            creationTimestamp: "2021-10-05T19:42:40Z"
        },
        timestamp: "2021-10-05T19:42:03Z",
        window: "30s",
        usage: {
            cpu: "1174669182n",
            memory: "7134828Ki"
        }
    },
    {
        metadata: {
            name: "df-dev-03",
            selfLink: "/apis/metrics.k8s.io/v1beta1/nodes/df-dev-03",
            creationTimestamp: "2021-10-05T19:42:40Z"
        },
        timestamp: "2021-10-05T19:42:03Z",
        window: "30s",
        usage: {
            cpu: "261983036n",
            memory: "4934932Ki"
        }
    }
]);
export default nodes;
