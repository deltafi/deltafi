require 'sinatra'
require 'yaml'
require 'k8s-ruby'

NAMESPACE = ENV['NAMESPACE']
SECRET = ENV['SECRET']

def allowed
  client = K8s::Client.in_cluster_config
  base64 = client.api('v1').resource('secrets', namespace: NAMESPACE).get(SECRET).data["allowed"]
  yaml = Base64.strict_decode64(base64)
  YAML.load(yaml)
rescue => e
  puts e.message
  []
end

get '/auth' do
  client_dn = request.env["HTTP_SSL_CLIENT_SUBJECT_DN"]
  halt 401 unless allowed.include? client_dn
end
