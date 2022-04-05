# frozen_string_literal: true

module Deltafi
  module API
    module V1
      module ServerSentEvents
        class Service
          attr_accessor :subscribers

          def initialize
            self.subscribers = []
            @redis = DF.redis_client

            Thread.new do
              channel_prefix = DF::Common::SSE_REDIS_CHANNEL_PREFIX
              @redis.psubscribe("#{channel_prefix}.*") do |on|
                on.pmessage do |_match, channel, message|
                  channel = channel.sub("#{channel_prefix}.", '')

                  subscribers.each do |conn|
                    conn << "event: #{channel}\n"
                    conn << "data: #{message}\n\n"
                  end
                end
              end
            end
          end
        end
      end
    end
  end
end
