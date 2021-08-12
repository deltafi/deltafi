# frozen_string_literal: true

require 'sinatra'
require 'yaml'
require 'k8s-ruby'

NAMESPACE = ENV['NAMESPACE']
SECRET = ENV['SECRET']
SUBDOMAIN_REGEX = %r{^https://([a-zA-Z0-9_]*)\.}.freeze

def allowed_map
  client = K8s::Client.in_cluster_config
  base64 = client.api('v1').resource('secrets', namespace: NAMESPACE).get(SECRET).data['allowed']
  yaml = Base64.strict_decode64(base64)
  YAML.safe_load(yaml)
end

def allowed?(client_dn, original_url)
  subdomain = SUBDOMAIN_REGEX.match(original_url).captures[0]
  allowed = allowed_map
  allowed_subdomain = allowed[subdomain] || []
  allowed_all = allowed['all'] || []
  (allowed_subdomain | allowed_all).include?(client_dn)
rescue StandardError => e
  logger.error e.message
  false
end

get '/auth' do
  logger.debug request.env

  client_dn = request.env['HTTP_SSL_CLIENT_SUBJECT_DN']
  original_url = request.env['HTTP_X_ORIGINAL_URL']

  unless allowed?(client_dn, original_url)
    logger.warn "Unauthorized DN: #{client_dn.inspect}"
    halt 401
  end
end
