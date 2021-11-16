# frozen_string_literal: true

require 'webrick'

module WEBrick
  # monkeypatch the webrick logger to produce json
  class Log
    def log(level, data)
      return unless @log && level <= @level

      @log << { timestamp: Time.now,
                level: level,
                loggerName: 'deltafi-egress-sink',
                hostName: `hostname`,
                message: data }.to_json + "\n"
    end
  end
end

require './egress_sink_server'
run EgressSinkServer
