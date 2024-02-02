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
require 'date'
require 'time'
require 'csv'
require 'ap'

module Deltafi
  module API
    module V1
      module Metrics
        module Flow
          class << self
            def summary(params:)
              egress_filter = params[:egressFlows] ? ", 'egressFlow=~(#{params[:egressFlows].join('|')})'" : ''

              ingress = <<-QUERY
              summarize(groupByTags(seriesByTag('name=~stats_counts.(bytes_in|files_in)', 'action=ingress'), "sum", "name", "dataSource"), '5y', 'sum')
              QUERY
              egress = <<-QUERY
              summarize(groupByTags(seriesByTag('name=~stats_counts.(bytes_out|files_out|files_errored|files_filtered)'#{egress_filter}), "sum", "name", "dataSource"), '5y', 'sum')
              QUERY

              # Offset now to prevent ragged edge of metrics
              now = (DateTime.now.to_time - 20).to_datetime
              today = Date.today

              range = case params[:range]
                      when 'last-week'
                        { from: (today - today.wday - 7).to_datetime,
                          until: (today - today.wday).to_datetime }
                      when 'this-week'
                        { from: (today - today.wday).to_datetime,
                          until: now }
                      when 'last-month'
                        { from: (today.prev_month - today.prev_month.mday + 1).to_datetime,
                          until: (today - today.mday + 1).to_datetime }
                      when 'this-month'
                        { from: (today - today.mday + 1).to_datetime,
                          until: now }
                      when 'today'
                        { from: today.to_datetime,
                          until: now }
                      else
                        from = now - 1
                        to = now

                        from = DateTime.parse(params[:from]) if params[:from]
                        if params[:until]
                          to = if params[:until] == 'now'
                                 now
                               else
                                 DateTime.parse(params[:until])
                               end
                        end

                        { from: from, until: to }
                      end

              queries = [ingress, egress]
              from = range[:from].strftime('%H:%M_%Y%m%d')
              to = range[:until].strftime('%H:%M_%Y%m%d')

              results = DF::Metrics.graphite({
                                               target: queries,
                                               from: from,
                                               until: to,
                                               maxDataPoints: '99999',
                                               format: 'json'
                                             })

              output = Hash.new { |hash, key| hash[key] = hash.dup.clear }
              results.each do |metric|
                metric_name = metric[:tags][:name].gsub('stats_counts.', '')
                ingress_flow = metric[:tags][:ingressFlow]
                count = metric[:datapoints].reduce(0) { |val, point| val + (point.first || 0) }
                output[ingress_flow][:metrics][metric_name] = count
              end

              params[:aggregate]&.each do |agg_name, agg_flows|
                flows = agg_flows.split(',')
                output[agg_name][:metrics] = flows.map { |flow| output[flow][:metrics] }.reduce({}) { |sum, single| sum.merge(single) { |_k, a, b| a + b } }
                output[agg_name][:aggregate] = flows
              end

              { time_range: range, configuration: params, flows: output }
            end

            def summary_csv(params:)
              report = summary(params: params)
              metrics = report[:flows].values.reduce(Set.new) { |s, flow| s.merge(flow[:metrics].keys) }.to_a
              fields = ['flow'].concat(metrics).append 'aggregate'
              flows = report[:flows].keys

              CSV.generate do |csv|
                csv << fields
                flows.each do |flow|
                  row = [flow]
                  row += metrics.reduce([]) do |a, metric|
                    if report[:flows][flow][:metrics].key? metric
                      a.append(report[:flows][flow][:metrics][metric].to_i)
                    else
                      a.append(0)
                    end
                  end
                  row << (report[:flows][flow].key?(:aggregate) ? report[:flows][flow][:aggregate].join(' ') : nil)
                  csv << row
                end
              end
            end
          end
        end
      end
    end
  end
end
