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

import { faker } from "@faker-js/faker";

const generateData = () => {
  return {
    nodes: [
      {
        name: "df-mock-01",
        resources: {
          cpu: {
            limit: 4000,
            usage: faker.number.int({ min: 2000, max: 3000 })
          },
          memory: {
            limit: 8232894464,
            usage: faker.number.int({ min: 4116447232, max: 6174670848 })
          },
          disk: {
            limit: 107374182400,
            usage: faker.number.int({ min: 85899345920, max: 88046829568 })
          }
        },
        apps: [
          {
            name: "deltafi-mongodb-79b6699d44-xgp6q"
          },
          {
            name: "deltafi-monitor-68d66b857f-bxzj8"
          },
          {
            name: "deltafi-nodemonitor-mjb8z"
          },
          {
            name: "deltafi-passthrough-56b5d56ccb-nzbd9"
          },
          {
            name: "deltafi-promtail-9gpwv"
          },
          {
            name: "deltafi-redis-master-0"
          },
          {
            name: "deltafi-ui-7954c66dd4-rrn9n"
          }
        ]
      },
      {
        name: "df-mock-02",
        resources: {
          cpu: {
            limit: 4000,
            usage: faker.number.int({ min: 2000, max: 3000 })
          },
          memory: {
            limit: 8232894464,
            usage: faker.number.int({ min: 4116447232, max: 6174670848 })
          },
          disk: {
            limit: 107374182400,
            usage: faker.number.int({ min: 85899345920, max: 88046829568 })
          }
        },
        apps: [
          {
            name: "deltafi-egress-sink-7ffb4546-v8wml"
          },
          {
            name: "deltafi-grafana-5656d4f775-zdnr8"
          },
          {
            name: "deltafi-graphite-0"
          },
          {
            name: "deltafi-ingress-d5546d8bd-f9fnj"
          },
          {
            name: "deltafi-kubernetes-dashboard-6cb87f577c-g2km6"
          },
          {
            name: "deltafi-loki-0"
          },
          {
            name: "deltafi-minio-8598bd99d-8gfwq"
          },

        ]
      },
      {
        name: "df-mock-03",
        resources: {
          cpu: {
            limit: 4000,
            usage: faker.number.int({ min: 2000, max: 3000 })
          },
          memory: {
            limit: 8232894464,
            usage: faker.number.int({ min: 4116447232, max: 6174670848 })
          },
          disk: {
            limit: 107374182400,
            usage: faker.number.int({ min: 85899345920, max: 88046829568 })
          }
        },
        apps: [
          {
            name: "deltafi-api-7d4c95f656-j7lfr"
          },
          {
            name: "deltafi-auth-66459f4989-qhdl9"
          },
          {
            name: "deltafi-core-7c66b6878-s2sh2"
          },
          {
            name: "deltafi-core-actions-6fcfbf6ff4-hfwq6"
          },
          {
            name: "deltafi-core-worker-0"
          },
          {
            name: "deltafi-docs-5676979dfc-sjqqf"
          },
        ]
      }
    ],
    timestamp: new Date().toISOString(),
  };
};

export default generateData();
