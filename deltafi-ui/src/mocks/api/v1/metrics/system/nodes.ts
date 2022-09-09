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
import { faker } from "@faker-js/faker";

const generateData = () => {
  return {
    nodes: [
      {
        name: "df-dev-01",
        resources: {
          cpu: {
            limit: 2000,
            usage: faker.datatype.number({ min: 500, max: 2000 }),
          },
          memory: {
            limit: 8061513728,
            usage: faker.datatype.number({ min: 4061513728, max: 6061513728 }),
          },
          disk: {
            limit: 101000000000,
            usage: 14123817690,
          },
        },
        pods: [
          {
            name: "argocd-application-controller-0",
            namespace: "argocd",
            resources: {
              cpu: {
                limit: 0,
                request: 0,
                usage: 12,
              },
              memory: {
                limit: 0,
                request: 0,
                usage: 291885056,
              },
            },
          },
          {
            name: "argocd-repo-server-5fbf484547-jdsmt",
            namespace: "argocd",
            resources: {
              cpu: {
                limit: 0,
                request: 0,
                usage: 1,
              },
              memory: {
                limit: 0,
                request: 0,
                usage: 94773248,
              },
            },
          },
          {
            name: "deltafi-api-64dcf498fc-jcsdv",
            namespace: "deltafi",
            resources: {
              cpu: {
                limit: 0,
                request: 0,
                usage: 73,
              },
              memory: {
                limit: 0,
                request: 0,
                usage: 120774656,
              },
            },
          },
          {
            name: "deltafi-egress-sink-f7c69747d-b7hjb",
            namespace: "deltafi",
            resources: {
              cpu: {
                limit: 0,
                request: 0,
                usage: 6,
              },
              memory: {
                limit: 0,
                request: 0,
                usage: 97591296,
              },
            },
          },
          {
            name: "deltafi-fluentd-dmz4m",
            namespace: "deltafi",
            resources: {
              cpu: {
                limit: 0,
                request: 0,
                usage: 10,
              },
              memory: {
                limit: 0,
                request: 0,
                usage: 196378624,
              },
            },
          },
          {
            name: "deltafi-minio-7544744b7f-ggzq9",
            namespace: "deltafi",
            resources: {
              cpu: {
                limit: 0,
                request: 0,
                usage: 24,
              },
              memory: {
                limit: 0,
                request: 2147483648,
                usage: 133869568,
              },
            },
          },
          {
            name: "deltafi-stix-actions-74f48f8557-9fwv5",
            namespace: "deltafi",
            resources: {
              cpu: {
                limit: 0,
                request: 0,
                usage: 37,
              },
              memory: {
                limit: 0,
                request: 0,
                usage: 408907776,
              },
            },
          },
          {
            name: "deltafi-ui-869c989959-mcknl",
            namespace: "deltafi",
            resources: {
              cpu: {
                limit: 0,
                request: 0,
                usage: 0,
              },
              memory: {
                limit: 0,
                request: 0,
                usage: 7131136,
              },
            },
          },
          {
            name: "deltafi-zipkin-7984d58bdf-2qh2d",
            namespace: "deltafi",
            resources: {
              cpu: {
                limit: 0,
                request: 0,
                usage: 4,
              },
              memory: {
                limit: 0,
                request: 0,
                usage: 173854720,
              },
            },
          },
          {
            name: "default-http-backend-6977475d9b-2trrm",
            namespace: "ingress-nginx",
            resources: {
              cpu: {
                limit: 10,
                request: 10,
                usage: 0,
              },
              memory: {
                limit: 20971520,
                request: 20971520,
                usage: 4661248,
              },
            },
          },
          {
            name: "nginx-ingress-controller-4m678",
            namespace: "ingress-nginx",
            resources: {
              cpu: {
                limit: 0,
                request: 0,
                usage: 2,
              },
              memory: {
                limit: 0,
                request: 0,
                usage: 113127424,
              },
            },
          },
          {
            name: "canal-7tfxz",
            namespace: "kube-system",
            resources: {
              cpu: {
                limit: 0,
                request: 250,
                usage: 16,
              },
              memory: {
                limit: 0,
                request: 0,
                usage: 105975808,
              },
            },
          },
          {
            name: "coredns-55b58f978-q69nm",
            namespace: "kube-system",
            resources: {
              cpu: {
                limit: 0,
                request: 100,
                usage: 2,
              },
              memory: {
                limit: 178257920,
                request: 73400320,
                usage: 18391040,
              },
            },
          },
          {
            name: "coredns-autoscaler-76f8869cc9-mhqff",
            namespace: "kube-system",
            resources: {
              cpu: {
                limit: 0,
                request: 20,
                usage: 0,
              },
              memory: {
                limit: 0,
                request: 10485760,
                usage: 11513856,
              },
            },
          },
          {
            name: "metrics-server-55fdd84cd4-7qh5m",
            namespace: "kube-system",
            resources: {
              cpu: {
                limit: 0,
                request: 0,
                usage: 1,
              },
              memory: {
                limit: 0,
                request: 0,
                usage: 31313920,
              },
            },
          },
          {
            name: "website-785cc8db87-bmtth",
            namespace: "website",
            resources: {
              cpu: {
                limit: 0,
                request: 0,
                usage: 0,
              },
              memory: {
                limit: 0,
                request: 0,
                usage: 3977216,
              },
            },
          },
        ],
      },
      {
        name: "df-dev-02",
        resources: {
          cpu: {
            limit: 2000,
            usage: faker.datatype.number({ min: 500, max: 2000 }),
          },
          memory: {
            limit: 8061513728,
            usage: faker.datatype.number({ min: 4061513728, max: 6061513728 }),
          },
          disk: {
            limit: 100000000000,
            request: 100000000000,
            usage: 19923817690,
          },
        },
        pods: [
          {
            name: "deltafi-config-server-5c88b6c8f5-jdbtk",
            namespace: "deltafi",
            resources: {
              cpu: {
                limit: 0,
                request: 0,
                usage: 6,
              },
              memory: {
                limit: 0,
                request: 0,
                usage: 300085248,
              },
            },
          },
          {
            name: "deltafi-fluentd-64xkp",
            namespace: "deltafi",
            resources: {
              cpu: {
                limit: 0,
                request: 0,
                usage: 15,
              },
              memory: {
                limit: 0,
                request: 0,
                usage: 180269056,
              },
            },
          },
          {
            name: "deltafi-kibana-c75fc7b95-hj68h",
            namespace: "deltafi",
            resources: {
              cpu: {
                limit: 1000,
                request: 1000,
                usage: 18,
              },
              memory: {
                limit: 2147483648,
                request: 2147483648,
                usage: 570744832,
              },
            },
          },
          {
            name: "deltafi-mongodb-775c9b6f64-d7rjt",
            namespace: "deltafi",
            resources: {
              cpu: {
                limit: 0,
                request: 0,
                usage: 40,
              },
              memory: {
                limit: 0,
                request: 0,
                usage: 580009984,
              },
            },
          },
          {
            name: "deltafi-passthrough-actions-695445c76-zbl9r",
            namespace: "deltafi",
            resources: {
              cpu: {
                limit: 0,
                request: 0,
                usage: 5,
              },
              memory: {
                limit: 0,
                request: 0,
                usage: 233959424,
              },
            },
          },
          {
            name: "nginx-ingress-controller-hqw99",
            namespace: "ingress-nginx",
            resources: {
              cpu: {
                limit: 0,
                request: 0,
                usage: 1,
              },
              memory: {
                limit: 0,
                request: 0,
                usage: 120918016,
              },
            },
          },
          {
            name: "calico-kube-controllers-7d5d95c8c9-p2ljl",
            namespace: "kube-system",
            resources: {
              cpu: {
                limit: 0,
                request: 0,
                usage: 2,
              },
              memory: {
                limit: 0,
                request: 0,
                usage: 49876992,
              },
            },
          },
          {
            name: "canal-ghr5t",
            namespace: "kube-system",
            resources: {
              cpu: {
                limit: 0,
                request: 250,
                usage: 18,
              },
              memory: {
                limit: 0,
                request: 0,
                usage: 102309888,
              },
            },
          },
          {
            name: "coredns-55b58f978-dmbfk",
            namespace: "kube-system",
            resources: {
              cpu: {
                limit: 0,
                request: 100,
                usage: 2,
              },
              memory: {
                limit: 178257920,
                request: 73400320,
                usage: 30330880,
              },
            },
          },
        ],
      },
      {
        name: "df-dev-03",
        resources: {
          cpu: {
            limit: 2000,
            usage: faker.datatype.number({ min: 500, max: 2000 }),
          },
          memory: {
            limit: 8061513728,
            usage: faker.datatype.number({ min: 4061513728, max: 6061513728 }),
          },
          disk: {
            limit: 100000000000,
            request: 100000000000,
            usage: 23123817690,
          },
        },
        pods: [
          {
            name: "argocd-dex-server-9dc558f5-zh4wg",
            namespace: "argocd",
            resources: {
              cpu: {
                limit: 0,
                request: 0,
                usage: 0,
              },
              memory: {
                limit: 0,
                request: 0,
                usage: 27394048,
              },
            },
          },
          {
            name: "argocd-redis-759b6bc7f4-tkvzw",
            namespace: "argocd",
            resources: {
              cpu: {
                limit: 0,
                request: 0,
                usage: 3,
              },
              memory: {
                limit: 0,
                request: 0,
                usage: 13000704,
              },
            },
          },
          {
            name: "argocd-server-588d8f485b-c6tsr",
            namespace: "argocd",
            resources: {
              cpu: {
                limit: 0,
                request: 0,
                usage: 0,
              },
              memory: {
                limit: 0,
                request: 0,
                usage: 70508544,
              },
            },
          },
          {
            name: "deltafi-core-actions-74646b5bbd-qk5q5",
            namespace: "deltafi",
            resources: {
              cpu: {
                limit: 0,
                request: 0,
                usage: 13,
              },
              memory: {
                limit: 0,
                request: 0,
                usage: 323084288,
              },
            },
          },
          {
            name: "deltafi-core-f89544bd5-vtvbv",
            namespace: "deltafi",
            resources: {
              cpu: {
                limit: 0,
                request: 0,
                usage: 14,
              },
              memory: {
                limit: 0,
                request: 0,
                usage: 1306750976,
              },
            },
          },
          {
            name: "deltafi-fluentd-9whmc",
            namespace: "deltafi",
            resources: {
              cpu: {
                limit: 0,
                request: 0,
                usage: 12,
              },
              memory: {
                limit: 0,
                request: 0,
                usage: 197869568,
              },
            },
          },
          {
            name: "deltafi-gateway-774f4dc4c6-rw9r7",
            namespace: "deltafi",
            resources: {
              cpu: {
                limit: 0,
                request: 0,
                usage: 6,
              },
              memory: {
                limit: 0,
                request: 0,
                usage: 94044160,
              },
            },
          },
          {
            name: "deltafi-ingress-59dbd9cd86-6lqbz",
            namespace: "deltafi",
            resources: {
              cpu: {
                limit: 0,
                request: 0,
                usage: 3,
              },
              memory: {
                limit: 0,
                request: 0,
                usage: 229810176,
              },
            },
          },
          {
            name: "deltafi-kubernetes-dashboard-574748f947-wh96j",
            namespace: "deltafi",
            resources: {
              cpu: {
                limit: 2000,
                request: 100,
                usage: 0,
              },
              memory: {
                limit: 209715200,
                request: 209715200,
                usage: 35262464,
              },
            },
          },
          {
            name: "deltafi-redis-master-0",
            namespace: "deltafi",
            resources: {
              cpu: {
                limit: 0,
                request: 0,
                usage: 11,
              },
              memory: {
                limit: 0,
                request: 0,
                usage: 17862656,
              },
            },
          },
          {
            name: "elasticsearch-master-0",
            namespace: "deltafi",
            resources: {
              cpu: {
                limit: 1000,
                request: 100,
                usage: 104,
              },
              memory: {
                limit: 2000000000,
                request: 1000000000,
                usage: 1688006656,
              },
            },
          },
          {
            name: "nginx-ingress-controller-kb8lr",
            namespace: "ingress-nginx",
            resources: {
              cpu: {
                limit: 0,
                request: 0,
                usage: 2,
              },
              memory: {
                limit: 0,
                request: 0,
                usage: 91324416,
              },
            },
          },
          {
            name: "canal-x2qrp",
            namespace: "kube-system",
            resources: {
              cpu: {
                limit: 0,
                request: 250,
                usage: 19,
              },
              memory: {
                limit: 0,
                request: 0,
                usage: 81362944,
              },
            },
          },
        ],
      },
    ],
    timestamp: "2022-03-01 21:33:31 +0000",
  };
};

export default generateData();
