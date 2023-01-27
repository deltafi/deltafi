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

require 'base64'
require 'deltafi'
require 'sinatra/base'
require 'sinatra/streaming'
require 'sinatra/quiet_logger'
require 'sinatra/namespace'

$sse_service = DF::API::V1::ServerSentEvents::Service.new unless ENV['RUNNING_IN_CLUSTER'].nil?

class ApiServer < Sinatra::Base
  %w[helpers models routes].each { |dir| Dir.glob("./#{dir}/*.rb").sort.each(&method(:require)) }

  helpers Sinatra::Streaming

  configure :production, :development, :test do
    enable :logging
    set :quiet_logger_prefixes, %w[probe]
  end

  register Sinatra::QuietLogger
  register Sinatra::Namespace

  set :show_exceptions, :after_handler

  before do
    content_type 'application/json'
  end

  get('/probe') { return }

  namespace '/api/v1' do
    get '/config' do
      authorize! :UIAccess

      config = { ui: DF::API::V1::Config::UI.config }
      build_response({ config: config })
    end

    get '/metrics/system/content' do
      authorize! :MetricsView

      build_response({ content: DF::API::V1::Metrics::System.content })
    end

    get '/metrics/system/nodes' do
      authorize! :MetricsView

      build_response({ nodes: DF::API::V1::Metrics::System.nodes })
    end

    get '/metrics/queues' do
      authorize! :MetricsView

      build_response({ queues: DF::API::V1::Metrics::Action.queues })
    end

    get '/metrics/action' do
      authorize! :MetricsView

      last = params[:last] || '5m'
      flow = params[:flowName]
      build_response({ actions: DF::API::V1::Metrics::Action.metrics_by_action_by_family(last: last, flow: flow) })
    end

    get '/metrics/flow(.json)?' do
      authorize! :MetricsView

      build_response({ flow_report: DF::API::V1::Metrics::Flow.summary(params: params) })
    end

    get '/metrics/flow.csv' do
      authorize! :MetricsView

      content_type 'text/csv'
      DF::API::V1::Metrics::Flow.summary_csv(params: params)
    end

    get '/metrics/graphite' do
      authorize! :MetricsView

      DF::Metrics.graphite(params, raw: true)
    end

    get '/status' do
      authorize! :StatusView

      build_response({ status: DF::API::V1::Status.status })
    end

    get '/versions' do
      authorize! :VersionsView

      build_response({ versions: DF::API::V1::Versions.apps })
    end

    get '/content' do
      authorize! :DeltaFileContentView

      raise 'Missing required parameter: reference' unless params[:reference]

      content_reference_json = Base64.strict_decode64(params[:reference]).encode('UTF-8', invalid: :replace)
      content_reference = JSON.parse(content_reference_json, symbolize_names: true)
      stream_content(content_reference)
    rescue ArgumentError, Encoding::UndefinedConversionError, JSON::ParserError => e
      raise "Failed to parse content reference: #{e.message}"
    end

    post '/content' do
      authorize! :DeltaFileContentView

      content_reference = JSON.parse(request.body.read, symbolize_names: true)
      stream_content(content_reference)
    rescue JSON::ParserError => e
      raise JSON::ParserError, "Failed to parse content reference: #{e.message}"
    end

    get '/sse' do
      authorize! :UIAccess

      content_type 'text/event-stream'
      headers 'Access-Control-Allow-Origin' => '*'
      stream(:keep_open) do |conn|
        $sse_service.subscribers << conn
        conn.callback { $sse_service.subscribers.delete(conn) }
      end
    end
  end

  error Sinatra::NotFound do
    build_error_response('404 Not Found')
  end

  error Deltafi::AuthError do
    permission = env['sinatra.error'].permission
    req = "#{request.request_method} #{request.env['PATH_INFO']}"
    audit("request '#{req}' was denied due to missing permission '#{permission}'")
    build_error_response(env['sinatra.error'].message)
  end

  error StandardError do
    build_error_response(env['sinatra.error'].message)
  end

  def stream_content(content_reference)
    DF::API::V1::Content.verify_content_reference(content_reference)

    size = [
      DF::API::V1::Content.content_reference_size(content_reference),
      content_reference[:size]
    ].min

    filename = content_reference[:filename] || content_reference[:uuid]
    headers['Content-Disposition'] = "attachment; filename=#{filename};"
    headers['Content-Transfer-Encoding'] = 'binary'
    headers['Cache-Control'] = 'no-cache'
    headers['Content-Type'] = content_reference[:mediaType]
    headers['Content-Length'] = size.to_s

    bytes_left = content_reference[:size]

    stream do |out|
      content_reference[:segments].each do |segment|
        break unless bytes_left.positive?

        audit("viewed content for DID #{segment[:did]}")
        segment[:size] = [segment[:size], bytes_left].min
        DF::API::V1::Content.get_segment(segment) do |chunk|
          out.write(chunk)
        end
        bytes_left -= segment[:size]
      end
    end
  end

  def build_response(object)
    object[:timestamp] = Time.now
    object.to_json
  end

  def build_error_response(message)
    build_response({ error: message })
  end

  run! if __FILE__ == $PROGRAM_NAME
end
