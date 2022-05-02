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

# frozen_string_literal: true

require 'yaml'

module Deltafi
  module API
    module V1
      module Metrics
        module Action
          class << self
            def queues
              query = <<-QUERY
              {
                "size": 0,
                "query": {
                  "bool": {
                    "filter": [
                      {
                        "term": {
                          "metric.name.keyword": "queue_size"
                        }
                      },
                      {
                        "range": {
                          "time": {
                            "gte": "now-5m",
                            "lte": "now"
                          }
                        }
                      }
                    ]
                  }
                },
                "aggs": {
                  "metrics": {
                    "terms": {
                      "field": "metric.tags.queue_name.keyword",
                      "size": 10000
                    },
                    "aggs": {
                      "last_value": {
                        "top_hits": {
                          "_source": {
                            "includes": [
                              "metric"
                            ]
                          },
                          "size": 1,
                          "sort": [
                            {
                              "time": {
                                "order": "desc"
                              }
                            }
                          ]
                        }
                      }
                    }
                  }
                }
              }
              QUERY
              response = DF.elasticsearch('fluentd/_search', query)
              raise StandardError, "Elasticsearch query failed: #{response.parsed_response['error']['reason']}" if response.code != 200

              response.parsed_response['aggregations']['metrics']['buckets'].map do |bucket|
                {
                  name: bucket['key'],
                  size: bucket['last_value']['hits']['hits'][0]['_source']['metric']['value'],
                  timestamp: Time.at(bucket['last_value']['hits']['hits'][0]['_source']['metric']['timestamp'].to_i / 1000)
                }
              end
            end

            def metrics_by_action_by_family(last: '5m', flow:)
              results = flow.nil? ? action_names_by_family : Hash.new { |h, k| h[k] = {} }

              counter_metrics(last: last, flow: flow).each do |family|
                family_name = family['key']
                family['actions']['buckets'].map do |action|
                  action_name = action['key']
                  results[family_name][action_name] = {}
                  action['metrics']['buckets'].each do |metric|
                    metric_name = metric['key']
                    results[family_name][action_name][metric_name] = metric['sum']['value']
                  end
                end
              end
              results
            end

            def counter_metrics(last: '5m', flow:)
              flow_query = "{ \"term\" : { \"metric.tags.flow\": \"#{flow}\" } }," unless flow.nil?
              query = <<-QUERY
              {
                "size": 0,
                "query": {
                  "bool": {
                    "filter": [
                      {
                        "term": {
                          "metric.type.keyword": "COUNTER"
                        }
                      },#{flow_query}
                      {
                        "range": {
                          "time": {
                            "gte": "now-#{last}",
                            "lte": "now"
                          }
                        }
                      }
                    ]
                  }
                },
                "aggs": {
                  "families": {
                    "terms": {
                      "field": "metric.source.keyword"
                    },
                    "aggs": {
                      "actions": {
                        "terms": {
                          "field": "metric.tags.action.keyword"
                        },
                        "aggs": {
                          "metrics": {
                            "terms": {
                              "field": "metric.name.keyword"
                            },
                            "aggs": {
                              "sum": {
                                "sum": {
                                  "field": "metric.value"
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
              QUERY
              response = DF.elasticsearch('fluentd/_search', query)
              response.parsed_response['aggregations']['families']['buckets']
            end

            def action_names_by_family
              action_families = DF.graphql('query { getActionNamesByFamily { family actionNames } }')
                                  .parsed_response['data']['getActionNamesByFamily']

              output = Hash.new { |h, k| h[k] = h.dup.clear }
              
              action_families.each_entry do |family_entry|
                family = family_entry['family']
                family_entry['actionNames'].each do |action_name|
                  output[family][action_name] = {}
                end
              end

              output
            end
          end
        end
      end
    end
  end
end
