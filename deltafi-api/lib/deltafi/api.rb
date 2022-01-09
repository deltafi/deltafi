# frozen_string_literal: true

require 'httparty'
require 'k8s-ruby'
require 'redis'

module Deltafi
  module API
    NAMESPACE = 'deltafi'

    def self.k8s_client
      ENV['RUNNING_IN_CLUSTER'].nil? ? K8s::Client.config(K8s::Config.load_file(File.expand_path('~/.kube/config'))) : K8s::Client.in_cluster_config
    end

    def self.graphql(query)
      properties = system_properties
      gateway_url = ENV['DELTAFI_GATEWAY_URL'] ||
                    properties['graphql.urls.gateway'] ||
                    'http://deltafi-gateway-service/graphql'

      HTTParty.post(gateway_url,
                    body: { query: query }.to_json,
                    headers: { 'Content-Type' => 'application/json' })
    end

    def self.elasticsearch(path, query)
      base_url = ENV['DELTAFI_ES_URL'] ||
                 'http://elasticsearch-master:9200'
      es_url = File.join(base_url, path)

      HTTParty.post(es_url,
                    body: query,
                    headers: { 'Content-Type' => 'application/json' })
    end

    def self.redis_client
      properties = system_properties
      redis_password = ENV['REDIS_PASSWORD']
      redis_url = ENV['REDIS_URL'] ||
                  properties['redis.url']&.gsub(/^http/, 'redis') ||
                  'http://deltafi-redis-master:6379'

      Redis.new(url: redis_url, password: redis_password)
    end

    def self.system_properties
      base_url = ENV['DELTAFI_CONFIG_URL'] || 'http://deltafi-config-server'
      config_url = File.join(base_url, 'application/default')
      properties = {}

      response = HTTParty.get(config_url,
                              headers: { 'Content-Type' => 'application/json' })

      response.parsed_response['propertySources'].reverse_each do |property_source|
        property_source['source'].each do |name, value|
          properties[name] = value.to_s.start_with?('$') ? ENV[value.gsub(/[${}]/, '')] : value
        end
      end

      return properties
    end
  end
end

Dir[File.join(File.dirname(__FILE__), 'api', '*.rb')].each do |f|
  require "deltafi/api/#{File.basename(f).split('.')[0]}"
end
