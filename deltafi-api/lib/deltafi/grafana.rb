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

require 'httparty'
require 'json'

module Deltafi
  # Grafana API client
  module Grafana
    extend self

    GRAFANA_URL = ENV['DELTAFI_GRAFANA_URL'] || 'http://deltafi-grafana'

    ALERT_API = "#{GRAFANA_URL}/api/alertmanager/grafana/api/v2/alerts?active=true&silenced=false&inhibited=false"

    OPTIONS = {
      headers: {
        'X-Metrics-Role' => 'Admin',
        'X-User-Name' => 'admin'
      }
    }.freeze

    def self.alerts(raw: false)
      if raw
        HTTParty.get(ALERT_API, OPTIONS).to_s
      else
        begin
          JSON.parse(HTTParty.get(ALERT_API, OPTIONS).to_s,
                     symbolize_names: true)
        rescue JSON::ParserError => e
          raise JSON::ParserError, "Failed to parse Grafana alert response: #{e.message}"
        end
      end
    end
  end
end
