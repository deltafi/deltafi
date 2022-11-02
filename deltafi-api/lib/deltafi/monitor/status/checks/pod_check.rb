#
#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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
        class PodCheck < Status::Check
          def initialize
            super('Kubernetes Pod Check')
          end

          def run
            pods = DF.k8s_client.api('v1').resource('pods', namespace: DF::Common::K8S_NAMESPACE).get('').items
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

            self
          end
        end
      end
    end
  end
end
