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

module Deltafi
  module Monitor
    # Monitor Service base class
    class Service
      include Deltafi::Logger

      private

      def periodic_timer(seconds)
        timer = Timers::Group.new
        timer.now_and_every(seconds) do
          yield
        rescue StandardError => e
          error e.message
          error e.backtrace.join("\n")
        end
        loop { timer.wait }
      end
    end
  end
end
