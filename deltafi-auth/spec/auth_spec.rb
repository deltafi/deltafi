#
#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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

ENV['RACK_ENV'] = 'test'
ENV['DOMAIN'] = 'test.deltafi.org'

require './auth_api'
require './models/user'
require 'rspec'
require 'rack/test'

USERNAME = 'frank'
PASSWORD = 'P@ssw0rd'
ADMIN_PERMISSIONS = { 'HTTP_X_USER_PERMISSIONS' => 'Admin' }.freeze

RSpec.describe 'Auth API' do
  include Rack::Test::Methods

  def app
    AuthApi
  end

  def cert_auth_request(dn, url)
    get '/cert-auth', {}, {
      'HTTP_SSL_CLIENT_SUBJECT_DN' => dn,
      'HTTP_X_ORIGINAL_URL' => url
    }
  end

  def basic_auth_request(username, password, url)
    authorize username, password
    get '/basic-auth', {}, {
      'HTTP_X_ORIGINAL_URL' => url
    }
  end

  def should_allow(dn, url)
    cert_auth_request(dn, url)
    expect(last_response.status).to eq(200)
    expect(last_response.headers.key?('X-User-ID')).to be(true)
    expect(last_response.headers.key?('X-User-Name')).to be(true)
    expect(last_response.headers.key?('X-User-Permissions')).to be(true)
  end

  def should_deny(dn, url, code)
    cert_auth_request(dn, url)
    expect(last_response.status).to eq(code)
  end

  def create_user(params)
    post '/users', params.to_json, ADMIN_PERMISSIONS
    JSON.parse(last_response.body, symbolize_names: true)
  end

  def read_user(id:)
    get "/users/#{id}", nil, ADMIN_PERMISSIONS
    JSON.parse(last_response.body, symbolize_names: true)
  end

  def update_user(id:, updates:)
    put "/users/#{id}", updates.to_json, ADMIN_PERMISSIONS
    JSON.parse(last_response.body, symbolize_names: true)
  end

  def delete_user(id:)
    delete "/users/#{id}", nil, ADMIN_PERMISSIONS
  end

  def create_role(params)
    post '/roles', params.to_json, ADMIN_PERMISSIONS
    JSON.parse(last_response.body, symbolize_names: true)
  end

  def read_role(id:)
    get "/roles/#{id}", nil, ADMIN_PERMISSIONS
    JSON.parse(last_response.body, symbolize_names: true)
  end

  def update_role(id:, updates:)
    put "/roles/#{id}", updates.to_json, ADMIN_PERMISSIONS
    JSON.parse(last_response.body, symbolize_names: true)
  end

  def delete_role(id:)
    delete "/roles/#{id}", nil, ADMIN_PERMISSIONS
  end

  RSpec.configure do |config|
    config.before(:all) do
      puts "Creating roles"
      @admin_role = create_role({ name: 'Admin', permissions: %w[Admin] })
      @test_role = create_role({ name: 'Test', permissions: %w[UIAccess MetricsView] })
      @temp_role = create_role({ name: 'Temp', permissions: %w[UIAccess MetricsView] })
    end
  end

  describe '/permissions' do
    it 'should handle reading permissions' do
      get '/permissions'
      expect(last_response.status).to eq(200)
      expect(JSON.parse(last_response.body).first['name']).to eq('Admin')
    end
  end

  describe '/roles' do
    it 'should handle creating roles' do
      expect(@admin_role[:id]).to eq(1)
      expect(@test_role[:id]).to eq(2)
    end

    it 'should handle reading roles' do
      role = read_role(id: @test_role[:id])
      expect(role[:name]).to eq('Test')
      read_role(id: 100)
      expect(last_response.status).to eq(404)
    end

    it 'should handle updating roles' do
      role = read_role(id: @test_role[:id])
      expect(last_response.status).to eq(200)
      expect(role[:name]).to eq('Test')

      update_role(id: @test_role[:id], updates: {
                    name: 'TestRole'
                  })
      expect(last_response.status).to eq(200)

      role = read_role(id: @test_role[:id])
      expect(last_response.status).to eq(200)
      expect(role[:name]).to eq('TestRole')
    end

    it 'should handle errors when updating roles' do
      update_role(id: @test_role[:id], updates: {
                    nane: 'TestRole'
                  })
      expect(last_response.status).to eq(500)
      expect(JSON.parse(last_response.body)['error']).to eq("Unknown field \'nane\'")

      update_role(id: 100, updates: {
                    name: 'TestRole'
                  })
      expect(last_response.status).to eq(404)
    end

    it 'should handle deleting roles' do
      delete_role(id: @temp_role[:id])
      expect(last_response.status).to eq(200)
      delete_role(id: 100)
      expect(last_response.status).to eq(404)
    end

    it 'should prevent deleting the admin role' do
      delete_role(id: 1)
      expect(last_response.status).to eq(403)
      expect(JSON.parse(last_response.body)['error']).to eq('Unable to delete the admin role.')
    end

    it 'should prevent creating roles with invalid permissions' do
      create_role({ name: 'Bad', permissions: %w[SomethingBad] })
      expect(last_response.status).to eq(400)
      expect(JSON.parse(last_response.body)['validation_errors']['permissions'][0]).to eq('must contain only valid permissions')
    end
  end

  describe '/users' do
    it 'should handle creating users' do
      create_user({ name: 'Admin', dn: 'CN=Admin, OU=Foo, C=US', role_ids: [@admin_role[:id]] })
      expect(last_response.status).to eq(200)
      create_user({ name: 'Alice', dn: 'CN=Alice, OU=Foo, C=US', role_ids: [@test_role[:id]] })
      expect(last_response.status).to eq(200)
      create_user({ name: 'Bob', dn: 'CN=Bob, OU=Foo, C=US', role_ids: [@test_role[:id]] })
      expect(last_response.status).to eq(200)
      create_user({ name: 'Cam', dn: 'CN=Cam, OU=Foo, C=US' })
      expect(last_response.status).to eq(200)
      create_user({ name: 'Dave', dn: 'CN=Dave, OU=Foo, C=US' })
      expect(last_response.status).to eq(200)
    end

    it 'should validate presence of required fields on user creation' do
      res = create_user({ dn: 'CN=Sally, OU=Foo, C=US' })
      expect(last_response.status).to eq(400)
      expect(res[:validation_errors]).to eq({ name: ['cannot be empty', 'is not a valid string'] })
    end

    it 'should validate format of DN field on user creation' do
      res = create_user({ name: 'Sally', dn: 'Sally' })
      expect(last_response.status).to eq(400)
      expect(res[:validation_errors]).to eq({ dn: ['must be a valid Distinguished Name'] })
    end

    it 'should validate uniqueness of DN and name fields on user creation' do
      res = create_user({ name: 'Alice', dn: 'CN=Alice, OU=Foo, C=US' })
      expect(last_response.status).to eq(400)
      expect(res[:validation_errors]).to eq({ dn: ['must be unique'], name: ['must be unique'] })
    end

    it 'should normalize DN on user creation' do
      user = create_user({ name: 'Erin', dn: 'CN=Erin,OU=Foo,C=US' })
      expect(last_response.status).to eq(200)
      expect(user[:dn]).to eq('CN=Erin, OU=Foo, C=US')
    end

    it 'should handle reading users' do
      user = read_user(id: 2)
      expect(user[:dn]).to eq('CN=Alice, OU=Foo, C=US')
      read_user(id: 100)
      expect(last_response.status).to eq(404)
    end

    it 'should handle updating users' do
      user = read_user(id: 2)
      expect(last_response.status).to eq(200)
      expect(user[:name]).to eq('Alice')

      update_user(id: 2, updates: {
                    name: 'Alison'
                  })
      expect(last_response.status).to eq(200)

      user = read_user(id: 2)
      expect(last_response.status).to eq(200)
      expect(user[:name]).to eq('Alison')
    end

    it 'should handle errors when updating users' do
      update_user(id: 2, updates: {
                    nane: 'Alison'
                  })
      expect(last_response.status).to eq(500)
      expect(JSON.parse(last_response.body)['error']).to eq("Unknown field \'nane\'")

      update_user(id: 100, updates: {
                    name: 'Alison'
                  })
      expect(last_response.status).to eq(404)
    end

    it 'should handle deleting users' do
      @user = create_user(name: 'Delete Test', dn: 'CN=Delete Test', role_ids: [1])
      delete_user(id: @user[:id])
      expect(last_response.status).to eq(200)
      delete_user(id: 100)
      expect(last_response.status).to eq(404)
    end

    it 'should prevent deleting the admin user' do
      delete_user(id: 1)
      expect(last_response.status).to eq(403)
      expect(JSON.parse(last_response.body)['error']).to eq('Unable to delete admin user.')
    end

    it 'should handle listing all users' do
      get '/users', nil, ADMIN_PERMISSIONS
      expect(last_response.status).to eq(200)
      expect(JSON.parse(last_response.body).size).to eq(6)
    end

    it 'should handle receiving bad JSON' do
      post '/users', '{{', ADMIN_PERMISSIONS
      expect(last_response.status).to eq(500)
      expect(JSON.parse(last_response.body)['error']).to eq('Error parsing JSON')
    end
  end

  describe '/no-auth' do
    it 'should bot require credentials' do
      get '/no-auth', {}, {
        'HTTP_X_ORIGINAL_URL' => 'https://test.deltafi.org'
      }
      expect(last_response.status).to eq(200)
      expect(last_response.headers['X-User-ID']).to eq('0')
      expect(last_response.headers['X-User-Name']).to eq('Admin')
      expect(last_response.headers['X-User-Permissions']).to eq('Admin')
    end
  end

  describe '/basic-auth' do
    before(:all) do
      create_user({ name: 'Frank', username: USERNAME, password: PASSWORD, role_ids: [@test_role[:id]] })
    end

    it 'should require credentials' do
      get '/basic-auth', {}, {
        'HTTP_X_ORIGINAL_URL' => 'https://test.deltafi.org'
      }
      expect(last_response.status).to eq(401)
    end

    it 'should set expected headers for a known user' do
      basic_auth_request(USERNAME, PASSWORD, 'https://test.deltafi.org/')
      expect(last_response.headers['X-User-ID']).to eq('8')
      expect(last_response.headers['X-User-Name']).to eq('frank')
      expect(last_response.headers['X-User-Permissions']).to eq('MetricsView,UIAccess')
    end

    it 'should deny access (401) to an unknown user' do
      basic_auth_request('foo', 'bar', 'https://test.deltafi.org')
      expect(last_response.status).to eq(401)
    end

    it 'should deny access (403) to a known user without access to requested domain' do
      basic_auth_request(USERNAME, PASSWORD, 'https://k8s.test.deltafi.org/')
      expect(last_response.status).to eq(403)
    end

    it 'should deny access (401) to a known user with bad password' do
      basic_auth_request(USERNAME, 'foobar', 'https://test.deltafi.org/')
      expect(last_response.status).to eq(401)
    end

    it 'allow access to a known user with access to requested domain' do
      basic_auth_request(USERNAME, PASSWORD, 'https://test.deltafi.org/')
      expect(last_response.status).to eq(200)
      basic_auth_request(USERNAME, PASSWORD, 'https://metrics.test.deltafi.org/')
      expect(last_response.status).to eq(200)
    end
  end

  describe '/cert-auth' do
    it 'should set expected headers for a known user' do
      should_allow('CN=Alice, OU=Foo, C=US', 'http://test.deltafi.org/')
      expect(last_response.headers['X-User-ID']).to eq('CN=Alice, OU=Foo, C=US')
      expect(last_response.headers['X-User-Name']).to eq('Alice')
      expect(last_response.headers['X-User-Permissions']).to eq('MetricsView,UIAccess')
    end

    it 'should deny access (400) if required request headers are not set' do
      get '/cert-auth', {}, { 'HTTP_SSL_CLIENT_SUBJECT_DN' => 'CN=TestUser1' }
      expect(last_response.status).to eq(400)
      expect(last_response.body).to eq('400 Bad Request: Missing required header: X_ORIGINAL_URL')
      get '/cert-auth', {}, { 'HTTP_X_ORIGINAL_URL' => 'https://test.deltafi.org' }
      expect(last_response.status).to eq(400)
      expect(last_response.body).to eq('400 Bad Request: Missing required header: SSL_CLIENT_SUBJECT_DN')
    end

    it 'should deny access (401) if DN is invalid' do
      get '/cert-auth', {}, {
        'HTTP_SSL_CLIENT_SUBJECT_DN' => 'ABC',
        'HTTP_X_ORIGINAL_URL' => 'https://test.deltafi.org'
      }
      expect(last_response.status).to eq(401)
      expect(last_response.body).to eq('401 Unauthorized: Invalid Distinguished Name: ABC')
    end

    it 'should deny access (403) to an unknown user' do
      should_deny('CN=Jerry, OU=Foo, C=US', 'https://test.deltafi.org/', 403)
    end

    it 'should deny access (403) to a known user without access to requested domain' do
      should_deny('CN=Alice, OU=Foo, C=US', 'https://k8s.test.deltafi.org/', 403)
    end

    it 'should ignore whitespace between DN fields' do
      should_allow('CN=Alice,OU=Foo,  C=US', 'http://test.deltafi.org/')
      should_allow('CN=Bob ,OU=Foo, C=US', 'http://test.deltafi.org/')
    end
  end
end
