# frozen_string_literal: true

$LOAD_PATH.unshift File.expand_path(File.join(File.dirname(__FILE__), 'lib'))

require 'deltafi/api'
require 'sinatra/base'

class ApiServer < Sinatra::Base
  configure :production, :development, :test do
    enable :logging
  end

  get '/v1/metrics/nodes/?:node_name?' do
    Deltafi::API::Metrics.nodes(params[:node_name]).to_json
  end

  get '/v1/metrics/pods/?:pod_name?' do
    Deltafi::API::Metrics.pods(params[:pod_name]).to_json
  end

  run! if __FILE__ == $0
end
