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

Dir[File.join(File.dirname(__FILE__), 'monitor', '*.rb')].each do |f|
  require "deltafi/monitor/#{File.basename(f).split('.')[0]}"
end

module Deltafi
  module Monitor
    extend self
    extend Deltafi::Logger

    SERVICES = DF::Monitor.constants.map do |c|
      mod = DF::Monitor.const_get(c)
      mod::Service if mod.constants.include?(:Service)
    end.compact

    def run
      Process.setproctitle('monitor - parent')

      pids = SERVICES.map { |service| fork_service(service) }

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
        begin
          service = service_class.new
          service.run
        rescue StandardError => e
          error e.message
          error e.backtrace.join("\n")
          retry
        end
      end
    end
  end
end
