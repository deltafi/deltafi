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

module Deltafi
  module API
    module V1
      module Metrics
        module System
          class << self
            REFRESH_APPS_BY_NODE_CACHE_SECONDS = 60
            REFRESH_REDIS_CACHE_SECONDS = 2

            METRICS_QUERIES = [
              'keepLastValue(removeEmptySeries(seriesByTag(\'name=~gauge.node.*.*\')))'
            ].freeze

            @@cached_apps_by_node = nil
            @@cached_redis_metrics = nil
            @@last_redis_cache_time = Time.now

            def metrics
              return @@cached_redis_metrics if defined?(@@cached_redis_metrics) && @@cached_redis_metrics && Time.now - @@last_redis_cache_time < REFRESH_REDIS_CACHE_SECONDS

              keys = %w[gauge.node.memory.usage gauge.node.memory.limit gauge.node.disk.usage gauge.node.disk.limit gauge.node.cpu.usage gauge.node.cpu.limit]

              results = DF.redis.pipelined do |pipeline|
                keys.each { |key| pipeline.hgetall(key) }
              end

              @@last_redis_cache_time = Time.now
              @@cached_redis_metrics = keys.zip(results).each_with_object({}) do |(key, value_hash), obj|
                resource_str, metric_str = key.split('.')[2..3]
                resource = resource_str.to_sym
                metric = metric_str.to_sym

                value_hash.each do |hostname_str, data_str|
                  hostname = hostname_str.to_sym
                  data = JSON.parse(data_str)

                  # ignore stale metrics
                  next if Time.now.to_i - data.last > 60

                  obj[hostname] ||= {name: hostname, resources: {}}
                  obj[hostname][:resources][resource] ||= {}
                  obj[hostname][:resources][resource][metric] = data.first
                end
              end
            end

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

              running_pods.each_with_object({}) do |pod, hash|
                node_name = pod.spec.nodeName.intern
                hash[node_name] ||= []
                hash[node_name] << { name: pod.metadata.name }
              end
            end

            def apps_by_node_core
              DF.core_rest_get('appsByNode')
            end

            def apps_by_node
              if @@cached_apps_by_node.nil? || Time.now - @@last_apps_by_node_cache_time > REFRESH_APPS_BY_NODE_CACHE_SECONDS
                @@last_apps_by_node_cache_time = Time.now
                @@cached_apps_by_node = DF.cluster_mode? ? apps_by_node_k8s : apps_by_node_core
              end

              @@cached_apps_by_node
            end

            def content
              minio_disk_metrics = nodes.find { |node| node[:apps].any? { |a| a[:name].start_with?('deltafi-minio') } }&.dig(:resources, :disk)
              raise "Unable to get content storage metrics!\n\n\t#{@@cached_redis_metrics}" unless minio_disk_metrics&.values&.all?(&:positive?)

              minio_disk_metrics
            end
          end
        end
      end
    end
  end
end
