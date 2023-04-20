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
  module SystemProperties
    extend self
    extend Deltafi::Logger

    GRAPHQL_QUERY = <<-QUERY
      query {
        getDeltaFiProperties {
          systemName
          requeueSeconds
          coreServiceThreads
              scheduledServiceThreads
          delete {
            ageOffDays
            frequency
            policyBatchSize
          }
          ingress {
            enabled
            diskSpaceRequirementInMb
          }
          metrics {
            enabled
          }
          plugins {
            imageRepositoryBase
            imagePullSecret
          }
          checks {
            actionQueueSizeThreshold
            contentStoragePercentThreshold
          }
          ui {
            useUTC
            deltaFileLinks {
              name
              url
              description
            }
            externalLinks {
              name
              url
              description
            }
            topBar {
              textColor
              backgroundColor
            }
            securityBanner {
              enabled
              text
              textColor
              backgroundColor
            }
          }
          setProperties
        }
      }
    QUERY
    REFRESH_CACHE_SECONDS = 5
    TIMEOUT = 5

    @@last_cache_time = Time.at(0)
    @@system_properties = {}

    def self.all
      if @@system_properties.keys.empty? || Time.now - @@last_cache_time > REFRESH_CACHE_SECONDS
        begin
          debug 'Refreshing cache'
          @@last_cache_time = Time.now
          Timeout.timeout(TIMEOUT) do
            @@system_properties = DF.graphql(GRAPHQL_QUERY)&.dig('data', 'getDeltaFiProperties') || {}
          end
        rescue Timeout::Error
          error 'Timeout occurred while querying core.'
        rescue StandardError => e
          error e
        end
      end

      @@system_properties
    end

    def self.dig(dig_path = [], default_value = nil)
      all.dig(*dig_path) || default_value
    end
  end
end
