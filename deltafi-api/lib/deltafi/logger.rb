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

require 'logger'

$stdout.sync = true

module Deltafi
  module Logger
    LOGLEVELS = %w[DEBUG INFO WARN ERROR FATAL UNKNOWN].freeze

    def logger
      klass = self.class.name
      klass = name if klass == 'Module'
      klass = 'Main' if klass == 'Object'
      @logger ||= Logger.logger_for(klass)
    end

    def info(msg)
      logger.send(:info, msg)
    end

    def error(msg)
      logger.send(:error, msg)
    end

    def debug(msg)
      logger.send(:debug, msg)
    end

    # warn is called by some gems with 2 args
    def warn(msg, _ignore = nil)
      logger.send(:warn, msg)
    end

    @loggers = {}

    class << self
      def logger_for(klass)
        @loggers[klass] ||= configure_logger_for(klass)
      end

      def configure_logger_for(klass)
        logger = ::Logger.new($stdout)
        logger.progname = klass

        level ||= LOGLEVELS.index ENV.fetch('LOG_LEVEL', 'INFO')
        level ||= Logger::INFO
        logger.level = level

        return logger
      end
    end
  end
end
