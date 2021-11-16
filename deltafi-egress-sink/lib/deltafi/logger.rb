# frozen_string_literal: true

require 'logger'

STDOUT.sync = true

module Deltafi
  # Produce JSON logs
  class Logger < ::Logger
    def initialize(logdev)
      formatter = proc do |level, time, _, msg|
        log = {}
        log[:timestamp] = time
        log[:level] = level
        log[:loggerName] = 'deltafi-egress-sink'
        log[:hostName] = `hostname`

        if msg.is_a? Hash
          log.merge!(msg)
        else
          log[:message] = msg
        end

        log.to_json + "\n"
      end

      super(logdev, formatter: formatter)
    end
  end
end
