# frozen_string_literal: true

module Deltafi
  module API
    module Errors
      class << self
        def last_errored(count = 10)
          query = <<-QUERY
            {
              lastErrored(last: #{count}) {
                did
                stage
                created
                modified
                actions {
                  name
                  state
                  created
                  modified
                  errorCause
                  errorContext
                }
                sourceInfo {
                  flow
                  filename
                  metadata { key value }
                }
                enrichment { key value }
                protocolStack {
                  type
                  objectReference { name bucket offset size }
                  metadata { key value }
                }
                domains { key value }
                formattedData {
                  objectReference { name bucket offset size }
                  filename
                  formatAction
                  egressActions
                  metadata { key value }
                }
                markedForDelete
                markedForDeleteReason
              }
            }
          QUERY
          response = Deltafi::API.graphql(query)
          raise response.body if response.code != 200

          errored = response.parsed_response['data']['lastErrored']
          errored.map do |e|
            errored_actions = e['actions'].select { |a| a['state'] == 'ERROR' }.map do |a|
              {
                action: a['name'],
                state: a['state'],
                created: a['created'],
                modified: a['modified'],
                cause: a['errorCause'],
                context: a['errorContext']
              }
            end
            {
              did: e['did'],
              filename: e['sourceInfo']['filename'],
              flow: e['sourceInfo']['flow'],
              stage: e['stage'],
              created: e['created'],
              modified: e['modified'],
              last_error_cause: errored_actions.max_by { |ea| ea[:modified] }[:cause],
              errors: errored_actions
            }
          end
        end

        def retry(did)
          query = <<-QUERY
            mutation {
              retry(did: "#{did}") {
                did
              }
            }
          QUERY
          graphql_response = Deltafi::API.graphql(query)
          success = graphql_response.code == 200 && graphql_response.parsed_response['errors'].nil?
          response = { success: success }
          response[:errors] = graphql_response.parsed_response['errors'] unless success
          response
        end
      end
    end
  end
end
