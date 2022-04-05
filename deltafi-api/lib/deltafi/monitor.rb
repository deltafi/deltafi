# frozen_string_literal: true

require 'deltafi'
require 'timers'
require 'fileutils'

Dir[File.join(File.dirname(__FILE__), 'monitor', '*.rb')].each do |f|
  require "deltafi/monitor/#{File.basename(f).split('.')[0]}"
end

module Deltafi
  module Monitor
    extend self

    MODULES = DF::Monitor.constants.map { |c| DF::Monitor.const_get(c) }

    def run
      Process.setproctitle('monitor - parent')

      pids = MODULES.map { |mod| fork_service(mod::Service) }

      %w[INT TERM].each do |signal|
        trap(signal) do
          puts "Got SIG#{signal}. Cleaning up child processes (#{pids.join(', ')})"
          pids.each { |pid| Process.kill(signal, pid) }
        end
      end

      Process.waitall
    end

    def fork_service(service_class)
      fork do
        Process.setproctitle("monitor - child (#{service_class})")
        service = service_class.new
        timers = Timers::Group.new
        timers.now_and_every(service_class::INTERVAL) do
          service.run
          touch_probe_file(service_class.name)
        rescue StandardError => e
          puts e
        end
        loop { timers.wait }
      end
    end

    def touch_probe_file(filename)
      FileUtils.mkdir_p(ENV['PROBE_DIR']) if ENV['PROBE_DIR']
      target = File.join(ENV['PROBE_DIR'], filename)
      FileUtils.touch(target)
    end
  end
end
