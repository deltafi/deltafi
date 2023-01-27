#
#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
#
#    Licensed under the Apache License, Version 2.0 (the "License");
#    you may not use this file except in compliance with the License.
#    You may obtain a copy of the License at
#
#        http://www.apache.org/licenses/LICENSE-2.0
#
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS,
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#    See the License for the specific language governing permissions and
#    limitations under the License.
#

# frozen_string_literal: true

require 'deltafi'
require 'timers'
require 'fileutils'
require 'memory_profiler'

Dir[File.join(File.dirname(__FILE__), 'monitor', '*.rb')].each do |f|
  require "deltafi/monitor/#{File.basename(f).split('.')[0]}"
end

module Deltafi
  module Monitor
    extend self
    extend Deltafi::Logger

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
        info "Forking process for #{service_class}"
        Process.setproctitle("monitor - child (#{service_class})")
        service = service_class.new
        timers = Timers::Group.new
        timers.now_and_every(service_class::INTERVAL) do
          if ENV['LOG_LEVEL'] == 'DEBUG'
            MemoryProfiler.start
            service.run
            MemoryProfiler.stop.pretty_print(to_file: "/tmp/#{service_class}.txt", scale_bytes: true)
            thread_count_by_status = Thread.list.group_by(&:status).map { |s, t| "#{s} => #{t.size}" }
            debug "#{service_class} thread count by status: #{thread_count_by_status.join(', ')}"
          else
            service.run
          end
        rescue StandardError => e
          error e
        end
        loop { timers.wait }
      end
    end
  end
end
