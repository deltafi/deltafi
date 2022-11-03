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

require 'benchmark'
require 'httparty'
require 'securerandom'

require 'deltafi/logger'

module Deltafi
  module Smoke
    # Create and consume smoke files
    class Service
      SMOKE_INTERVAL_MS = 1000
      SMOKE_TIMEOUT_MS = 5000

      GRAPHQL_URL = 'http://deltafi-core-service/graphql'
      GET_FLOWS = '{"query":"{ getRunningFlows { ingress { name flowStatus { state } } enrich { name flowStatus { state } } egress { name flowStatus { state } } } }"}'
      INGRESS_URL = 'http://deltafi-ingress-service/deltafile/ingress'
      def initialize
        @smoke = {}
        @logger = Deltafi::Logger.new($stdout)
        @connected_to_graphql = true
        spawn_worker
      end

      def receive_smoke(metadata, data)
        smoke_entry = @smoke.delete(metadata['did'])
        if smoke_entry.nil?
          @logger.error({ did: metadata['did'],
                          filename: metadata['filename'],
                          message: 'received unexpected smoke file' })
        elsif metadata['filename'] != "smoke-#{data}"
          @logger.error({ did: metadata['did'],
                          filename: metadata['filename'],
                          message: "incorrect content: #{data}" })
        end
      end

      private

      def spawn_worker
        Thread.new do
          # startup delay, give sintra time to start listening
          sleep 1
          loop do
            begin
              time_spent = Benchmark.measure { run_smoke }.real
            rescue StandardError => e
              @logger.error "Error occurred while running smoke loop:\n#{e.message}\n#{e.backtrace}"
              time_spent = 0
            end
            sleep [(SMOKE_INTERVAL_MS / 1000.0 - time_spent), 1].max
          end
        end
      end

      def flow_running?(flows)
        return flows.find { |flow| flow['name'] == 'smoke' || flow['name'] == 'artificial-enrichment' }
      end

      def smoke_running?
        begin
          response = HTTParty.post(GRAPHQL_URL, body: GET_FLOWS, headers: { 'Content-Type' => 'application/json', 'X-User-Name' => 'egress-sink', 'X-User-Permissions' => 'FlowView'})
          @connected_to_graphql = true
        rescue StandardError => e
          @logger.error "Error connecting to graphql:\n#{e.message}\n#{e.backtrace}" if @connected_to_graphql
          @connected_to_graphql = false
          return false
        end
        flows = response.parsed_response.dig('data', 'getRunningFlows')
        return flow_running?(flows['ingress']) && flow_running?(flows['egress']) && flow_running?(flows['enrich'])
      end

      def timeout_smoke
        @smoke.select { |_, v| v[:sent] < Time.now - (SMOKE_TIMEOUT_MS / 1000.0) }.each do |did, v|
          @logger.error({ did: did,
                          filename: v[:filename],
                          message: "Did not receive smoke file sent at #{v[:sent]} within #{SMOKE_INTERVAL_MS} ms" })
          @smoke.delete(did)
        end
      end

      def run_smoke
        unless smoke_running?
          @smoke.clear
          return
        end

        timeout_smoke
        send_smoke
      end

      def send_smoke
        uuid = SecureRandom.uuid
        response = HTTParty.post(INGRESS_URL,
                                 body: uuid,
                                 headers: { 'Content-Type' => 'application/octet-stream',
                                            'Filename' => "smoke-#{uuid}",
                                            'Flow' => 'smoke',
                                            'X-User-Name' => 'egress-sink',
                                            'X-User-Permissions' => 'DeltaFileIngress'})
        if response.code == 200
          did = response.body
          @smoke[did] = { filename: uuid, sent: Time.now }
        else
          @logger.error "Failed to POST to #{INGRESS_URL}: #{response.code} #{response.body}"
        end
      end
    end
  end
end
