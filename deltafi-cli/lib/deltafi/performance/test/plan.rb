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
  module Performance
    module Test
      class Plan
        def initialize(yaml_hash)
          raise StandardError, 'The test plan duration must be set' unless yaml_hash['duration']

          raw_test_steps = yaml_hash['testSteps'] || []
          raise StandardError, 'The test plan must have one or more test steps' if raw_test_steps.empty?

          @duration = yaml_hash['duration'] / 1000.0

          domain = yaml_hash['deltafi_domain']
          unless domain.nil?
            graphql_url = "https://#{domain}/graphql-core-domain"
            ingress_url = "https://ingress.#{domain}/deltafile/ingress"
          end

          @test_steps = raw_test_steps.each_with_index.map { |test_step_yaml, idx| Step.new(test_step_yaml, @duration, idx, graphql_url, ingress_url) }
        end

        def run
          latch = Concurrent::CountDownLatch.new(@test_steps.length)
          @test_steps.each { |test_step| test_step.async.run(latch) }
          # wait for each TestStep to finish or timeout if we reach the total test duration
          latch.wait(@duration)

          Deltafi.log('${green}All test steps have been fired, waiting for all DeltaFiles to complete and gathering results${normal}')

          @test_results = @test_steps.map(&:complete_test)

          puts JSON.pretty_generate(@test_results)
        end
      end
    end
  end
end
