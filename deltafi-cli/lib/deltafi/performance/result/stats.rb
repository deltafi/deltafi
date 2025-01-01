#
#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
  module Performance
    module Result
      require 'time'
      class Stats
        def initialize
          @min = Float::MAX
          @max = -1
          @sum = 0
          @count = 0
        end

        def update_stats(start_time, end_time)
          start_parsed = Time.parse(start_time)
          end_parsed = Time.parse(end_time)
          duration = 1000 * (end_parsed.to_f - start_parsed.to_f)

          @max = [@max, duration].max
          @min = [@min, duration].min
          @sum += duration
          @count += 1
        end

        def mean
          @count.nonzero? ? @sum / @count : 0
        end
      end
    end
  end
end
