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

require 'httparty'
require 'graphite-api'
require 'json'

module Deltafi
  module Metrics
    extend self

    def self.graphite(query, raw: false)
      graphite_url = "#{ENV['DELTAFI_GRAPHITE_URL'] || 'http://deltafi-graphite:8080'}/render"

      if raw
        HTTParty.post(graphite_url, query: query).to_s
      else
        begin
          JSON.parse(HTTParty.post(graphite_url,
                                   query: query).to_s,
                     symbolize_names: true)
        rescue JSON::ParserError => e
          raise JSON::ParserError, "Failed to parse graphite response: #{e.message}"
        end
      end
    end

    GRAPHITE_METRIC_URL = ENV['DELTAFI_GRAPHITE_METRIC_URL'] || 'tcp://deltafi-graphite:2003'
    @@graphite_client = GraphiteAPI.new graphite: GRAPHITE_METRIC_URL, slice: 10

    def self.record_metric(name:, value:, tags: {}, prefix: nil, gauge: false)
      metric_name = ([ "#{"#{prefix}." if prefix}#{name}" ] + tags.map { |key, val| "#{key}=#{val}" }).join(';')
      @@graphite_client.metrics({ metric_name => value }, Time.now.to_i, gauge ? :replace : :sum)
    end
  end
end
