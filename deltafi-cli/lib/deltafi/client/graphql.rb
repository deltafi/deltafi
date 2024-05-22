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

require 'shellwords'

module Deltafi
  module Client
    class Graphql
      include Concurrent::Async

      GRAPHQL_QUERY = '{"query": %<query_string>s, "variables": %<variable_json>s}'

      def initialize(graphql_url = nil)
        @graphql_url = graphql_url || generate_url
      end

      def generate_url
        dgs_ip = `deltafi serviceip deltafi-core-service`
        "http://#{dgs_ip.chomp}/api/v2/graphql"
      end

      def execute_request(query_string, variables = '{}', dig_path = [:data])
        query = format(GRAPHQL_QUERY, query_string: query_string, variable_json: JSON[variables])

        response = DeltafiClient.post(@graphql_url,
                                      body: query,
                                      headers: { 'Content-Type' => 'application/json' })

        raise StandardError, "Executing #{Shellwords.escape(query)} resulted in status code: #{response.code}, message: #{response.body}" unless response.code == 200

        parsed_response = JSON.parse(response.body, symbolize_names: true)
        raise StandardError, parsed_response[:errors].first[:message] if parsed_response.key?(:errors)

        parsed_response.dig(*dig_path)
      end
    end
  end
end
