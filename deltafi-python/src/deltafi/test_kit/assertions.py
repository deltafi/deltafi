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

from typing import Dict

from .constants import IGNORE_VALUE


def assert_equal(e, a):
    assert e == a, f"{e} != {a}"


def assert_equal_with_label(e, a, l):
    assert e == a, f"{l}. Expected:\n<<{e}>>\nBut was:\n<<{a}>>"


def assert_equal_len(e, a):
    assert len(e) == len(a), f"{len(e)} != {len(a)}"


def assert_key_in(k, m):
    assert k in m, f"{k} not found"


def assert_key_not_in(k, m):
    assert k not in m, f"{k} found, but not expected"


def assert_keys_and_values(expected: Dict, actual: Dict):
    for key in expected:
        assert_key_in(key, actual)
        if expected[key] != IGNORE_VALUE:
            assert_equal(expected[key], actual[key])
