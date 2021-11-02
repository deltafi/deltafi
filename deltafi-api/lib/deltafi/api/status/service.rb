# frozen_string_literal: true

require 'benchmark'

Dir[File.join(File.dirname(__FILE__), 'checks', '*.rb')].each do |f|
  require "deltafi/api/status/checks/#{File.basename(f).split('.')[0]}"
end

module Deltafi
  module API
    module Status
      class Service
        attr_accessor :status, :interval, :checks

        COLORS = %w[green yellow red].freeze
        STATES = %w[Healthy Degraded Unhealthy].freeze

        def initialize(interval = 5)
          self.status = {
            code: -1,
            state: 'Unknown',
            color: 'Unknown',
            checks: []
          }
          self.interval = interval
          self.checks = Status::Checks.constants.map { |c| Status::Checks.const_get(c) }
          spawn_worker
        end

        private

        def spawn_worker
          Thread.new do
            loop do
              begin
                time_spent = Benchmark.measure { run_checks }.real
              rescue StandardError => e
                puts 'Error occurred while running checks!'
                puts e.message, e.backtrace
              end
              sleep [(interval - time_spent), 1].max
            end
          end
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
            checks: results.sort_by(&:code).reverse.map(&:to_hash)
          }
        end
      end
    end
  end
end
