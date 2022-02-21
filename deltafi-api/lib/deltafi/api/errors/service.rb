# frozen_string_literal: true

module Deltafi
  module API
    module Errors
      class Service
        REDIS_CHANNEL = 'sse.errorCount'

        def initialize(sse_service, interval = 5)
          @sse_service = sse_service
          @redis = Deltafi::API.redis_client

          EM.next_tick do
            EM.add_periodic_timer(interval) do
              publish_error_count unless @sse_service.subscribers.empty?
            rescue StandardError => e
              puts "Error publishing error count! #{e}"
            end
          end
        end

        def publish_error_count
          response = Deltafi::API.graphql('query { deltaFiles (filter: {stage: ERROR, errorAcknowledged: false}) { totalCount } }')
          parsed_response = JSON.parse(response.body, symbolize_names: true)
          raise StandardError, parsed_response[:errors].first[:message] if parsed_response.key?(:errors)

          count = parsed_response.dig(:data, :deltaFiles, :totalCount)
          @redis.publish(REDIS_CHANNEL, count) unless count.nil?
        end
      end
    end
  end
end
