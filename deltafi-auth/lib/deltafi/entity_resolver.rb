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

require 'httparty'
require 'deltafi/logger'

module Deltafi
  module EntityResolver
    include Deltafi::Logger

    ER_ENABLED = ENV['ENTITY_RESOLVER_ENABLED']&.downcase == 'true'
    ER_URL = ENV['ENTITY_RESOLVER_URL'] || 'http://127.0.0.1:8080/'

    def resolve(identifier)
      return [identifier] unless ER_ENABLED

      response = HTTParty.post(ER_URL,
                               body: [identifier].to_json,
                               headers: { 'Content-Type' => 'application/json' })
      raise "#{response.code} error from entity resolver: #{response.message}" unless response.success?

      resolved_entities = response.parsed_response
      debug "Resolved \"#{identifier}\" to #{resolved_entities}"

      resolved_entities
    rescue StandardError => e
      error "Entity resolution failed: #{e.message}"
      [identifier]
    end
  end
end
