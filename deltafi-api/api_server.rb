# frozen_string_literal: true

$LOAD_PATH.unshift File.expand_path(File.join(File.dirname(__FILE__), 'lib'))

require 'deltafi/api'
require 'sinatra/base'
require 'sinatra/streaming'
require 'sinatra/quiet_logger'

STATUS_INTERVAL = (ENV['STATUS_INTERVAL'] || 5).to_i # seconds

$sse_service = Deltafi::API::ServerSentEvents::Service.new
$status_service = Deltafi::API::Status::Service.new(STATUS_INTERVAL)
$errors_service = Deltafi::API::Errors::Service.new($sse_service)

class ApiServer < Sinatra::Base
  helpers Sinatra::Streaming

  configure :production, :development, :test do
    enable :logging
    set :quiet_logger_prefixes, %w[probe]
  end

  register Sinatra::QuietLogger

  set :show_exceptions, :after_handler

  before do
    content_type 'application/json'
  end

  get('/probe') {}

  get '/api/v1/config' do
    config = { ui: Deltafi::API::Config::UI.config }
    build_response({ config: config })
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

  get '/api/v1/content' do
    stream_content(params)
  end

  post '/api/v1/content' do
    content_reference = JSON.parse(request.body.read, symbolize_names: true)
    stream_content(content_reference)
  rescue JSON::ParserError => e
    raise JSON::ParserError, "Failed to parse content reference: #{e.message}"
  end

  get '/api/v1/events' do
    content_type 'text/event-stream'
    headers 'Access-Control-Allow-Origin' => '*'
    stream(:keep_open) do |conn|
      $sse_service.subscribers << conn
      conn.callback { $sse_service.subscribers.delete(conn) }
      $status_service.publish_status
      $errors_service.publish_error_count
    end
  end

  error StandardError do
    build_response({ error: env['sinatra.error'].message })
  end

  not_found do
    build_response({ error: 'API endpoint not found.' })
  end

  def stream_content(content_reference)
    %i[did uuid size offset].each do |key|
      raise "Invalid content reference: #{key} required" unless content_reference[key]
    end

    head = Deltafi::API::Content.head(content_reference)

    filename = content_reference[:filename] || content_reference[:uuid]
    headers['Content-Disposition'] = "attachment; filename=#{filename};"
    headers['Content-Transfer-Encoding'] = 'binary'
    headers['Cache-Control'] = 'no-cache'
    headers['Content-Type'] = content_reference[:mediaType]
    headers['Content-Length'] = head.content_length.to_s

    stream do |out|
      Deltafi::API::Content.get(params) do |chunk|
        out.write(chunk)
      end
    end
  end

  def build_response(object)
    object[:timestamp] = Time.now
    object.to_json
  end

  run! if __FILE__ == $PROGRAM_NAME
end
