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
      class FlowStats < Stats
        def initialize(flow, test_step_id)
          super()
          @test_step_id = test_step_id
          @flow = flow
          @bytes_processed = 0
          @children_created = 0
          @actions = []
          @warnings = []
        end

        def update(delta_file)
          @bytes_processed += delta_file[:totalBytes]
          @bytes_processed += delta_file[:childDids].length
          update_stats(delta_file[:created], delta_file[:modified])
          (delta_file[:actions] || []).each { |action_result| update_action(action_result) }
        end

        def add_warning(warning)
          @warnings << warning
        end

        def update_action(action_result)
          action_named = action_result[:name]
          action_stat = @actions.find { |action| action.name == action_named }
          unless action_stat
            action_stat = ActionStats.new(action_named)
            @actions << action_stat
          end

          action_stat.update_stats(action_result[:created], action_result[:modified])
        end

        def to_hash
          action_list = @actions.map(&:to_hash)
          { 'flow' => @flow, 'testStepId' => @test_step_id, 'bytes_processed' => @bytes_processed, 'children_created' => @children_created,
            'min' => @min, 'max' => @max, 'mean' => mean, 'sum' => @sum, 'count' => @count,
            'actions' => action_list, 'warnings' => @warnings }
        end
      end
    end
  end
end
