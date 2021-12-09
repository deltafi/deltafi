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

  set :show_exceptions, :after_handler

  before do
    content_type 'application/json'
  end

  get '/api/v1/config' do
    config = { ui: Deltafi::API::Config::UI.config }
    build_response({ config: config })
  end

  post '/api/v1/errors/retry' do
    raise 'did required' unless params[:did]
    raise 'Provided did is not a valid UUID' unless valid_uuid?(params[:did])

    build_response({ retry: Deltafi::API::Errors.retry(params[:did]) })
  end

  get '/api/v1/metrics/system/nodes' do
    build_response({ nodes: Deltafi::API::Metrics::System.nodes })
  end

  get '/api/v1/metrics/queues' do
    build_response({ queues: Deltafi::API::Metrics::Action.queues })
  end

  get '/api/v1/metrics/action' do
    last = params[:last] || '5m'
    build_response({ actions: Deltafi::API::Metrics::Action.metrics_by_action_by_family(last: last) })
  end

  get '/api/v1/status' do
    build_response({ status: $status_service.status })
  end

  get '/api/v1/versions' do
    build_response({ versions: Deltafi::API::Versions.apps })
  end

  error StandardError do
    build_response({ error: env['sinatra.error'].message })
  end

  not_found do
    build_response({ error: 'API endpoint not found.' })
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
