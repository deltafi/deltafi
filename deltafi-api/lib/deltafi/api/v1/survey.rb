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

module Deltafi
  module API
    module V1
      module Survey
        extend Deltafi::Logger

        def self.clickhouse_client
          @clickhouse_client ||= DF::Clickhouse.client
        end

        def self.add_survey(blob)
          now = Time.now.utc
          formatted_now = now.strftime('%s%N').to_i
          blob = [blob] if blob.is_a?(Hash)
          raise InvalidArgumentError, "Received Invalid JSON #{blob}" unless blob.is_a?(Array)

          errors = []

          clickhouse_client.insert(Deltafi::ClickhouseETL::DELTAFILE_TABLE_NAME, columns: %i[did timestamp update_timestamp flow files ingressBytes totalBytes errored filtered annotations]) do |insert_buffer|
            blob.each_with_index do |survey, index|
              unless survey.key?(:flow) && survey[:files].to_i >= 1
                errors << { error: "Invalid survey data at #{index}", source: survey.to_json.to_s }
                next
              end

              survey_data = [
                "survey-#{index}-#{formatted_now}",
                survey[:timestamp] ? Time.parse(survey[:timestamp]).to_i : now.to_i,
                survey[:update_timestamp] ? Time.parse(survey[:update_timestamp]).to_i : now.to_i,
                survey[:flow].to_s,
                survey[:files].to_i,
                survey[:ingress_bytes].to_i,
                survey[:ingress_bytes].to_i,
                survey[:errored].to_i,
                survey[:filtered].to_i,
                survey.except(:timestamp, :update_timestamp, :flow, :files, :ingress_bytes, :total_bytes, :errored, :filtered, :annotations)
              ]

              insert_buffer << survey_data
            end

            raise StandardError, errors.to_json if errors.any?
          end
        end
      end
    end
  end
end
