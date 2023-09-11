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

  namespace '/api/v1/metrics' do
    get '/system/content' do
      authorize! :MetricsView

      build_response({ content: DF::API::V1::Metrics::System.content })
    end

    get '/system/nodes' do
      authorize! :MetricsView

      build_response({ nodes: DF::API::V1::Metrics::System.nodes })
    end

    get '/queues' do
      authorize! :MetricsView

      build_response({ queues: DF::API::V1::Metrics::Action.queues })
    end

    get '/action' do
      authorize! :MetricsView

      last = params[:last] || '5m'
      flow = params[:flowName]
      build_response({ actions: DF::API::V1::Metrics::Action.metrics_by_action_by_family(last: last, flow: flow) })
    end

    get '/flow(.json)?' do
      authorize! :MetricsView

      build_response({ flow_report: DF::API::V1::Metrics::Flow.summary(params: params) })
    end

    get '/flow.csv' do
      authorize! :MetricsView

      content_type 'text/csv'
      DF::API::V1::Metrics::Flow.summary_csv(params: params)
    end

    get '/graphite' do
      authorize! :MetricsView

      DF::Metrics.graphite(params, raw: true)
    end
  end
end
