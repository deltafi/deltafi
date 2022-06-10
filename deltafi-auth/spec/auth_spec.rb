# frozen_string_literal: true

#
#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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

ENV['RACK_ENV'] = 'test'

require './auth_api.rb'
require './models/user.rb'
require 'rspec'
require 'rack/test'

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
  end

  def should_deny(dn, url, code)
    cert_auth_request(dn, url)
    expect(last_response.status).to eq(code)
  end

  def create_user(params)
    post '/users', params.to_json
    JSON.parse(last_response.body, symbolize_names: true)
  end

  def read_user(id:)
    get "/users/#{id}"
    JSON.parse(last_response.body, symbolize_names: true)
  end

  def update_user(id:, updates:)
    put "/users/#{id}", updates.to_json
  end

  def delete_user(id:)
    delete "/users/#{id}"
  end

  describe '/users' do
    it 'should handle creating users' do
      create_user({ name: 'Alice', dn: 'CN=Alice, OU=Foo, C=US', domains: 'test.deltafi.org' })
      create_user({ name: 'Bob', dn: 'CN=Bob, OU=Foo, C=US', domains: '*test.deltafi.org' })
      create_user({ name: 'Cam', dn: 'CN=Cam, OU=Foo, C=US', domains: 'test.deltafi.org, *fi.test.deltafi.org' })
      create_user({ name: 'Dave', dn: 'CN=Dave, OU=Foo, C=US', domains: 'metrics.test.deltafi.org' })
      expect(last_response.status).to eq(200)
    end

    it 'should validate presence of required fields on user creation' do
      res = create_user({ dn: 'CN=Sally, OU=Foo, C=US', domains: 'test.deltafi.org' })
      expect(last_response.status).to eq(400)
      expect(res[:validation_errors]).to eq({ name: ['cannot be empty', 'is not a valid string'] })
    end

    it 'should validate format of DN field on user creation' do
      res = create_user({ name: 'Sally', dn: 'Sally', domains: 'test.deltafi.org' })
      expect(last_response.status).to eq(400)
      expect(res[:validation_errors]).to eq({ dn: ['must be a valid Distinguished Name'] })
    end

    it 'should validate uniqueness of DN and name fields on user creation' do
      res = create_user({ name: 'Alice', dn: 'CN=Alice, OU=Foo, C=US', domains: 'test.deltafi.org' })
      expect(last_response.status).to eq(400)
      expect(res[:validation_errors]).to eq({ dn: ['must be unique'], name: ['must be unique'] })
    end

    it 'should normalize DN on user creation' do
      user = create_user({ name: 'Erin', dn: 'CN=Erin,OU=Foo,C=US', domains: '*' })
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
      expect(user[:domains]).to eq('test.deltafi.org')

      update_user(id: 2, updates: {
                    domains: 'test.deltafi.org, nifi.test.deltafi.org'
                  })
      expect(last_response.status).to eq(200)

      user = read_user(id: 2)
      expect(last_response.status).to eq(200)
      expect(user[:domains]).to eq('test.deltafi.org, nifi.test.deltafi.org')
    end

    it 'should handle errors when updating users' do
      update_user(id: 2, updates: {
                    domainss: 'test.deltafi.org'
                  })
      expect(last_response.status).to eq(500)
      expect(last_response.body).to eq('{"error":"Unknown field \'domainss\'"}')

      update_user(id: 100, updates: {
                    domains: 'test.deltafi.org, nifi.test.deltafi.org'
                  })
      expect(last_response.status).to eq(404)
    end

    it 'should handle deleting users' do
      delete_user(id: 6)
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
      get '/users'
      expect(last_response.status).to eq(200)
      expect(JSON.parse(last_response.body).size).to eq(5)
    end

    it 'should handle receiving bad JSON' do
      post '/users', '{{'
      expect(last_response.status).to eq(500)
      expect(JSON.parse(last_response.body)['error']).to eq('Error parsing JSON')
    end
  end

  describe '/basic-auth' do
    USERNAME = 'frank'
    PASSWORD = 'P@ssw0rd'

    before do
      create_user({ name: 'Frank', domains: 'test.deltafi.org', username: USERNAME, password: PASSWORD })
    end

    it 'should require credentials' do
      get '/basic-auth', {}, {
        'HTTP_X_ORIGINAL_URL' => 'https://test.deltafi.or'
      }
      expect(last_response.status).to eq(401)
    end

    it 'should deny access (401) to an unknown user' do
      basic_auth_request('foo', 'bar', 'https://test.deltafi.org')
      expect(last_response.status).to eq(401)
    end

    it 'should deny access (403) to a known user without access to requested domain' do
      basic_auth_request(USERNAME, PASSWORD, 'https://dev.deltafi.org/')
      expect(last_response.status).to eq(403)
    end

    it 'allow access to a known user with access to requested domain' do
      basic_auth_request(USERNAME, PASSWORD, 'https://test.deltafi.org/')
      expect(last_response.status).to eq(200)
    end
  end

  describe '/auth' do
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

    it 'should deny access (401) to an unknown user' do
      should_deny('CN=Jerry, OU=Foo, C=US', 'https://test.deltafi.org/', 401)
    end

    it 'should deny access (403) to a known user without access to requested domain' do
      should_deny('CN=Alice, OU=Foo, C=US', 'https://abc.deltafi.org/', 403)
    end

    it 'should handle a single domain in a user\'s "domains" field' do
      should_allow('CN=Dave, OU=Foo, C=US', 'https://metrics.test.deltafi.org/')
    end

    it 'should handle wildcards in a user\'s "domains" field' do
      should_allow('CN=Bob, OU=Foo, C=US', 'https://nifi.test.deltafi.org/')
      should_allow('CN=Bob, OU=Foo, C=US', 'https://test.deltafi.org/path')
      should_allow('CN=Bob, OU=Foo, C=US', 'https://abc.test.deltafi.org/path')
      should_deny('CN=Bob, OU=Foo, C=US', 'https://dev.deltafi.org/', 403)
    end

    it 'should handle CSV in a user\'s "domains" field' do
      should_allow('CN=Alice, OU=Foo, C=US', 'https://nifi.test.deltafi.org/')
      should_allow('CN=Alice, OU=Foo, C=US', 'https://test.deltafi.org/')
      should_deny('CN=Alice, OU=Foo, C=US', 'https://abc.test.deltafi.org/', 403)
    end

    it 'should handle both wildcards and CSV in a user\'s "domains" field' do
      should_allow('CN=Cam, OU=Foo, C=US', 'https://nifi.test.deltafi.org/')
      should_allow('CN=Cam, OU=Foo, C=US', 'https://test.deltafi.org/')
      should_deny('CN=Cam, OU=Foo, C=US', 'https://abc.test.deltafi.org/', 403)
    end

    it 'should ignore whitespace between DN fields' do
      should_allow('CN=Alice,OU=Foo,  C=US', 'http://test.deltafi.org/')
      should_allow('CN=Bob ,OU=Foo, C=US', 'http://test.deltafi.org/')
    end
  end
end
