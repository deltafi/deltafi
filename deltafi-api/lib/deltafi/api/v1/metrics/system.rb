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

module Deltafi
  module API
    module V1
      module Metrics
        module System
          class << self
            def nodes
              nodes = DF.k8s_client.api('v1').resource('nodes').list
              node_usage = DF.k8s_client.api('metrics.k8s.io/v1beta1').resource('nodes').list
              pods = pods_by_node
              disks = disks_by_node

              nodes.map do |node|
                {
                  name: node.metadata.name,
                  resources: {
                    cpu: {
                      limit: normalize_cpu(node.status.capacity.cpu),
                      usage: normalize_cpu(node_usage.find { |n| n.metadata.name == node.metadata.name }&.usage&.cpu || 0)
                    },
                    memory: {
                      limit: normalize_bytes(node.status.capacity.memory),
                      usage: normalize_bytes(node_usage.find { |n| n.metadata.name == node.metadata.name }&.usage&.memory || 0)
                    },
                    disk: {
                      limit: disks[node.metadata.name]&.dig('limit') || 0,
                      usage: disks[node.metadata.name]&.dig('usage') || 0
                    }
                  },
                  pods: pods[node.metadata.name] || []
                }
              end
            end

            def disks_by_node
              usage_query = <<-QUERY
              keepLastValue(seriesByTag('name=gauge.node.disk.usage'), inf)
              QUERY

              limit_query = <<-QUERY
              keepLastValue(seriesByTag('name=gauge.node.disk.limit'), inf)
              QUERY

              results = DF::Metrics.graphite({
                                               target: [usage_query, limit_query],
                                               from: '-1min',
                                               until: 'now',
                                               format: 'json'
                                             })
              disks = {}

              results.each do |metric|
                hostname = metric[:tags][:hostname]
                measurement = metric[:tags][:name].sub('gauge.node.disk.', '')
                disks[hostname] ||= {}
                disks[hostname][measurement] = metric[:datapoints].last.first
              end

              disks
            end

            def pods_by_node
              pods_by_node = DF.k8s_client.api('v1').resource('pods').list(fieldSelector: { 'status.phase' => 'Running' }).group_by { |p| p.spec.nodeName }
              pod_usage = DF.k8s_client.api('metrics.k8s.io/v1beta1').resource('pods').list
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

            def content
              nodes.find { |node| node[:pods].any? { |p| p[:name].include? 'minio' } }&.dig(:resources, :disk)
            end

            private

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
end
