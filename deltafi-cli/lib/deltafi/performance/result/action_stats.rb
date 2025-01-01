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

require 'deltafi/performance/result/stats'

module Deltafi
  module Performance
    module Result
      class ActionStats < Stats
        attr_reader :name

        def initialize(name)
          super()
          @name = name
        end

        def to_hash
          { 'name' => @name, 'min' => @min, 'max' => @max, 'mean' => mean, 'sum' => @sum, 'count' => @count }
        end
      end
    end
  end
end
