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
        class StatefulSetCheck < Status::Check
          def initialize
            super('Kubernetes Stateful Set Check')
          end

          def run
            stateful_sets = DF.k8s_client.api('apps/v1').resource('statefulsets', namespace: DF::Common::K8S_NAMESPACE).get('').items
            stateful_sets_names = stateful_sets.map { |d| d.metadata.name }.sort
            missing = expected_stateful_sets - stateful_sets_names

            unless missing.empty?
              self.code = 2
              message_lines << '##### Missing Stateful Sets'
              message_lines << missing.map { |n| "- #{n}" }

              message_lines << '##### Recommendation'
              message_lines << 'Run the installer:'
              message_lines << "\n\t$ deltafi install"
            end

            self
          end

          private

          def expected_stateful_sets
            YAML.safe_load(config)['expected_stateful_sets']
          end
        end
      end
    end
  end
end
