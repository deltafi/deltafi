#
#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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
        rescue StandardError => e
          puts e
        end
        loop { timers.wait }
      end
    end
  end
end
