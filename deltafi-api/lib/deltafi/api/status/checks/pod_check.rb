# frozen_string_literal: true

require 'deltafi/api/status/check'

module Deltafi
  module API
    module Status
      module Checks
        class PodCheck < Status::Check
          def initialize
            super('Kubernetes Pod Check')
          end

          def run
            pods = Deltafi::API.k8s_client.api('v1').resource('pods', namespace: NAMESPACE).get('').items
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
