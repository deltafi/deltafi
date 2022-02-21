# frozen_string_literal: true

module Deltafi
  module API
    module ServerSentEvents
      class Service
        attr_accessor :subscribers

        CHANNEL_PREFIX = 'sse'

        def initialize
          self.subscribers = []
          @redis = Deltafi::API.redis_client

          Thread.new do
            @redis.psubscribe("#{CHANNEL_PREFIX}.*") do |on|
              on.pmessage do |_match, channel, message|
                channel = channel.sub('sse.', '')

                subscribers.each do |conn|
                  conn << "event: #{channel}\n"
                  conn << "data: #{message}\n\n"
                end
              end
            end
          end
        end

        def publish(event_type, event_data)
          @redis.publish("#{CHANNEL_PREFIX}.#{event_type}", event_data)
        end
      end
    end
  end
end
