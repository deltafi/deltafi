# frozen_string_literal: true

$LOAD_PATH.unshift File.expand_path(File.join(File.dirname(__FILE__), 'lib'))

require 'deltafi/api'
require 'sinatra/base'

class ApiServer < Sinatra::Base
  configure :production, :development, :test do
    enable :logging
  end

  get '/v1/metrics/system/nodes' do
    {
      nodes: Deltafi::API::Metrics::System.nodes,
      timestamp: Time.now
    }.to_json
  end

  run! if __FILE__ == $0
end
