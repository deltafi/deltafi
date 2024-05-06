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

require 'yaml'

module Deltafi
  module API
    module V1
      module Metrics
        module Action
          class << self
            DGS_QUEUE_NAME = 'dgs'

            def queues
              queue_json = DF.valkey.get(DF::Common::ACTION_QUEUE_SIZES_VALKEY_KEY)

              if queue_json.nil?
                return []
              else
                parsed_queues = JSON.parse(queue_json)
              end

              time = parsed_queues['time']
              parsed_queues['queues'].map do |k, v|
                {
                  name: k,
                  size: v,
                  timestamp: time
                }
              end
            end

            def recent_queues
              queues = DF.valkey.hgetall(DF::Common::ACTION_HEARTBEAT_VALKEY_KEY)
              queues.select { |_, v| Time.now - Time.parse(v) < DF::Common::ACTION_HEARTBEAT_THRESHOLD }
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
          end
        end
      end
    end
  end
end
