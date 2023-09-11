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

class ApiServer < Sinatra::Base
  register Sinatra::Namespace
  namespace '/api/v1/registry' do
    get '/catalog' do
      authorize! :RegistryView
      DF::API::V1::Registry.catalog
    end

    get '/list' do
      authorize! :RegistryView
      build_response({ result: DF::API::V1::Registry.list })
    end

    post '/add/*' do
      authorize! :RegistryUpload

      unless (image_name = params[:splat]&.join('/'))
        status 400
        return build_error_response '"name" header is required for upload'
      end

      name = request.env['HTTP_NAME'] || params[:name]

      puts "Adding image #{image_name} to registry #{"as #{name}" if name}"
      response = DF::API::V1::Registry.add image_name: image_name, new_name: name
      audit("Uploaded #{name || image_name}#{", original name #{image_name}" if image_name != name}")
      build_response({ result: response })
    end

    post '/replace/*' do
      authorize! :RegistryUpload
      authorize! :RegistryDelete

      unless (image_name = params[:splat]&.join('/'))
        status 400
        return build_error_response '"name" header is required for upload'
      end

      name = request.env['HTTP_NAME'] || params[:name]

      puts "Replacement add image #{image_name} to registry #{"as #{name}" if name}"
      response = DF::API::V1::Registry.replace image_name: image_name, new_name: name
      audit("Uploaded (replacement) #{name || image_name}#{", original name #{image_name}" if image_name != name}")
      build_response({ result: response })
    end

    post '/upload' do
      authorize! :RegistryUpload
      unless (image_name = request.env['HTTP_NAME'])
        status 400
        return build_error_response '"name" header is required for upload'
      end
      tempfile = Tempfile.new('registry_upload')
      begin
        tempfile.binmode
        IO.copy_stream(request.body, tempfile)
        tempfile.close

        response = DF::API::V1::Registry.upload image_name: image_name, tarball: tempfile.path
        audit("Uploaded #{image_name}, original name #{tempfile.inspect}")
        retval = build_response({ result: response })
      rescue StandardError => e
        status 500
        retval = build_error_response e.message
      ensure
        tempfile.close unless tempfile.closed?
        tempfile.unlink
      end
      retval
    end

    delete '/delete/*' do
      authorize! :RegistryDelete

      image_tag = request.env['HTTP_TAG'] || params[:tag]
      image_splat = params[:splat]
      unless image_tag
        tag_candidate = image_splat.pop
        if tag_candidate.split(':').size == 2
          image_splat << tag_candidate.split(':').first
          image_tag = tag_candidate.split(':').last
        else
          image_splat << tag_candidate
          image_tag = 'latest'
        end
      end
      image_name = image_splat.join('/')
      puts "Deleting image #{image_name} with tag #{image_tag}"

      response = build_response({ result: DF::API::V1::Registry.delete(image_name: image_name, image_tag: image_tag) })

      audit("Deleted #{image_name} with tag #{image_tag}")

      response
    end

    delete '/repository/delete/*' do
      authorize! :RegistryDelete

      repository_name = params[:splat].join('/')

      puts "Deleting all tags in #{repository_name}"

      response = build_response({ result: DF::API::V1::Registry.delete_repository(repository_name: repository_name) })

      audit("Deleted all tags from #{repository_name}")

      response
    end
  end
end
