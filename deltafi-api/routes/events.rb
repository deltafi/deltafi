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

$LOAD_PATH.unshift File.expand_path(File.join(File.dirname(__FILE__), '../models'))

require 'event'

class ApiServer < Sinatra::Base
  register Sinatra::Namespace

  namespace '/api/v1' do
    get '/events' do
      authorize! :EventRead

      start_param = params.delete('start')
      end_param = params.delete('end')
      start_time = start_param.nil? ? 24.hours.ago : Time.parse(start_param)
      end_time = end_param.nil? ? Time.now : Time.parse(end_param)
      params[:timestamp] = (start_time..end_time)

      Event.where(params).order_by(%i[timestamp desc]).to_json
    end

    get '/events/:id' do |id|
      authorize! :EventRead

      event = Event.find(id)
      return not_found(id) if event.nil?

      event.to_json
    end

    post '/events' do
      authorize! :EventCreate

      new_event = read_json_body
      raise '_id cannot be specified on Event creation' if new_event.key?(:_id)

      event = Event.create!(new_event)
      event.to_json
    end

    put '/events/:id' do |id|
      authorize! :EventUpdate

      event = Event.find(id)
      return not_found(id) if event.nil?

      updates = read_json_body
      raise '_id cannot be updated' if updates.key?(:_id)

      event.update_attributes!(updates)
      event.to_json
    end

    put '/events/:id/:action' do |id, action|
      raise Sinatra::NotFound unless %w[acknowledge unacknowledge].include?(action)

      authorize! :EventAcknowledge

      event = Event.find(id)
      return not_found(id) if event.nil?

      event.update_attributes!({ acknowledged: action == 'acknowledge' })
      event.to_json
    end

    delete '/events/:id' do |id|
      authorize! :EventDelete

      event = Event.find(id)
      return not_found(id) if event.nil?

      event.destroy
      event.to_json
    end

    def not_found(id)
      error("Event with ID '#{id}' not found.", 404)
    end
  end
end
