# frozen_string_literal: true

require 'benchmark'

Dir[File.join(File.dirname(__FILE__), 'checks', '*.rb')].each do |f|
  require "deltafi/monitor/status/checks/#{File.basename(f).split('.')[0]}"
end

module Deltafi
  module Monitor
    module Status
      class Service
        attr_accessor :status, :checks

        INTERVAL = 5
        COLORS = %w[green yellow red].freeze
        STATES = %w[Healthy Degraded Unhealthy].freeze
        SSE_REDIS_CHANNEL = [DF::Common::SSE_REDIS_CHANNEL_PREFIX, 'status'].compact.join('.')

        def initialize
          self.status = {
            code: -1,
            state: 'Unknown',
            color: 'Unknown',
            checks: [],
            timestamp: Time.now
          }
          self.checks = Status::Checks.constants.map { |c| Status::Checks.const_get(c) }
        end

        def run
          run_checks
          publish_status
        end

        private

        def publish_status
          DF.redis_client.publish(SSE_REDIS_CHANNEL, status.to_json)
          DF.redis_client.set(DF::Common::STATUS_REDIS_KEY, status.to_json)
        end

        def run_checks
          results = checks.map do |check|
            Thread.new do
              Thread.current[:result] = check.new.run
            rescue StandardError => e
              puts "Error occurred while running #{check} check!"
              puts e.message, e.backtrace
              Thread.current[:result] = check.new.tap do |c|
                c.code = 2
                c.message_lines << 'Exception occurred while running check.'
                c.message_lines << "\n\t#{e.message}"
              end
            end
          end.map do |thread|
            thread.join
            thread[:result]
          end

          overall_code = results.map(&:code).max || 0

          self.status = {
            code: overall_code,
            color: COLORS[overall_code],
            state: STATES[overall_code],
            checks: results.sort_by { |r| [-r.code, r.name] }.map(&:to_hash),
            timestamp: Time.now
          }
        end
      end
    end
  end
end
