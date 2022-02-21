# frozen_string_literal: true

module Deltafi
  module API
    module Metrics
      module System
        class << self
          def nodes
            nodes = Deltafi::API.k8s_client.api('v1').resource('nodes').list
            node_usage = Deltafi::API.k8s_client.api('metrics.k8s.io/v1beta1').resource('nodes').list

            pods = pods_by_node
            pvs = pvs_by_node

            nodes.map do |node|
              node_pods = pods[node.metadata.name] || []
              {
                name: node.metadata.name,
                resources: {
                  cpu: {
                    limit: normalize_cpu(node.status.capacity.cpu),
                    request: node_pods.reduce(0) { |ntotal, p| ntotal + p[:resources][:cpu][:request] },
                    usage: normalize_cpu(node_usage.find { |n| n.metadata.name == node.metadata.name }.usage.cpu)
                  },
                  memory: {
                    limit: normalize_bytes(node.status.capacity.memory),
                    request: node_pods.reduce(0) { |ntotal, p| ntotal + p[:resources][:memory][:request] },
                    usage: normalize_bytes(node_usage.find { |n| n.metadata.name == node.metadata.name }.usage.memory)
                  },
                  disk: {
                    limit: pvs[node.metadata.name]&.reduce(0) { |ntotal, pv| ntotal + normalize_bytes(pv.spec.capacity.storage) } || 0,
                    request: pvs[node.metadata.name]&.reduce(0) { |ntotal, pv| ntotal + normalize_bytes(pv.spec.capacity.storage) } || 0,
                    usage: 0 # TODO
                  }
                },
                pods: node_pods
              }
            end
          end

          private

          def pods_by_node
            pods_by_node = Deltafi::API.k8s_client.api('v1').resource('pods').list(fieldSelector: { 'status.phase' => 'Running' }).group_by { |p| p.spec.nodeName }
            pod_usage = Deltafi::API.k8s_client.api('metrics.k8s.io/v1beta1').resource('pods').list
            pods_by_node.transform_values do |pods|
              pods.map do |pod|
                {
                  name: pod.metadata.name,
                  namespace: pod.metadata.namespace,
                  resources: {
                    cpu: {
                      limit: pod.spec.containers.reduce(0) { |ptotal, c| ptotal + normalize_cpu(c.resources.limits&.cpu || 0) },
                      request: pod.spec.containers.reduce(0) { |ptotal, c| ptotal + normalize_cpu(c.resources.requests&.cpu || 0) },
                      usage: (pod_usage.find { |p| p.metadata.name == pod.metadata.name }&.containers || []).reduce(0) { |ptotal, c| ptotal + normalize_cpu(c.usage.cpu) }
                    },
                    memory: {
                      limit: pod.spec.containers.reduce(0) { |ptotal, c| ptotal + normalize_bytes(c.resources.limits&.memory || 0) },
                      request: pod.spec.containers.reduce(0) { |ptotal, c| ptotal + normalize_bytes(c.resources.requests&.memory || 0) },
                      usage: (pod_usage.find { |p| p.metadata.name == pod.metadata.name }&.containers || []).reduce(0) { |ptotal, c| ptotal + normalize_bytes(c.usage.memory) }
                    }
                  }
                }
              end
            end
          end

          def pvs_by_node
            Deltafi::API.k8s_client.api('v1').resource('persistentvolumes').list.group_by do |pv|
              pv.spec.nodeAffinity.required.nodeSelectorTerms.first.matchExpressions.first.values.first
            end
          end

          # Normalize CPU resources
          def normalize_cpu(cpu_string)
            case cpu_string.to_s
            when /m$/
              cpu_string.to_i
            when /n$/
              cpu_string.to_i / 1_000_000
            when /^\d+$/
              cpu_string.to_i * 1000
            else
              0
            end
          end

          # Normalize byte string
          def normalize_bytes(bytes_string)
            case bytes_string.to_s
            when /Ki$/
              bytes_string.to_i * 1024
            when /K$/
              bytes_string.to_i * 1000
            when /Mi$/
              bytes_string.to_i * 1024 * 1024
            when /M$/
              bytes_string.to_i * 1000 * 1000
            when /Gi$/
              bytes_string.to_i * 1024 * 1024 * 1024
            when /G$/
              bytes_string.to_i * 1000 * 1000 * 1000
            else
              bytes_string.to_i
            end
          end
        end
      end
    end
  end
end
