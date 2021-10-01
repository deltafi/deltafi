# frozen_string_literal: true

require 'k8s-ruby'

module Deltafi
  module API
    module Metrics
      def self.nodes(node_name='')
        client = K8s::Client.in_cluster_config
        client.api('metrics.k8s.io/v1beta1').resource('nodes').get(node_name)
      end

      def self.pods(pod_name='')
        client = K8s::Client.in_cluster_config
        client.api('metrics.k8s.io/v1beta1').resource('pods', namespace: NAMESPACE).get(pod_name)
      end
    end
  end
end