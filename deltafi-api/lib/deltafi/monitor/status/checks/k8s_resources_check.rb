#
#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
#
#    Licensed under the Apache License, Version 2.0 (the "License");
#    you may not use this file except in compliance with the License.
#    You may obtain a copy of the License at
#
#        http://www.apache.org/licenses/LICENSE-2.0
#
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS,
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#    See the License for the specific language governing permissions and
#    limitations under the License.
#

# frozen_string_literal: true

require 'deltafi/monitor/status/check'

module Deltafi
  module Monitor
    module Status
      module Checks
        class K8sResourcesCheck < Status::Check
          RESOURCES = DF.cluster_mode? ? [
            DF.k8s_client.api('apps/v1').resource('deployments', namespace: DF::Common::K8S_NAMESPACE),
            DF.k8s_client.api('networking.k8s.io/v1').resource('ingresses', namespace: DF::Common::K8S_NAMESPACE),
            DF.k8s_client.api('v1').resource('pods', namespace: DF::Common::K8S_NAMESPACE),
            DF.k8s_client.api('v1').resource('services', namespace: DF::Common::K8S_NAMESPACE),
            DF.k8s_client.api('apps/v1').resource('statefulsets', namespace: DF::Common::K8S_NAMESPACE),
            DF.k8s_client.api('v1').resource('persistentvolumeclaims', namespace: DF::Common::K8S_NAMESPACE)
          ] : []

          def initialize
            super('Kubernetes Resource Check')
          end

          def self.should_run?
            DF.cluster_mode?
          end

          def run
            if DF.cluster_mode?
              k8s_client = DF.k8s_client
              resources = k8s_client.list_resources(RESOURCES, namespace: DF::Common::K8S_NAMESPACE)

              check_pods(resources.select { |r| r.kind == 'Pod' })

              config = k8s_client.api('v1').resource('configmaps', namespace: 'deltafi').get(CONFIGMAP).data

              missing = 0
              missing += check_missing(resources, config.DeploymentCheck, 'Deployment')
              missing += check_missing(resources, config.IngressCheck, 'Ingress')
              missing += check_missing(resources, config.ServiceCheck, 'Service')
              missing += check_missing(resources, config.StatefulSetCheck, 'StatefulSet')
              missing += check_missing(resources, config.StorageCheck, 'PersistentVolumeClaim')

              unless missing.zero?
                message_lines << '##### Recommendation'
                message_lines << 'Run the installer:'
                message_lines << "\n\t$ deltafi install"
              end
            end

            self
          end

          def check_missing(resources, expected_resources, resource_type)
            resource_names = resources.select { |r| r.kind == resource_type }.map { |r| r.metadata.name }.sort
            missing = YAML.safe_load(expected_resources).first[1] - resource_names

            unless missing.empty?
              self.code = 2
              message_lines << "##### Missing #{resource_type}(s)"
              message_lines << missing.map { |n| "- #{n}" }
            end

            missing.empty? ? 0 : 1
          end

          def check_pods(pods)
            pods_with_issue = pods.select do |pod|
              container_statuses = pod.status.containerStatuses
              container_statuses.nil? || container_statuses.any? do |container_status|
                !((container_status.started && container_status.ready) ||
                  (container_status.state.terminated && container_status.state.terminated.reason == 'Completed'))
              end
            end

            unless pods_with_issue.empty?
              self.code = 1
              message_lines << '##### Pods with issues'
              message_lines << pods_with_issue.map { |n| "- #{n.metadata.name}" }

              message_lines << '##### Recommendation'
              message_lines << "Check the logs:\n"
              message_lines << pods_with_issue.map { |n| "\t$ kubectl logs #{n.metadata.name}" }
            end
          end
        end
      end
    end
  end
end
