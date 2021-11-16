# frozen_string_literal: true

$LOAD_PATH.unshift File.expand_path(File.join(File.dirname(__FILE__), 'lib'))

require 'fileutils'
require 'json'
require 'sinatra/base'

require 'deltafi/logger'
require 'deltafi/smoke/service'

# Receive files from the RestPostEgressAction and drop them to disk
class EgressSinkServer < Sinatra::Base
  METADATA_KEY = 'HTTP_DELTAFIMETADATA'
  OUTPUT_PATH = '/data/deltafi/egress-sink'

  @@smoke_service = Deltafi::Smoke::Service.new

  configure do
    set :logger, Deltafi::Logger.new(STDOUT)
    enable :logging, :dump_errors
    set :raise_errors, true
  end

  post '/' do
    json = request.env.find { |k, _| k.upcase == METADATA_KEY }&.last
    raise "Missing header #{METADATA_KEY}" if json.nil?

    metadata = JSON.parse(json)

    if metadata['flow'].casecmp('SMOKE').zero?
      @@smoke_service.receive_smoke(metadata, request.body.read)
    else
      sink_file(json, metadata, request.body.read)
    end
  end

  def sink_file(json, metadata, body)
    filename = metadata['filename']
    flow = metadata['flow']

    flow_path = File.join(OUTPUT_PATH, flow)

    FileUtils.mkdir_p(flow_path)
    File.write(File.join(flow_path, filename), body)
    File.write(File.join(flow_path, "#{filename}.metadata.json"), json)
  end

  run! if __FILE__ == $PROGRAM_NAME
end
