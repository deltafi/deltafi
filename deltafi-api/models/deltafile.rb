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

class Deltafile
  include Mongoid::Document

  field :id, type: String
  field :requeueCount, type: Integer
  field :ingressBytes, type: Integer
  field :referencedBytes, type: Integer
  field :totalBytes, type: Integer
  field :stage, type: String
  field :sourceInfo, type: Hash
  field :egress, type: Array
  field :annotations, type: Hash
  field :annotationKeys, type: Array
  field :created, type: DateTime
  field :modified, type: DateTime
  field :egressed, type: Boolean
  field :filtered, type: Boolean
  field :terminal, type: Boolean

  scope :errors, -> { where(:stage.in => [ 'ERROR' ]) }

  store_in collection: 'deltaFile'

  # index({ timestamp: 1 })
end

# Deltafile.create_indexes
