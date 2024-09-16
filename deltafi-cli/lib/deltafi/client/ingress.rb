#
#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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

module Deltafi
  module Client
    class Ingress
      include Concurrent::Async

      def initialize(ingress_url = nil)
        @ingress_url = ingress_url || generate_url
      end

      def generate_url
        ingress_ip = `deltafi serviceip deltafi-ingress-service`
        "http://#{ingress_ip.chomp}/api/v2/deltafile/ingress"
      end

      def ingress_data(flow, filename, body, media_type = 'application/octet-stream', filename_tag = nil)
        response = if filename
                     DeltafiClient.post(@ingress_url,
                                        body_stream: File.open(filename, 'r'),
                                        headers: { 'Content-Type' => media_type,
                                                   'Content-Length' => File.size(filename).to_s,
                                                   'Filename' => "#{File.basename(filename)}#{filename_tag}",
                                                   'Flow' => flow })
                   else
                     DeltafiClient.post(@ingress_url,
                                        body: body,
                                        headers: { 'Content-Type' => media_type,
                                                   'Filename' => "#{flow}#{filename_tag}",
                                                   'Flow' => flow })
                   end

        raise StandardError, "Failed to POST to #{INGRESS_URL}: #{response.code} #{response.body}" unless response.code == 200

        response.body
      end
    end
  end
end
