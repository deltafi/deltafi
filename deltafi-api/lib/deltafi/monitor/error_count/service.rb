# frozen_string_literal: true

module Deltafi
  module Monitor
    module ErrorCount
      class Service
        SSE_REDIS_CHANNEL = [DF::Common::SSE_REDIS_CHANNEL_PREFIX, 'errorCount'].compact.join('.')
        QUERY = 'query { deltaFiles (filter: {stage: ERROR, errorAcknowledged: false}) { totalCount } }'
        INTERVAL = 5

        def initialize
          @redis = DF.redis_client
        end

        def run
          response = DF.graphql(QUERY)
          parsed_response = JSON.parse(response.body, symbolize_names: true)
          raise StandardError, parsed_response[:errors].first[:message] if parsed_response.key?(:errors)

          count = parsed_response.dig(:data, :deltaFiles, :totalCount)
          @redis.publish(SSE_REDIS_CHANNEL, count) unless count.nil?
        end
      end
    end
  end
end
