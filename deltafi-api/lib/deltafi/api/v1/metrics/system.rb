#
#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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

require 'deltafi/memoist'

module Deltafi
  module API
    module V1
      module Metrics
        module System
          class << self
            extend Memoist

            def metrics
              keys = %w[gauge.node.memory.usage gauge.node.memory.limit gauge.node.disk.usage gauge.node.disk.limit gauge.node.cpu.usage gauge.node.cpu.limit]

              results = DF.valkey.pipelined do |pipeline|
                keys.each { |key| pipeline.hgetall(key) }
              end

              keys.zip(results).each_with_object({}) do |(key, value_hash), obj|
                resource_str, metric_str = key.split('.')[2..3]
                resource = resource_str.to_sym
                metric = metric_str.to_sym

                value_hash.each do |hostname_str, data_str|
                  hostname = hostname_str.to_sym
                  data = JSON.parse(data_str)

                  # ignore stale metrics
                  next if Time.now.to_i - data.last > 60

                  obj[hostname] ||= { name: hostname, resources: {} }
                  obj[hostname][:resources][resource] ||= {}
                  obj[hostname][:resources][resource][metric] = data.first
                end
              end
            end
            memoize :metrics, expires_in: 2

            def nodes
              apps = apps_by_node
              node_metrics = metrics
              nodes = (node_metrics.keys + apps.keys).uniq

              nodes.map do |node|
                {
                  name: node,
                  resources: node_metrics[node]&.dig(:resources),
                  apps: apps[node] || []
                }
              end
            end

            def apps_by_node_k8s
              running_pods = DF.k8s_client.api('v1').resource('pods', namespace: 'deltafi')
                               .list(fieldSelector: { 'status.phase' => 'Running' })

              ret = running_pods.each_with_object({}) do |pod, hash|
                node_name = pod.spec.nodeName.intern
                hash[node_name] ||= []
                hash[node_name] << { name: pod.metadata.name }
              end
              # this kicks the GC into gear
              running_pods = nil
              ret
            end

            def minio_node_k8s
              minio_pods = DF.k8s_client.api('v1').resource('pods', namespace: 'deltafi')
                             .list(labelSelector: { 'app' => 'minio' })

              minio_pods&.first&.spec&.nodeName&.to_s
            end

            def minio_node_standalone
              ENV['HOSTNAME'] || 'UNKNOWN'
            end

            def minio_node
              DF.cluster_mode? ? minio_node_k8s : minio_node_standalone
            end

            memoize :minio_node, expires_in: 60

            def apps_by_node_core
              DF.core_rest_get('appsByNode')
            end

            def apps_by_node
              DF.cluster_mode? ? apps_by_node_k8s : apps_by_node_core
            end

            memoize :apps_by_node, expires_in: 60

            def content
              all_metrics = metrics
              node = minio_node
              minio_disk_metrics = all_metrics[node.to_sym]&.dig(:resources, :disk)
              raise "Unable to get content storage metrics, received metrics #{all_metrics}, searching for node #{node}" unless minio_disk_metrics&.values&.all?(&:positive?)

              minio_disk_metrics
            end
          end
        end
      end
    end
  end
end
