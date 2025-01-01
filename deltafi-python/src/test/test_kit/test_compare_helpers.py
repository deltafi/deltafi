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


import pytest
import re

from deltafi.test_kit.compare_helpers import JsonCompareHelper


@pytest.mark.parametrize("obj, item",
                         [
                             ([{"a": "alpha"}, {"b": "bravo"}], "notfound"),                              # string
                             ([{"a": "alpha"}, {"b": "bravo"}], re.compile("^notfound$")),                # regex
                             ([{"a": "alpha"}, {"b": "bravo"}], ["notfound"]),                            # list of 1 string
                             ([{"a": "alpha"}, {"b": "bravo"}], ["notfound1", "notfound2"]),              # list of 2 strings
                             ([{"a": "alpha"}, {"b": "bravo"}], [re.compile("^notfound1$")]),             # list of 1 regex
                             ([{"a": "alpha"}, {"b": "bravo"}], [re.compile("^notfound1$"),
                                                                 re.compile("^notfound1$")]),             # list of 2 regexes
                             ([{"a": "alpha"}, {"b": "bravo"}], ["notfound1", re.compile("^notfound$")])  # list of string and regex
                         ])
def test_is_not_found_success(obj, item):
    json_compare_helper = JsonCompareHelper()
    assert json_compare_helper.is_not_found(obj, item) is None


@pytest.mark.parametrize("obj, item, in_err_msg, not_in_err_msg",
                         [
                             ([{"a": "alpha"}, {"b": "bravo"}], "bravo", ["bravo"], ["alpha"]),                  # string
                             ([{"a": "alpha"}, {"b": "bravo"}], re.compile("^bravo$"), ["bravo"], ["alpha"]),    # regex
                             ([{"a": "alpha"}, {"b": "bravo"}], ["bravo"], ["bravo"], ["alpha"]),                # list of 1 string
                             ([{"a": "alpha"}, {"b": "bravo"}], ["alpha", "bravo"], ["alpha", "bravo"], []),     # list of 2 strings
                             ([{"a": "alpha"}, {"b": "bravo"}], [re.compile("^bravo$")], ["bravo"], ["alpha"]),  # list of 1 regex
                             ([{"a": "alpha"}, {"b": "bravo"}], [re.compile("^bravo$"), re.compile("^alpha$")],
                              ["alpha", "bravo"], []),                                                           # list of 2 regexes
                             ([{"a": "alpha"}, {"b": "bravo"}], ["bravo", re.compile("^alpha$")],
                              ["alpha", "bravo"], []),                                                           # list of string and regex
                             ([{"target-key": "alpha"}, {"b": "bravo"}], "target-key", ["target-key"], []),      # demonstrate dict key
                         ])
def test_is_not_found_fail(obj, item, in_err_msg, not_in_err_msg):

    with pytest.raises(ValueError) as exc_info:
        json_compare_helper = JsonCompareHelper()
        json_compare_helper.is_not_found(obj, item)

    for err in in_err_msg:
        assert err in str(exc_info.value)

    for err in not_in_err_msg:
        assert err not in str(exc_info)


@pytest.mark.parametrize("obj, item",
                         [
                             ([{"a": "alpha"}, {"b": "bravo"}], "bravo"),                        # string
                             ([{"a": "alpha"}, {"b": "bravo"}], re.compile("^alp[a-z]{2}$")),    # regex
                             ([{"a": "alpha"}, {"b": "bravo"}], ["bravo"]),                      # list of 1 string
                             ([{"a": "alpha"}, {"b": "bravo"}], ["alpha", "bravo"]),             # list of 2 strings
                             ([{"a": "alpha"}, {"b": "bravo"}], [re.compile("^alp[a-z]{2}$")]),  # list of 1 regex
                             ([{"a": "alpha"}, {"b": "bravo"}], [re.compile("^alp[a-z]{2}$"),
                                                                 re.compile("^br[a-z]{3}$")]),   # list of 2 regexes
                             ([{"a": "alpha"}, {"b": "bravo"}],
                              ["alpha", "bravo", re.compile("^alpha$")]),                        # list of string and regex
                             ([{"target-key": "alpha"}, {"b": "bravo"}], "target-key")           # demonstrate dict key
                         ])
def test_is_found_success(obj, item):
    json_compare_helper = JsonCompareHelper()
    assert json_compare_helper.is_found(obj, item) is None


@pytest.mark.parametrize("obj, item, in_err_msg, not_in_err_msg",
                         [
                             ([{"a": "alpha"}, {"b": "bravo"}], "notfound", ["notfound"], ["alpha", "bravo"]),    # string
                             ([{"a": "alpha"}, {"b": "bravo"}], re.compile("^alp[a-z]{3}$"),
                              ["[re.compile('^alp[a-z]{3}$')]"], ["alpha", "bravo"]),                             # regex
                             ([{"a": "alpha"}, {"b": "bravo"}], ["notfound"], ["notfound"], ["alpha", "bravo"]),  # list of 1 string
                             ([{"a": "alpha"}, {"b": "bravo"}], ["notfound1", "notfound2"],
                              ["notfound1", "notfound2"], ["alpha", "bravo"]),                                    # list of 2 strings
                             ([{"a": "alpha"}, {"b": "bravo"}], [re.compile("^alp[a-z]{3}$")],
                              ["[re.compile('^alp[a-z]{3}$')]"], ["alpha", "bravo"]),                             # list of 1 regex
                             ([{"a": "alpha"}, {"b": "bravo"}],
                              [re.compile("^br[a-z]{3}$"), re.compile("^alp[a-z]{3}$")],
                              ["[re.compile('^alp[a-z]{3}$')]"], ["alpha", "bravo"]),                             # list of 2 regexes
                             ([{"a": "alpha"}, {"b": "bravo"}], ["bravo", re.compile("^alp[a-z]{3}$")],
                              ["[re.compile('^alp[a-z]{3}$')]"], ["alpha", "bravo"])                              # list of string and regex
                         ])
def test_is_found_fail(obj, item, in_err_msg, not_in_err_msg):

    with pytest.raises(ValueError) as exc_info:
        json_compare_helper = JsonCompareHelper()
        json_compare_helper.is_found(obj, item)

    for err in in_err_msg:
        assert err in str(exc_info.value)

    for err in not_in_err_msg:
        assert err not in str(exc_info)


@pytest.mark.parametrize("expected, actual",
                         [
                             ("{\"a\": 1, \"b\": 2}", "{\"a\": 1, \"b\": 2}"),  # string, same order
                             ("{\"a\": 1, \"b\": 2}", "{\"b\": 2, \"a\": 1}"),  # string, different order
                             ({"a": 1, "b": 2}, {"a": 1, "b": 2}),              # dict, same order
                             ({"a": 1, "b": 2}, {"b": 2, "a": 1})               # dict, different order
                         ])
def test_compare_success(expected, actual):
    json_compare_helper = JsonCompareHelper()
    assert json_compare_helper.compare(expected, actual, "test") is None


@pytest.mark.parametrize("expected, actual",
                         [
                             ("{\"a\": 1, \"b\": 2}", "{\"a\": 1, \"c\": 2}"),            # string, different property
                             ("{\"a\": 1, \"b\": 2}", "{\"a\": 2, \"b\": 2, \"c\": 3}"),  # string, additional property
                             ({"a": 1, "b": 2}, {"a": 1, "c": 2}),                        # dict, different property
                             ({"a": 1, "b": 2}, {"a": 1, "b": 2, "c": 3})                 # dict, additional property
                         ])
def test_compare_fail(expected, actual):
    with pytest.raises(ValueError):
        json_compare_helper = JsonCompareHelper()
        json_compare_helper.compare(expected, actual, "test")


@pytest.mark.parametrize("expected, actual",
                         [
                             # 'expected' as a list
                             ([1, 2, 3], [1, 2, 3]),                        # ints, same order
                             ([1, 2, 3], [1, 3, 2]),                        # ints, different order
                             ([1, 2, 3], [3, 2, 1]),                        # ints, different (complete reverse) order
                             ([1, 2, 2, 3], [1, 2, 2, 3]),                  # ints, repetitions in same order
                             ([1, 2, 2, 3], [2, 1, 2, 3]),                  # ints, repetitions in different order
                             (["a", "b", "c"], ["a", "b", "c"]),            # strings, same order
                             (["a", "b", "c"], ["a", "c", "b"]),            # strings, different order
                             (["a", "b", "b", "c"], ["a", "b", "b", "c"]),  # strings, repetitions in same order
                             (["a", "b", "b", "c"], ["b", "a", "b", "c"]),  # strings, repetitions in different order

                             # 'expected' as a map of repetitions
                             ({1: 1, 2: 1, 3: 1}, [1, 2, 3]),                  # ints, no repetitions
                             ({1: 1, 2: 2, 3: 1}, [1, 2, 2, 3]),               # ints, repetitions
                             ({"a": 1, "b": 1, "c": 1}, ["a", "b", "c"]),      # strings, no repetitions
                             ({"a": 1, "b": 2, "c": 1}, ["a", "b", "b", "c"])  # strings, repetitions
                         ])
def test_compare_lists_success(expected, actual):
    json_compare_helper = JsonCompareHelper()
    assert json_compare_helper.compare_lists(expected, actual, "test") is None


@pytest.mark.parametrize("expected, actual",
                         [
                             # 'expected' as a list
                             ([1, 2, 3], [1, 2, 3, 4]),                           # ints, extra item
                             ([1, 2, 3], [1, 3]),                                 # ints, missing item
                             ([1, 2, 3], [1, 3, 4]),                              # ints, missing item but same number of total items
                             ([1, 2, 3], [1, 2, 2, 3]),                           # ints, repeated item
                             ([1, 2, 2, 3], [1, 2, 2, 2, 3]),                     # ints, repeated item
                             (["a", "b", "c"], ["a", "b", "c", "d"]),             # strings, extra item
                             (["a", "b", "c"], ["a", "c"]),                       # strings, missing item
                             (["a", "b", "c"], ["a", "c", "d"]),                  # strings, missing item but same number of total items
                             (["a", "b", "c"], ["a", "b", "b", "c"]),             # strings, repeated item
                             (["a", "b", "b", "c"], ["a", "b", "b", "c", "b"]),   # strings, repeated item

                             # 'expected' as a map of repetitions
                             ({1: 1, 2: 1, 3: 1}, [1, 2, 3, 4]),        # ints, extra item
                             ({1: 1, 2: 1, 3: 1}, [1, 3]),              # ints, missing item
                             ({1: 1, 2: 1, 3: 1}, [1, 3, 4]),           # ints, missing item but same number of total items
                             ({1: 1, 2: 1, 3: 1}, [1, 2, 2, 3]),        # ints, repeated item
                             ({1: 1, 2: 2, 3: 1}, [1, 2, 2, 3, 2])      # ints, repeated item
                         ])
def test_compare_lists_fail(expected, actual):
    with pytest.raises(ValueError):
        json_compare_helper = JsonCompareHelper()
        json_compare_helper.compare_lists(expected, actual, "test")


@pytest.mark.parametrize("expected_subset, actual",
                         [
                             # 'expected_subset' as a list
                             ([1, 2, 3], [1, 3, 2]),                    # ints, 'expected' and 'actual' are the same, no repetitions
                             ([1, 2], [1, 3, 2]),                       # ints, 'expected' is subset of 'actual', no repetitions
                             ([1, 2, 2], [1, 2, 2]),                    # ints, 'expected' and 'actual' are the same, w/ repetitions
                             ([1, 2, 2], [1, 2, 3, 2]),                 # ints, 'expected' is subset of 'actual', w/ repetitions
                             (["a", "b", "c"], ["a", "c", "b"]),        # strings, 'expected' and 'actual' are the same, no repetitions
                             (["a", "b"], ["a", "c", "b"]),             # strings, 'expected' is a subset of 'actual', no repetitions
                             (["a", "b", "b"], ["a", "b", "b"]),        # strings, 'expected' and 'actual' are the same, w/ repetitions
                             (["a", "b", "b"], ["b", "a", "b", "c"]),   # strings, 'expected' is subset of 'actual', w/ repetitions

                             # 'expected_subset' as a map of repetitions
                             ({1: 1, 2: 1}, [1, 2, 3]),                 # ints, no repetitions
                             ({1: 1, 2: 2}, [1, 2, 3, 2]),              # ints, repetitions
                             ({"a": 1, "b": 1}, ["a", "b", "c"]),       # strings, no repetitions
                             ({"a": 1, "b": 2}, ["a", "b", "c", "b"])   # strings, repetitions
                         ])
def test_compare_lists_subset_success(expected_subset, actual):
    assert JsonCompareHelper.compare_lists_subset(expected_subset, actual) is None


@pytest.mark.parametrize("expected_subset, actual",
                         [
                             # 'expected_subset' as a list
                             ([1, 2, 3], [3, 2, 4]),                 # ints, missing item
                             ([1, 2], [1, 3, 2, 1]),                 # ints, additional repetition
                             (["a", "b", "c"], ["c", "b", "d"]),     # strings, missing item
                             (["a", "b"], ["a", "c", "b", "a"]),     # strings, additional item

                             # 'expected_subset' as a map of repetitions
                             ({1: 1, 2: 1, 3: 1}, [3, 2, 4]),               # ints, missing item
                             ({1: 1, 2: 2}, [1, 3, 2, 1]),                  # ints, additional item
                             ({"a": 1, "b": 1, "c": 1}, ["c", "b", "d"]),   # strings, missing item
                             ({"a": 1, "b": 1}, ["a", "c", "b", "a"])       # strings, additional item
                         ])
def test_compare_lists_subset_fail(expected_subset, actual):
    with pytest.raises(ValueError):
        JsonCompareHelper.compare_lists_subset(expected_subset, actual)


@pytest.mark.parametrize("expected_superset, actual",
                         [
                             # 'expected_superset' as a list
                             ([1, 2, 3], [1, 3, 2]),                    # ints, 'expected' and 'actual' are the same, no repetitions
                             ([1, 2, 3], [1, 3]),                       # ints, 'expected' is superset of 'actual', no repetitions
                             ([1, 2, 2], [2, 1, 2]),                    # ints, 'expected' and 'actual' are the same, w/ repetitions
                             ([1, 2, 2, 3], [1, 2, 3, 2]),              # ints, 'expected' is supersset of 'actual', w/ repetitions
                             (["a", "b", "c"], ["a", "c", "b"]),        # strings, 'expected' and 'actual' are the same, no repetitions
                             (["a", "b", "c"], ["a", "c"]),             # strings, 'expected' is a superset of 'actual', no repetitions
                             (["a", "b", "b"], ["b", "a", "b"]),        # strings, 'expected' and 'actual' are the same, w/ repetitions
                             (["a", "b", "b", "c"], ["b", "a", "b"]),   # strings, 'expected' is superset of 'actual', w/ repetitions

                             # 'expected_superset' as a map of repetitions
                             ({1: 1, 2: 1, 3: 1}, [1, 3, 2]),      # ints, 'expected' and 'actual' are the same, no repetitions
                             ({1: 1, 2: 1, 3: 1}, [1, 3]),         # ints, 'expected' is superset of 'actual', no repetitions
                             ({1: 1, 2: 2}, [1, 2, 2]),            # ints, 'expected' and 'actual' are the same, w/ repetitions
                             ({1: 1, 2: 2, 3: 1}, [1, 2, 3, 2]),   # ints, 'expected' is supersset of 'actual', w/ repetitions

                             ({"a": 1, "b": 1, "c": 1}, ["a", "c", "b"]),  # strings, 'expected' and 'actual' are the same, no repetitions
                             ({"a": 1, "b": 2, "c": 1}, ["a", "c"]),       # strings, 'expected' is a superset of 'actual', no repetitions
                             ({"a": 1, "b": 2}, ["b", "a", "b"]),          # strings, 'expected' and 'actual' are the same, w/ repetitions
                             ({"a": 1, "b": 2, "c": 1}, ["b", "a", "b"])   # strings, 'expected' is superset of 'actual', w/ repetitions
                         ])
def test_compare_lists_superset_success(expected_superset, actual):
    assert JsonCompareHelper.compare_lists_superset(expected_superset, actual) is None


@pytest.mark.parametrize("expected_superset, actual",
                         [
                             # 'expected_superset' as a list
                             ([1, 2, 3], [2, 1, 4]),               # ints, additional item
                             ([1, 2, 3], [1, 3, 2, 2]),            # ints, additional repetition
                             (["a", "b", "c"], ["b", "a", "d"]),   # strings, additional item
                             (["a", "b"], ["a", "b", "a"]),        # strings, additional repetition

                             # 'expected_superset' as a map of repetitions
                             ({1: 1, 2: 1, 3: 1}, [2, 1, 4]),               # ints, additional item
                             ({1: 1, 2: 1, 3: 1}, [1, 3, 2, 2]),            # ints, additional repetition
                             ({"a": 1, "b": 1, "c": 1}, ["b", "a", "d"]),   # strings, additional item
                             ({"a": 1, "b": 1}, ["a", "b", "a"])            # strings, additional repetition
                         ])
def test_compare_lists_superset_fail(expected_superset, actual):
    with pytest.raises(ValueError):
        JsonCompareHelper.compare_lists_superset(expected_superset, actual)


@pytest.mark.parametrize("regex, expected_result_list",
                         [
                             # regex as a string
                             ("^notfound--", []),                                                  # regex matches 0 of 4 items
                             ("^identity--", ["identity--678"]),                                   # regex matches 1 of 4 items
                             ("^malware--", ["malware--123", "malware--123456", "malware--354"]),  # regex matches 3 of 4 items
                             ("^malware--123", ["malware--123", "malware--123456"]),               # partial match from start of string
                             ("malware--123", ["malware--123", "malware--123456"]),                # partial match from anywhere in the string
                             ("^malware--123$", ["malware--123"]),                                 # exact match from start to end of string

                             # regex as a compiled pattern object
                             (re.compile("^notfound--"), []),                                                 # regex matches 0 of 4 items
                             (re.compile("^identity--"), ["identity--678"]),                                  # regex matches 1 of 4 items
                             (re.compile("^malware--"), ["malware--123", "malware--123456", "malware--354"])  # regex matches 3 of 4 items
                         ])
def test_create_list_from_list_using_filter_regex(regex, expected_result_list: list):
    source_list = ["malware--123", "malware--123456", "identity--678", "malware--354"]
    actual_result_list = JsonCompareHelper.create_list_from_list_using_filter_regex(regex, source_list)
    assert len(expected_result_list) == len(actual_result_list)

    # check that lists are equivalent including number of repetitions but ignoring order
    json_compare_helper = JsonCompareHelper()
    assert json_compare_helper.compare_lists(expected_result_list, actual_result_list, "test") is None
