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
require 'deltafi/logger'
require 'click_house'
require 'json'

module Deltafi
  module API
    module V1
      module Survey
        extend Deltafi::Logger
        class << self
          Survey = Struct.new(:timestamp, :update_timestamp, :flow, :files, :ingress_bytes, :errored, :filtered, :annotations) do
            def self.create(update_timestamp, flow:, timestamp: Time.now.utc, files: 1, ingress_bytes: 0, errored: 0, filtered: 0, **args)
              timestamp = Time.parse(timestamp) if timestamp.is_a?(String)
              %i[update_timestamp annotations].each { |k| args.delete(k) }
              annotations = args.empty? ? nil : args
              new timestamp.is_a?(String) ? Time.parse(timestamp) : timestamp,
                  update_timestamp.is_a?(String) ? Time.parse(update_timestamp) : update_timestamp,
                  flow.to_s,
                  files.to_i,
                  ingress_bytes.to_i,
                  errored.to_i,
                  filtered.to_i,
                  annotations
            end
          end

          def clickhouse_client
            @clickhouse_client ||= DF.clickhouse_client
          end

          def add_survey(blob)
            now = Time.now.utc
            random_number = rand(1_000_000)
            error('Invalid JSON') unless blob.is_a?(Array)
            blob = [blob] if blob.is_a?(Hash)

            errors = []

            clickhouse_client.insert(Deltafi::ClickhouseETL::DELTAFILE_TABLE_NAME, columns: %i[did timestamp update_timestamp flow files ingressBytes totalBytes errored filtered annotations]) do |insert_buffer|
              blob.each_with_index do |survey, index|
                error_count = errors.length
                errors << { error: "Flow missing at #{index}", source: survey.to_json.to_s } unless survey.key?(:flow)
                errors << { error: "Invalid file count at #{index}", source: survey.to_json.to_s } if survey[:files].to_i < 1
                next if errors.length > error_count

                s = Survey.create(now, **survey)

                insert_buffer << [
                  "survey-#{index}-#{random_number}-#{s.update_timestamp.strftime('%s%N').to_i}",
                  s.timestamp.to_i,
                  s.update_timestamp.to_i,
                  s.flow,
                  s.files,
                  s.ingress_bytes,
                  s.ingress_bytes,
                  s.errored,
                  s.filtered,
                  s.annotations
                ]
              end

              raise StandardError, errors.to_json if errors.any?
            end
          end
        end
      end
    end
  end
end
