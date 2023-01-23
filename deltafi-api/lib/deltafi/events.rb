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
  module Events
    extend self

    EVENT_API_URL = 'http://deltafi-api-service/api/v1/events'

    def self.generate(summary,
                      content: nil,
                      source: 'Monitor',
                      notification: false,
                      severity: 'info')

      # Map API severity label string to an API conforming string
      severity = case severity&.downcase
                 when 'error', 'failure', 'red'
                   'error'
                 when 'warn', 'warning', 'yellow'
                   'warn'
                 when 'success', 'successful', 'green'
                   'success'
                 else
                   'info'
                 end

      HTTParty.post(EVENT_API_URL,
                    body: {
                      summary: summary,
                      content: content,
                      source: source,
                      notification: notification,
                      severity: severity
                    }.to_json,
                    headers: {
                      'Content-Type' => 'application/json',
                      'X-User-Permissions' => 'Admin',
                      'X-User-Name' => 'Admin'
                    })
    end
  end
end
