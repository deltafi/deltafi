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
              container_statuses.any? do |container_status|
                !(container_status.started && container_status.ready)
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
