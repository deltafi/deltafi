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
        class IngressCheck < Status::Check
          def initialize
            super('Kubernetes Ingress Check')
          end

          def run
            ingresses = DF.k8s_client.api('networking.k8s.io/v1').resource('ingresses', namespace: DF::Common::K8S_NAMESPACE).get('').items
            ingress_names = ingresses.map { |i| i.metadata.name }.sort
            missing = expected_ingresses - ingress_names

            unless missing.empty?
              self.code = 2
              message_lines << '##### Missing Ingresses'
              message_lines << missing.map { |n| "- #{n}" }

              message_lines << '##### Recommendation'
              message_lines << 'Run the installer:'
              message_lines << "\n\t$ deltafi install"
            end

            # TODO: Test http?

            self
          end

          private

          def expected_ingresses
            YAML.safe_load(config)['expected_ingresses']
          end
        end
      end
    end
  end
end
