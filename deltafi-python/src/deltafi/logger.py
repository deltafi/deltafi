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

import logging
import sys
from datetime import datetime, UTC

import json_logging


def get_logger(name: str = None) -> logging.Logger:
    logger = logging.getLogger(name)
    logger.setLevel(logging.INFO)
    logger.addHandler(logging.StreamHandler(sys.stdout))
    logger.propagate = False

    if name is not None:
        logger = logging.LoggerAdapter(logger, dict(action=name))

    return logger


def _sanitize_log_msg(record):
    return record.getMessage().replace('\n', '_').replace('\r', '_').replace('\t', '_')


class JSONLogFormatter(json_logging.JSONLogFormatter):

    def _format_log_object(self, record, request_util):
        utcnow = datetime.now(UTC)

        json_log_object = {
            'timestamp': json_logging.util.iso_time_format(utcnow),
            'message': _sanitize_log_msg(record),
            'loggerName': record.name,
            'threadName': record.threadName,
            'level': record.levelname,
            'module': record.module,
            'line_no': record.lineno
        }

        if hasattr(record, 'action'):
            json_log_object['action'] = record.action

        if hasattr(record, 'props'):
            json_log_object.update(record.props)

        if record.exc_info or record.exc_text:
            json_log_object.update(self.get_exc_fields(record))

        return json_log_object


json_logging.init_non_web(custom_formatter=JSONLogFormatter, enable_json=True)
