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

require 'yaml'

module Deltafi
  module API
    module V1
      module Metrics
        module Action
          class << self
            DGS_QUEUE_NAME = 'dgs'

            def queues
              query = <<-QUERY
              sortBy(aliasByTags(groupByTags(seriesByTag('name=gauge.action_queue.queue_size'), 'last', 'queue_name'), 'queue_name'), 'max', true)
              QUERY

              results = DF::Metrics.graphite({
                                               target: query,
                                               from: '-20sec',
                                               until: 'now',
                                               format: 'json'
                                             })

              queue_names = [ DGS_QUEUE_NAME ] + action_names
              queue_list = []

              results.each do |metric|
                queue_list << {
                  name: metric[:target],
                  size: metric[:datapoints].map(&:first).compact.last, # Use the oldest non-null datapoint for the gauge value
                  timestamp: metric[:datapoints].last.last.to_i * 1000
                } if queue_names.include?(metric[:target])
              end

              queue_list
            end

            def metrics_by_action_by_family(flow:, last: '5min')
              # TECH DEBT: Supporting legacy times like '5m', '3h', '14d'
              last += 'in' if /^\d*m$/.match?(last)
              last += 'our' if /^\d*h$/.match?(last)
              last += 'ay' if /^\d*d$/.match?(last)

              query = <<-QUERY
              smartSummarize(groupByTags(seriesByTag('name=~stats_counts.(files_filtered|files_errored|files_in|bytes_out)'#{", 'ingressFlow=#{flow}'" if flow}), "sum", "name", "source", "action"), "#{last}")
              QUERY

              results = DF::Metrics.graphite({
                                               target: query,
                                               from: "-#{last}",
                                               until: 'now',
                                               format: 'json'
                                             })
              transform = Hash.new { |hash, key| hash[key] = hash.dup.clear }

              results.each do |metric|
                tags = metric[:tags]
                transform[tags[:action]][tags[:source]][tags[:name].sub(/stats_counts./, '')] = metric[:datapoints].first.first || 0
              end

              transform
            end

            def action_names_by_family
              action_families = DF.graphql('query { getActionNamesByFamily { family actionNames } }')
                                  .parsed_response['data']['getActionNamesByFamily']

              output = Hash.new { |hash, key| hash[key] = hash.dup.clear }

              action_families.each_entry do |family_entry|
                family = family_entry['family']
                family_entry['actionNames'].each do |action_name|
                  output[family][action_name] = {}
                end
              end

              output
            end

            def action_names
              actions = DF.graphql('query { actionDescriptors { name } }')
                          .parsed_response['data']['actionDescriptors']

              actions.map { |action| action['name'] }.sort
            end
          end
        end
      end
    end
  end
end
