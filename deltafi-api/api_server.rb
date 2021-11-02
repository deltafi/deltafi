# frozen_string_literal: true

$LOAD_PATH.unshift File.expand_path(File.join(File.dirname(__FILE__), 'lib'))

require 'deltafi/api'
require 'sinatra/base'

STATUS_INTERVAL = 5 # seconds

$status_service = Deltafi::API::Status::Service.new(STATUS_INTERVAL)

class ApiServer < Sinatra::Base
  configure :production, :development, :test do
    enable :logging
  end

  before do
    content_type 'application/json'
  end

  get '/api/v1/config' do
    build_response({
                     config: {
                       system: Deltafi::API::Config::System.config
                     }
                   })
  end

  get '/api/v1/errors' do
    count = params[:count] || 10
    build_response({ errors: Deltafi::API::Errors.last_errored(count) })
  end

  post '/api/v1/errors/retry' do
    raise 'did required' unless params[:did]
    raise 'Provided did is not a valid UUID' unless valid_uuid?(params[:did])

    build_response({ retry: Deltafi::API::Errors.retry(params[:did]) })
  end

  get '/api/v1/metrics/system/nodes' do
    build_response({ nodes: Deltafi::API::Metrics::System.nodes })
  end

  get '/api/v1/status' do
    build_response({ status: $status_service.status })
  end

  def build_response(object)
    object[:timestamp] = Time.now
    object.to_json
  end

  def valid_uuid?(uuid)
    uuid_regex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/
    uuid_regex.match?(uuid.to_s.downcase)
  end

  run! if __FILE__ == $PROGRAM_NAME
end
