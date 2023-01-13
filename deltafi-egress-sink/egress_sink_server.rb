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

$LOAD_PATH.unshift File.expand_path(File.join(File.dirname(__FILE__), 'lib'))

require 'fileutils'
require 'json'
require 'sinatra/base'
require 'sinatra/quiet_logger'

require 'deltafi/logger'
require 'deltafi/smoke/service'

# Receive files from the RestPostEgressAction and drop them to disk
class EgressSinkServer < Sinatra::Base
  METADATA_HEADER = 'HTTP_DELTAFIMETADATA'
  METADATA_KEYS = %w[filename flow].freeze
  OUTPUT_PATH = '/data/deltafi/egress-sink'

  @@smoke_service = Deltafi::Smoke::Service.new

  configure do
    set :logger, Deltafi::Logger.new($stdout)
    enable :logging, :dump_errors
    set :raise_errors, true
    set :quiet_logger_prefixes, %w[probe]
  end

  register Sinatra::QuietLogger

  get('/probe') {}

  post '/' do
    json = request.env.find { |k, _| k.upcase == METADATA_HEADER }&.last
    raise "Missing metadata header \"#{METADATA_HEADER}\"" if json.nil?

    metadata = JSON.parse(json)
    METADATA_KEYS.each do |key|
      raise "Missing metadata key \"#{key}\"" unless metadata.key?(key)
    end

    if metadata['flow'].casecmp('SMOKE').zero?
      @@smoke_service.receive_smoke(metadata, request.body.read)
    else
      sink_file(json, metadata, request.body.read)
    end

    return 200
  end

  def sink_file(json, metadata, body)
    filename = metadata['filename'].gsub('/', '__')
    flow = metadata['flow']

    flow_path = File.join(OUTPUT_PATH, flow)

    FileUtils.mkdir_p(flow_path)
    File.write(File.join(flow_path, filename), body)
    File.write(File.join(flow_path, "#{filename}.metadata.json"), json)
  end

  run! if __FILE__ == $PROGRAM_NAME
end
