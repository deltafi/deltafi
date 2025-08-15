#
#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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

import time
from enum import Enum
from typing import NamedTuple


class LogSeverity(Enum):
    TRACE = "TRACE"
    INFO = "INFO"
    WARNING = "WARNING"
    ERROR = "ERROR"
    USER = "USER"


class LogMessage(NamedTuple):
    severity: LogSeverity
    created: int
    source: str
    message: str

    @classmethod
    def info(cls, source: str, message: str):
        return LogMessage(severity=LogSeverity.INFO, created=time.time(), source=source, message=message)

    @classmethod
    def warning(cls, source: str, message: str):
        return LogMessage(severity=LogSeverity.WARNING, created=time.time(), source=source,
                          message=message)

    @classmethod
    def error(cls, source: str, message: str):
        return LogMessage(severity=LogSeverity.ERROR, created=time.time(), source=source,
                          message=message)

    def json(self):
        return {'severity': self.severity.value,
                'created': self.created,
                'source': self.source,
                'message': self.message}
