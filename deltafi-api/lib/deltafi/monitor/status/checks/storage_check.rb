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
        class StorageCheck < Status::Check
          USAGE_THRESHOLD = 90

          def initialize
            super('Kubernetes Storage Check')
          end

          def run
            check_pvcs
            check_usage

            if code == 2
              message_lines << '##### Recommendation'
              message_lines << 'Run ansible:'
              message_lines << "\n\t$ ansible-playbook bootstrap-deltafi.yml -i inventory/site"
            end

            self
          end

          def check_pvcs
            pvcs = DF.k8s_client.api('v1').resource('persistentvolumeclaims', namespace: DF::Common::K8S_NAMESPACE).get('').items
            unbound = pvcs.reject { |pvc| pvc.status.phase == 'Bound' }
            unless unbound.empty?
              self.code = 2
              message_lines << '##### Unbound Persistent Volume Claims'
              message_lines << unbound.map { |pvc| "- #{pvc.metadata.name}" }
            end

            pvc_names = pvcs.map { |pvc| pvc.metadata.name }.sort
            pvc_missing = expected_volume_claims - pvc_names
            return if pvc_missing.empty?

            self.code = 2
            message_lines << '##### Missing Persistent Volume Claims'
            message_lines << pvc_missing.map { |n| "- #{n}" }
          end

          def check_usage
            nodes_over_threshold = []
            DF::API::V1::Metrics::System.disks_by_node.each do |node, metrics|
              percent = (metrics&.dig(:usage, :pct).to_f * 100).floor
              nodes_over_threshold << "__#{node}:/data__ at __#{percent}%__" if percent >= USAGE_THRESHOLD
            end
            return if nodes_over_threshold.empty?

            self.code = 1
            message_lines << "##### Nodes with disk usage over threshold (#{USAGE_THRESHOLD}%)"
            message_lines << nodes_over_threshold.map { |n| "- #{n}" }
          end

          private

          def expected_volume_claims
            YAML.safe_load(config)['expected_volume_claims']
          end
        end
      end
    end
  end
end
