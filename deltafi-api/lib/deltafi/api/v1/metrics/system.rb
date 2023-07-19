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
            # this matches half the refresh rate of the nodemonitor.  We will be at most 9 seconds stale.
            REFRESH_GRAPHITE_CACHE_SECONDS = 4.5

            METRICS_QUERIES = [
              'keepLastValue(removeEmptySeries(seriesByTag(\'name=~gauge.node.*.*\')))'
            ].freeze

            @@cached_apps_by_node = nil
            @@cached_graphite_metrics = nil
            @@last_graphite_cache_time = Time.now

            def k8s_info
              {
                pods_by_node: DF.k8s_client.api('v1').resource('pods', namespace: 'deltafi').list(fieldSelector: { 'status.phase' => 'Running' }).group_by { |p| p.spec.nodeName.intern }
              }
            end

            def metrics
              if @@cached_graphite_metrics.nil? || Time.now - @@last_graphite_cache_time > REFRESH_GRAPHITE_CACHE_SECONDS
                @@last_graphite_cache_time = Time.now

                @@cached_graphite_metrics = DF::Metrics.graphite({
                                                                   target: METRICS_QUERIES,
                                                                   from: '-1min',
                                                                   until: 'now',
                                                                   format: 'json'
                                                                 })
              end

              @@cached_graphite_metrics
            end

            def nodes
              apps = apps_by_node
              metrics = metrics_by_node
              nodes = (metrics.keys + apps.keys).uniq

              nodes.map do |node|
                {
                  name: node,
                  resources: {
                    cpu: {
                      limit: metrics&.dig(node, :cpu, :limit) || 0,
                      usage: metrics&.dig(node, :cpu, :usage) || 0
                    },
                    memory: {
                      limit: metrics&.dig(node, :memory, :limit) || 0,
                      usage: metrics&.dig(node, :memory, :usage) || 0
                    },
                    disk: {
                      limit: metrics&.dig(node, :disk, :limit) || 0,
                      usage: metrics&.dig(node, :disk, :usage) || 0
                    }
                  },
                  apps: apps[node] || []
                }
              end
            end

            def metrics_by_node
              nodes = {}

              metrics.each do |metric|
                hostname = metric[:tags][:hostname].intern
                _, _, resouce, measurement = metric[:tags][:name].split('.').map(&:to_sym)
                nodes[hostname] ||= {}
                nodes[hostname][resouce] ||= {}
                nodes[hostname][resouce][measurement] = metric[:datapoints].reverse.find { |p| p[0].positive? }&.first

                raise "Invalid metric: #{metric}" if nodes[hostname][resouce][measurement].nil?
              end

              nodes
            end

            def apps_by_node_k8s
              # In Kubernetes mode
              pods_by_node = k8s_info[:pods_by_node]
              pods_by_node.transform_values do |pods|
                pods.map do |pod|
                  { name: pod.metadata.name }
                end
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
              nodes.find { |node| node[:apps].any? { |a| a[:name].include? 'minio' } }&.dig(:resources, :disk)
            end
          end
        end
      end
    end
  end
end
