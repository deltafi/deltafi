ENV['APP_ENV'] = 'test'

require './auth.rb'
require 'rspec'
require 'rack/test'

RSpec.describe 'Auth' do
  include Rack::Test::Methods

  def app
    Auth
  end

  def auth_request(dn, url)
    get '/auth', params={}, rack_env={
      'HTTP_SSL_CLIENT_SUBJECT_DN' => dn,
      'HTTP_X_ORIGINAL_URL' => url
    }
  end

  def should_allow(dn, url)
    auth_request(dn, url)
    expect(last_response.status).to eq(200)
  end

  def should_deny(dn, url)
    auth_request(dn, url)
    expect(last_response.status).to eq(401)
    expect(last_response.body).to eq("Unauthorized request: \"#{dn}\" -> #{url}")
  end

  before(:example) do
    allow(app).to receive(:secret_yaml) do
      {
        'all'    => ['CN=TestUser1'],
        'nifi'   => ['CN=TestUser2'],
        'zipkin' => ['CN=TestUser3'],
        'kibana' => ['CN=TestUser2', 'CN=TestUser3']
      }.to_yaml
    end
  end

  describe "Service" do
    it "should return 400 if required request headers are not set" do
      get '/auth'
      expect(last_response.status).to eq(400)
      expect(last_response.body).to eq("Missing required headers")
    end

    it "should return 500 if Kubenetes Secret contains invalid YAML" do
      allow(app).to receive(:secret_yaml) { "[]-" }
      auth_request('CN=TestUser1', 'https://k8s.test.deltafi.org/')
      expect(last_response.status).to eq(500)
    end
  end

  describe "Users with 'all' access" do
    it "should be granted access to known subdomains" do
      should_allow('CN=TestUser1', 'https://nifi.test.deltafi.org/')
    end

    it "should be granted access to unknown subdomains" do
      should_allow('CN=TestUser1', 'https://k8s.test.deltafi.org/')
    end
  end

  describe "Users with partial access" do
    it "should be granted access to expected subdomains" do
      should_allow('CN=TestUser2', 'https://nifi.test.deltafi.org/')
      should_allow('CN=TestUser2', 'https://kibana.test.deltafi.org/')
      should_allow('CN=TestUser3', 'https://zipkin.test.deltafi.org/')
      should_allow('CN=TestUser3', 'https://kibana.test.deltafi.org/')
    end

    it "should be denied access to expected subdomains" do
      should_deny('CN=TestUser2', 'https://zipkin.test.deltafi.org/')
      should_deny('CN=TestUser3', 'https://nifi.test.deltafi.org/')
    end

    it "should be denied access to unknown subdomains" do
      should_deny('CN=TestUser2', 'https://k8s.test.deltafi.org/')
      should_deny('CN=TestUser3', 'https://k8s.test.deltafi.org/')
    end
  end

  describe "Users with no access" do
    it "should be denied access to known subdomains" do
      should_deny('CN=TestUser0', 'https://nifi.test.deltafi.org/')
    end

    it "should be denied access to unknown subdomains" do
      should_deny('CN=TestUser0', 'https://k8s.test.deltafi.org/')
    end  
  end
end
