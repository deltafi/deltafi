# frozen_string_literal: true

require 'httparty'
require 'k8s-ruby'

module Deltafi
  module API
    NAMESPACE = 'deltafi'

    def self.k8s_client
      ENV['RUNNING_IN_CLUSTER'].nil? ? K8s::Client.config(K8s::Config.load_file(File.expand_path('~/.kube/config'))) : K8s::Client.in_cluster_config
    end

    def self.graphql(query)
      gateway_url = ENV['DELTAFI_GATEWAY_URL'] || 'http://deltafi-gateway-service/graphql'

      HTTParty.post(gateway_url,
                    body: { query: query }.to_json,
                    headers: { 'Content-Type' => 'application/json' })
    end
  end
end

Dir[File.join(File.dirname(__FILE__), 'api', '*.rb')].each do |f|
  require "deltafi/api/#{File.basename(f).split('.')[0]}"
end
