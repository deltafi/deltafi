# frozen_string_literal: true

require 'deltafi/api/status/check'

module Deltafi
  module API
    module Status
      module Checks
        class IngressCheck < Status::Check
          def initialize
            super('Kubernetes Ingress Check')
          end

          def run
            ingresses = Deltafi::API.k8s_client.api('networking.k8s.io/v1').resource('ingresses', namespace: NAMESPACE).get('').items
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
