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

module Deltafi
  module Common
    K8S_NAMESPACE = 'deltafi'
    STATUS_REDIS_KEY = 'org.deltafi.monitor.status'
    SSE_REDIS_CHANNEL_PREFIX = 'org.deltafi.ui.sse'
    ACTION_HEARTBEAT_REDIS_KEY = 'org.deltafi.action-queue.heartbeat'
    ACTION_HEARTBEAT_THRESHOLD = 60
    MONITOR_HEARTBEAT_REDIS_KEY = 'org.deltafi.monitor.heartbeat'
    LONG_RUNNING_TASKS_REDIS_KEY = 'org.deltafi.action-queue.long-running-tasks'
    ACTION_QUEUE_SIZES_REDIS_KEY = 'org.deltafi.action-queue.sizes'
  end
end
