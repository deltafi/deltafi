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

require 'mongoid'

config = Deltafi.mongo_config

Mongoid.load_configuration(
  {
    clients: {
      default: {
        hosts: ["#{config[:host]}:#{config[:port]}"],
        database: config[:database],
        options: {
          user: config[:user],
          password: config[:password],
          auth_source: config[:auth_source]
        }
      }
    }
  }
)

Mongoid.raise_not_found_error = false

module BSON
  class ObjectId
    alias to_json to_s
    alias as_json to_s
  end
end

class Event
  include Mongoid::Document

  field :severity, type: String
  field :summary, type: String
  field :content, type: String
  field :source, type: String
  field :timestamp, type: DateTime, default: -> { Time.now.utc }
  field :notification, type: Boolean
  field :acknowledged, type: Boolean, default: false

  validates_presence_of :summary, :notification, :severity, :source
  validates_inclusion_of :severity, in: %w[info success warn error], message: 'must be info, success, warn, or error.'

  index({ timestamp: 1 })
end

Event.create_indexes