# frozen_string_literal: true

module Deltafi
  module API
    NAMESPACE = 'deltafi'

    def self.k8s_client
      ENV['RUNNING_IN_CLUSTER'].nil? ? K8s::Client.config(K8s::Config.load_file(File.expand_path '~/.kube/config')) : K8s::Client.in_cluster_config
    end
  end
end

Dir[File.join(File.dirname(__FILE__), 'api', '*.rb')].each do |f|
  require "deltafi/api/#{File.basename(f).split('.')[0]}"
end