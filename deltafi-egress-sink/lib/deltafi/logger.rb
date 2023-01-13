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

require 'logger'

$stdout.sync = true

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

        "#{log.to_json}\n"
      end

      super(logdev, formatter: formatter)
    end
  end
end
