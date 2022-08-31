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

const generateData = () => {
  return {
    versions: [
      {
        app: "deltafi-api",
        container: "deltafi-api",
        image: {
          name: "deltafi/deltafi-api",
          tag: "0.21.0",
        },
        group: "deltafi-core",
      },
      {
        app: "deltafi-config-server",
        container: "deltafi-config-server",
        image: {
          name: "deltafi/deltafi-config-server",
          tag: "0.21.0",
        },
        group: "deltafi-core",
      },
      {
        app: "deltafi-core-actions",
        container: "deltafi-core-actions",
        image: {
          name: "deltafi/deltafi-core-actions",
          tag: "0.21.0",
        },
        group: "deltafi-core",
      },
      {
        app: "deltafi-core-domain",
        container: "deltafi-core-domain",
        image: {
          name: "deltafi/deltafi-core-domain",
          tag: "0.21.0",
        },
        group: "deltafi-core",
      },
      {
        app: "deltafi-egress-sink",
        container: "deltafi-egress-sink",
        image: {
          name: "deltafi/deltafi-egress-sink",
          tag: "0.21.0",
        },
        group: "deltafi-core",
      },
      {
        app: "deltafi-gateway",
        container: "deltafi-gateway",
        image: {
          name: "deltafi/deltafi-gateway",
          tag: "0.21.0",
        },
        group: "deltafi-core",
      },
      {
        app: "deltafi-ingress",
        container: "deltafi-ingress",
        image: {
          name: "deltafi/deltafi-ingress",
          tag: "0.21.0",
        },
        group: "deltafi-core",
      },
      {
        app: "deltafi-monitor",
        container: "deltafi-monitor",
        image: {
          name: "deltafi/deltafi-api",
          tag: "0.21.0",
        },
        group: "deltafi-core",
      },
      {
        app: "deltafi-passthrough-actions",
        container: "deltafi-passthrough-actions",
        image: {
          name: "deltafi/deltafi-passthrough-actions",
          tag: "0.21.0",
        },
        group: "deltafi-core",
      },
      {
        app: "deltafi-ui",
        container: "deltafi-ui",
        image: {
          name: "deltafi/deltafi-ui",
          tag: "0.21.0",
        },
        group: "deltafi-core",
      },
      {
        app: "elasticsearch-master",
        container: "elasticsearch",
        image: {
          name: "docker.elastic.co/elasticsearch/elasticsearch",
          tag: "7.16.2",
        },
        group: null,
      },
      {
        app: "fluentd",
        container: "fluentd",
        image: {
          name: "fluent/fluentd-kubernetes-daemonset",
          tag: "v1.12.0-debian-elasticsearch7-1.0",
        },
        group: null,
      },
      {
        app: "kibana",
        container: "kibana",
        image: {
          name: "docker.elastic.co/kibana/kibana",
          tag: "7.16.2",
        },
        group: null,
      },
      {
        app: "kubernetes-dashboard",
        container: "kubernetes-dashboard",
        image: {
          name: "kubernetesui/dashboard",
          tag: "v2.3.0",
        },
        group: null,
      },
      {
        app: "metricbeat",
        container: "metricbeat",
        image: {
          name: "docker.elastic.co/beats/metricbeat",
          tag: "7.16.2",
        },
        group: null,
      },
      {
        app: "minio",
        container: "minio",
        image: {
          name: "minio/minio",
          tag: "RELEASE.2021-02-14T04-01-33Z",
        },
        group: null,
      },
      {
        app: "mongodb",
        container: "mongodb",
        image: {
          name: "docker.io/bitnami/mongodb",
          tag: "4.4.5-debian-10-r0",
        },
        group: null,
      },
      {
        app: "redis",
        container: "redis",
        image: {
          name: "docker.io/bitnami/redis",
          tag: "6.2.4-debian-10-r13",
        },
        group: null,
      },
      {
        app: "zipkin",
        container: "zipkin-collector",
        image: {
          name: "openzipkin/zipkin-slim",
          tag: "2.23.15",
        },
        group: null,
      },
      {
        app: "deltafi-stix-actions",
        container: "stix-conversion-server",
        image: {
          name: "deltafi/stix-conversion-server",
          tag: "0.0.1",
        },
        group: "deltafi-plugins",
      },
      {
        app: "deltafi-stix-actions",
        container: "deltafi-stix-actions",
        image: {
          name: "deltafi-stix/deltafi-stix-actions",
          tag: "1.0.0",
        },
        group: "deltafi-plugins",
      },
    ],
    timestamp: "2022-04-01 16:06:14 -0400",
  };
};

export default generateData();
