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
    module Test
      class Step
        include Concurrent::Async

        # Max time to wait for a future ingress result to return
        INGRESS_TIME_OUT_SECONDS = 5
        # Max time to wait for a DeltaFile to reach the COMPLETE stage
        DELTAFI_COMPLETION_TIMEOUT = 5
        MAX_RESULT_BATCH = 500
        DELTAFILES_QUERY = '"query ($limit: Int, $dids: [String!]){deltaFiles(limit: $limit filter: {dids: $dids}) { count totalCount offset deltaFiles { did stage totalBytes created modified childDids actions { name state created start stop modified errorCause }} }}"'

        def initialize(test_step_yaml, max_time, idx, graphql_url, ingress_url)
          super()
          idx += 1
          @dgs_client = Deltafi::Client::Graphql.new(graphql_url)
          @ingress_client = Deltafi::Client::Ingress.new(ingress_url)
          @id = SecureRandom.uuid
          @dids_future = Queue.new

          raise StandardError, "Test step #{idx} is missing the flow" unless test_step_yaml['flow']
          raise StandardError, "Test step #{idx} is missing the data section" unless test_step_yaml['data']

          @flow = test_step_yaml['flow']
          @rate = (test_step_yaml['rate'] || 1000) / 1000.0
          @concurrency = test_step_yaml['concurrency'] || 1
          @count = test_step_yaml['count'] || -1
          @filename = test_step_yaml['data']['filename']
          @input = test_step_yaml['data']['input']

          raise StandardError, "Test step #{idx} must have exactly one of data.filename or data.input must be set, both cannot be set" if @filename.nil? == @input.nil?
          raise StandardError, "Test step #{idx} references file: ${bold}#{File.basename(@filename)}${normal}${red} that cannot be read" unless @filename.nil? || File.readable?(@filename)

          @media_type = test_step_yaml['data']['mediaType'] || 'application/octet-stream'
          @max_time = max_time
          @test_result = Deltafi::Performance::Result::FlowStats.new(@flow, @id)
        end

        def run(test_plan_latch)
          execution_count = Concurrent::AtomicFixnum.new
          timer_task_latch = Concurrent::CountDownLatch.new(1)

          @timer_task = Concurrent::TimerTask.new(execution_interval: @rate, run_now: true) do |task|
            @concurrency.times do |_i|
              execution_count_value = execution_count.increment
              if @count != -1 && execution_count_value > @count
                task.shutdown
                timer_task_latch.count_down
                break
              else
                ingress_data
              end
            end
          end

          @timer_task.execute

          # block until the task is shutdown or the max_time has expired
          timer_task_latch.wait(@max_time)

          shutdown_test_step

          # acts as a notification to the TestPlan.run that this test step is complete
          test_plan_latch.count_down
        end

        def complete_test
          shutdown_test_step
          update_test_result
        end

        private

        def update_test_result
          until @dids_future.empty?
            raw_results = fetch_delta_files
            (raw_results || []).each { |delta_file| update_results_from_delta_file(delta_file) }
          end
          return @test_result.to_hash
        end

        def update_results_from_delta_file(delta_file)
          case delta_file[:stage]
          when 'COMPLETE'
            @test_result.update(delta_file)
          when 'ERROR'
            @test_result.add_warning("DeltaFile #{delta_file[:did]} had an error")
          else
            created_time = Time.parse(delta_file[:created]) if delta_file[:created]
            if created_time.nil? || Time.now.utc - created_time > DELTAFI_COMPLETION_TIMEOUT
              Deltafi.log("${red}Error: DeltaFile #{delta_file[:did]} did not complete within #{DELTAFI_COMPLETION_TIMEOUT} seconds and will not be included in the results${normal}")
              @test_result.add_warning("DeltaFile #{delta_file[:did]} did not complete within #{DELTAFI_COMPLETION_TIMEOUT} seconds")
            else
              Deltafi.log("${yellow}Warn: Adding incomplete DeltaFile back onto the queue: #{delta_file[:did]}${normal}")
              @dids_future << Concurrent::IVar.new(delta_file[:did])
            end
          end
        end

        def ingress_data
          @dids_future << @ingress_client.async.ingress_data(@flow, @filename, @input, @media_type, "-#{@id}")
        end

        def fetch_delta_files
          dids = []
          while !@dids_future.empty? && dids.length < MAX_RESULT_BATCH
            did_future = @dids_future.pop
            if did_future.complete?
              dids << did_future.value
            elsif did_future.pending?
              did = did_future.value(INGRESS_TIME_OUT_SECONDS)
              if !did
                Deltafi.log('${red}Error: Ingress timeout encountered${normal}')
              else
                dids << did
              end
            else
              Deltafi.log('${red}Error: Unexpected error encountered running ingress${normal}')
            end
          end
          variables = { 'dids' => dids, 'limit' => dids.length }
          @dgs_client.execute_request(DELTAFILES_QUERY, variables, %i[data deltaFiles deltaFiles])
        end

        def shutdown_test_step
          @timer_task.shutdown unless @timer_task.shutdown?
        end
      end
    end
  end
end
