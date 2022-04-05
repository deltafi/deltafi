# frozen_string_literal: true

require 'deltafi/monitor/status/check'

module Deltafi
  module Monitor
    module Status
      module Checks
        class DeploymentCheck < Status::Check
          def initialize
            super('Kubernetes Deployment Check')
          end

          def run
            deployments = DF.k8s_client.api('apps/v1').resource('deployments', namespace: DF::Common::K8S_NAMESPACE).get('').items
            deployment_names = deployments.map { |d| d.metadata.name }.sort
            missing = expected_deployments - deployment_names

            unless missing.empty?
              self.code = 2
              message_lines << '##### Missing Deployments'
              message_lines << missing.map { |n| "- #{n}" }

              message_lines << '##### Recommendation'
              message_lines << 'Run the installer:'
              message_lines << "\n\t$ deltafi install"
            end

            self
          end

          private

          def expected_deployments
            YAML.safe_load(config)['expected_deployments']
          end
        end
      end
    end
  end
end
