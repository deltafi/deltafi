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

import json
from abc import ABC
from abc import abstractmethod
from typing import List

from deepdiff import DeepDiff

from .assertions import *


class CompareHelper(ABC):
    @abstractmethod
    def compare(self, expected: str, actual: str, label: str):
        pass


class GenericCompareHelper(CompareHelper):
    def compare(self, expected: str, actual: str, label: str):
        assert_equal_with_label(expected, actual, label)


class JsonCompareHelper(CompareHelper):
    def __init__(self, regex_exclusion_list: List):
        self.excludes = regex_exclusion_list

    def compare(self, expected: str, actual: str, label: str):
        exp = json.loads(expected)
        act = json.loads(actual)
        diffs = DeepDiff(exp, act, exclude_regex_paths=self.excludes)
        if len(diffs) > 0:
            raise ValueError(f"{diffs}")
        assert len(diffs) == 0
