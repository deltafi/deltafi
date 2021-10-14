import { ref } from 'vue';

const pods = ref([
        {
            metadata: {
                name: "deltafi-api-6768d99bd6-fr284",
                namespace: "deltafi",
                selfLink: "/apis/metrics.k8s.io/v1beta1/namespaces/deltafi/pods/deltafi-api-6768d99bd6-fr284",
                creationTimestamp: "2021-10-05T19:42:55Z"
            },
            timestamp: "2021-10-05T19:41:52Z",
            window: "30s",
            containers: [
                {
                    name: "deltafi-api",
                    usage: {
                        cpu: "0",
                        memory: "39704Ki"
                    }
                }
            ]
        },
        {
            metadata: {
                name: "deltafi-core-actions-86ddbf465f-qpjwp",
                namespace: "deltafi",
                selfLink: "/apis/metrics.k8s.io/v1beta1/namespaces/deltafi/pods/deltafi-core-actions-86ddbf465f-qpjwp",
                creationTimestamp: "2021-10-05T19:42:55Z"
            },
            timestamp: "2021-10-05T19:41:51Z",
            window: "30s",
            containers: [
                {
                    name: "deltafi-core-actions",
                    usage: {
                        cpu: "3691409n",
                        memory: "226160Ki"
                    }
                }
            ]
        },
        {
            metadata: {
                name: "deltafi-core-domain-95bbfcddd-bx6mt",
                namespace: "deltafi",
                selfLink: "/apis/metrics.k8s.io/v1beta1/namespaces/deltafi/pods/deltafi-core-domain-95bbfcddd-bx6mt",
                creationTimestamp: "2021-10-05T19:42:55Z"
            },
            timestamp: "2021-10-05T19:41:58Z",
            window: "30s",
            containers: [
                {
                    name: "deltafi-core-domain",
                    usage: {
                        cpu: "17302446n",
                        memory: "376072Ki"
                    }
                }
            ]
        },
        {
            metadata: {
                name: "deltafi-fluentd-8cmq7",
                namespace: "deltafi",
                selfLink: "/apis/metrics.k8s.io/v1beta1/namespaces/deltafi/pods/deltafi-fluentd-8cmq7",
                creationTimestamp: "2021-10-05T19:42:55Z"
            },
            timestamp: "2021-10-05T19:41:54Z",
            window: "30s",
            containers: [
                {
                    name: "fluentd",
                    usage: {
                        cpu: "4279349n",
                        memory: "154856Ki"
                    }
                }
            ]
        },
        {
            metadata: {
                name: "deltafi-fluentd-dgv5s",
                namespace: "deltafi",
                selfLink: "/apis/metrics.k8s.io/v1beta1/namespaces/deltafi/pods/deltafi-fluentd-dgv5s",
                creationTimestamp: "2021-10-05T19:42:55Z"
            },
            timestamp: "2021-10-05T19:42:01Z",
            window: "30s",
            containers: [
                {
                    name: "fluentd",
                    usage: {
                        cpu: "18371030n",
                        memory: "159292Ki"
                    }
                }
            ]
        },
        {
            metadata: {
                name: "deltafi-fluentd-p6bhm",
                namespace: "deltafi",
                selfLink: "/apis/metrics.k8s.io/v1beta1/namespaces/deltafi/pods/deltafi-fluentd-p6bhm",
                creationTimestamp: "2021-10-05T19:42:55Z"
            },
            timestamp: "2021-10-05T19:41:46Z",
            window: "30s",
            containers: [
                {
                    name: "fluentd",
                    usage: {
                        cpu: "6635398n",
                        memory: "150256Ki"
                    }
                }
            ]
        },
        {
            metadata: {
                name: "deltafi-gateway-864f57f9c9-844mw",
                namespace: "deltafi",
                selfLink: "/apis/metrics.k8s.io/v1beta1/namespaces/deltafi/pods/deltafi-gateway-864f57f9c9-844mw",
                creationTimestamp: "2021-10-05T19:42:55Z"
            },
            timestamp: "2021-10-05T19:41:58Z",
            window: "30s",
            containers: [
                {
                    name: "deltafi-gateway",
                    usage: {
                        cpu: "3314163n",
                        memory: "73932Ki"
                    }
                }
            ]
        },
        {
            metadata: {
                name: "deltafi-ingress-6f8fdb9d4f-qcqvc",
                namespace: "deltafi",
                selfLink: "/apis/metrics.k8s.io/v1beta1/namespaces/deltafi/pods/deltafi-ingress-6f8fdb9d4f-qcqvc",
                creationTimestamp: "2021-10-05T19:42:55Z"
            },
            timestamp: "2021-10-05T19:41:58Z",
            window: "30s",
            containers: [
                {
                    name: "deltafi-ingress",
                    usage: {
                        cpu: "4633420n",
                        memory: "222048Ki"
                    }
                }
            ]
        },
        {
            metadata: {
                name: "deltafi-kibana-6db67dd754-bzbwr",
                namespace: "deltafi",
                selfLink: "/apis/metrics.k8s.io/v1beta1/namespaces/deltafi/pods/deltafi-kibana-6db67dd754-bzbwr",
                creationTimestamp: "2021-10-05T19:42:55Z"
            },
            timestamp: "2021-10-05T19:41:48Z",
            window: "30s",
            containers: [
                {
                    name: "kibana",
                    usage: {
                        cpu: "8979863n",
                        memory: "277020Ki"
                    }
                }
            ]
        },
        {
            metadata: {
                name: "deltafi-kubernetes-dashboard-574748f947-bzhbs",
                namespace: "deltafi",
                selfLink: "/apis/metrics.k8s.io/v1beta1/namespaces/deltafi/pods/deltafi-kubernetes-dashboard-574748f947-bzhbs",
                creationTimestamp: "2021-10-05T19:42:55Z"
            },
            timestamp: "2021-10-05T19:42:03Z",
            window: "30s",
            containers: [
                {
                    name: "kubernetes-dashboard",
                    usage: {
                        cpu: "105241n",
                        memory: "16656Ki"
                    }
                }
            ]
        },
        {
            metadata: {
                name: "deltafi-minio-8cdc7bcdb-dndt6",
                namespace: "deltafi",
                selfLink: "/apis/metrics.k8s.io/v1beta1/namespaces/deltafi/pods/deltafi-minio-8cdc7bcdb-dndt6",
                creationTimestamp: "2021-10-05T19:42:55Z"
            },
            timestamp: "2021-10-05T19:41:54Z",
            window: "30s",
            containers: [
                {
                    name: "minio",
                    usage: {
                        cpu: "33027098n",
                        memory: "558948Ki"
                    }
                }
            ]
        },
        {
            metadata: {
                name: "deltafi-mongodb-775c9b6f64-7bxdk",
                namespace: "deltafi",
                selfLink: "/apis/metrics.k8s.io/v1beta1/namespaces/deltafi/pods/deltafi-mongodb-775c9b6f64-7bxdk",
                creationTimestamp: "2021-10-05T19:42:55Z"
            },
            timestamp: "2021-10-05T19:41:50Z",
            window: "30s",
            containers: [
                {
                    name: "mongodb",
                    usage: {
                        cpu: "964036533n",
                        memory: "5370452Ki"
                    }
                }
            ]
        },
        {
            metadata: {
                name: "deltafi-nifi-df794cff4-pjf6l",
                namespace: "deltafi",
                selfLink: "/apis/metrics.k8s.io/v1beta1/namespaces/deltafi/pods/deltafi-nifi-df794cff4-pjf6l",
                creationTimestamp: "2021-10-05T19:42:55Z"
            },
            timestamp: "2021-10-05T19:41:56Z",
            window: "30s",
            containers: [
                {
                    name: "nifi",
                    usage: {
                        cpu: "44616043n",
                        memory: "1222712Ki"
                    }
                }
            ]
        },
        {
            metadata: {
                name: "deltafi-passthrough-actions-869c96bb54-vdqtv",
                namespace: "deltafi",
                selfLink: "/apis/metrics.k8s.io/v1beta1/namespaces/deltafi/pods/deltafi-passthrough-actions-869c96bb54-vdqtv",
                creationTimestamp: "2021-10-05T19:42:55Z"
            },
            timestamp: "2021-10-05T19:41:51Z",
            window: "30s",
            containers: [
                {
                    name: "deltafi-passthrough-actions",
                    usage: {
                        cpu: "5993423n",
                        memory: "225232Ki"
                    }
                }
            ]
        },
        {
            metadata: {
                name: "deltafi-redis-master-0",
                namespace: "deltafi",
                selfLink: "/apis/metrics.k8s.io/v1beta1/namespaces/deltafi/pods/deltafi-redis-master-0",
                creationTimestamp: "2021-10-05T19:42:55Z"
            },
            timestamp: "2021-10-05T19:41:56Z",
            window: "30s",
            containers: [
                {
                    name: "redis",
                    usage: {
                        cpu: "10607935n",
                        memory: "13244Ki"
                    }
                }
            ]
        },
        {
            metadata: {
                name: "deltafi-stix-actions-7567459bd4-hwb8b",
                namespace: "deltafi",
                selfLink: "/apis/metrics.k8s.io/v1beta1/namespaces/deltafi/pods/deltafi-stix-actions-7567459bd4-hwb8b",
                creationTimestamp: "2021-10-05T19:42:55Z"
            },
            timestamp: "2021-10-05T19:41:53Z",
            window: "30s",
            containers: [
                {
                    name: "deltafi-stix-actions",
                    usage: {
                        cpu: "1938478n",
                        memory: "221616Ki"
                    }
                },
                {
                    name: "stix-conversion-server",
                    usage: {
                        cpu: "21383141n",
                        memory: "93984Ki"
                    }
                }
            ]
        },
        {
            metadata: {
                name: "deltafi-stix-domain-7f6575f764-nzm7l",
                namespace: "deltafi",
                selfLink: "/apis/metrics.k8s.io/v1beta1/namespaces/deltafi/pods/deltafi-stix-domain-7f6575f764-nzm7l",
                creationTimestamp: "2021-10-05T19:42:55Z"
            },
            timestamp: "2021-10-05T19:41:51Z",
            window: "30s",
            containers: [
                {
                    name: "deltafi-stix-domain",
                    usage: {
                        cpu: "748433n",
                        memory: "245936Ki"
                    }
                }
            ]
        },
        {
            metadata: {
                name: "deltafi-zipkin-8544d8cf77-2c2l7",
                namespace: "deltafi",
                selfLink: "/apis/metrics.k8s.io/v1beta1/namespaces/deltafi/pods/deltafi-zipkin-8544d8cf77-2c2l7",
                creationTimestamp: "2021-10-05T19:42:55Z"
            },
            timestamp: "2021-10-05T19:42:02Z",
            window: "30s",
            containers: [
                {
                    name: "zipkin-collector",
                    usage: {
                        cpu: "12234670n",
                        memory: "159440Ki"
                    }
                }
            ]
        },
        {
            metadata: {
                name: "elasticsearch-master-0",
                namespace: "deltafi",
                selfLink: "/apis/metrics.k8s.io/v1beta1/namespaces/deltafi/pods/elasticsearch-master-0",
                creationTimestamp: "2021-10-05T19:42:55Z"
            },
            timestamp: "2021-10-05T19:41:50Z",
            window: "30s",
            containers: [
                {
                    name: "elasticsearch",
                    usage: {
                        cpu: "28510603n",
                        memory: "1605612Ki"
                    }
                }
            ]
        }
]);
export default pods;