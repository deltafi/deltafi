# frozen_string_literal: true

require 'deltafi/api/status/check'

module Deltafi
  module API
    module Status
      module Checks
        class StorageCheck < Status::Check
          USAGE_THRESHOLD = 90
          DATA_MOUNT_POINT = '/data'

          def initialize
            super('Kubernetes Storage Check')
          end

          def run
            check_pvs
            check_pvcs
            check_usage

            if code == 2
              message_lines << '##### Recommendation'
              message_lines << 'Run ansible:'
              message_lines << "\n\t$ ansible-playbook bootstrap-deltafi.yml -i inventory/site"
            end

            self
          end

          def check_pvs
            pvs = Deltafi::API.k8s_client.api('v1').resource('persistentvolumes').get('').items
            pv_names = pvs.map { |pv| pv.metadata.name }.sort
            pv_missing = expected_volumes - pv_names
            return if pv_missing.empty?

            self.code = 2
            message_lines << '##### Missing Persistent Volumes'
            message_lines << pv_missing.map { |n| "- #{n}" }
          end

          def check_pvcs
            pvcs = Deltafi::API.k8s_client.api('v1').resource('persistentvolumeclaims', namespace: NAMESPACE).get('').items
            pvc_names = pvcs.map { |pvc| pvc.metadata.name }.sort
            pvc_missing = expected_volumes - pvc_names
            return if pvc_missing.empty?

            self.code = 2
            message_lines << '##### Missing Persistent Volume Claims'
            message_lines << pvc_missing.map { |n| "- #{n}" }
          end

          def check_usage
            nodes_over_threshold = []
            Deltafi::API::Metrics::System.disks_by_node(mount_point: DATA_MOUNT_POINT).each do |node, metrics|
              percent = (metrics&.dig(:usage, :pct).to_f * 100).floor
              nodes_over_threshold << "__#{node}:#{DATA_MOUNT_POINT}__ at __#{percent}%__" if percent >= USAGE_THRESHOLD
            end
            return if nodes_over_threshold.empty?

            self.code = 1
            message_lines << "##### Nodes with disk usage over threshold (#{USAGE_THRESHOLD}%)"
            message_lines << nodes_over_threshold.map { |n| "- #{n}" }
          end

          private

          def expected_volumes
            YAML.safe_load(config)['expected_volumes']
          end
        end
      end
    end
  end
end
