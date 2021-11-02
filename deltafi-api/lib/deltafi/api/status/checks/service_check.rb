# frozen_string_literal: true

require 'deltafi/api/status/check'

module Deltafi
  module API
    module Status
      module Checks
        class ServiceCheck < Status::Check
          def initialize
            super('Kubernetes Service Check')
          end

          def run
            services = Deltafi::API.k8s_client.api('v1').resource('services', namespace: NAMESPACE).get('').items
            service_names = services.map { |s| s.metadata.name }.sort
            missing = expected_services - service_names

            unless missing.empty?
              self.code = 2
              message_lines << '##### Missing Services'
              message_lines << missing.map { |n| "- #{n}" }

              message_lines << '##### Recommendation'
              message_lines << 'Run the installer:'
              message_lines << "\n\t$ deltafi install"
            end

            # TODO: Test ports?

            self
          end

          private

          def expected_services
            YAML.safe_load(config)['expected_services']
          end
        end
      end
    end
  end
end
