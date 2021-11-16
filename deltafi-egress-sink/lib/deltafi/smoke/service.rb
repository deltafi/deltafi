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

      GATEWAY_URL = 'http://deltafi-gateway-service/graphql'
      SMOKE_QUERY = '{"query":"{ deltaFiConfigs(configQuery: {configType: INGRESS_FLOW, name: \"smoke\"}) { name } }"}'
      INGRESS_URL = 'http://deltafi-ingress-service/deltafile/ingress'
      def initialize
        @smoke = {}
        @logger = Deltafi::Logger.new(STDOUT)
        @connected_to_gateway = true
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

      def smoke_installed?
        begin
          response = HTTParty.post(GATEWAY_URL, body: SMOKE_QUERY, headers: { 'Content-Type' => 'application/json' })
          @connected_to_gateway = true
        rescue StandardError => e
          @logger.error "Error connecting to gateway:\n#{e.message}\n#{e.backtrace}" if @connected_to_gateway
          @connected_to_gateway = false
          return false
        end
        return !(JSON.parse(response.body).dig('data', 'deltaFiConfigs') || []).empty?
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
        unless smoke_installed?
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
                                            'Flow' => 'smoke' })
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
