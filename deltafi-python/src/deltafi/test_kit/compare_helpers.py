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

import json
import re
from abc import ABC
from abc import abstractmethod
from itertools import repeat

from deepdiff import DeepDiff, DeepSearch

from .assertions import *


class CompareHelper(ABC):
    @abstractmethod
    def compare(self, expected: str, actual: str, label: str):
        pass


class GenericCompareHelper(CompareHelper):
    def compare(self, expected: str, actual: str, label: str):
        assert_equal_with_label(expected, actual, label)


class JsonCompareHelper(CompareHelper):
    """Provides helper functions for comparing JSON/dict objects.

            Are these two JSON/dict objects equivalent?
                - compare(...)

            Are these two lists equivalent?
                - compare_lists(...)

            Is this list a subset/superset of that list?
                - compare_lists_subset(...)
                - compare_lists_superset(...)

            Select a list of values from an existing list and put them into a new list to facilitate list comparisons:
                - create_list_from_list_using_filter_regex(...)

            Is this value found (or not) in this JSON/dict object?
                - is_found(...)
                - is_not_found(...)
        """

    def __init__(self, regex_exclusion_list=None, ignore_order=True):
        """Creates and configures a JsonCompareHelper object.  If the optional 'ignore_order' is true, then the order of
                data is ignored when checking else order is enforced.  The optional 'regex_exclusion_list' excludes
                paths within the object from comparison; if empty or not provided, then no excludes are applied."""
        if regex_exclusion_list is None:
            regex_exclusion_list = []
        self.excludes = regex_exclusion_list
        self.ignore_order = ignore_order

    def __perform_find(self, obj: object, item):
        """Returns a dict of matches of the 'item' in the object 'obj'.  Both keys and values of dicts are included in
                the search.  The item may be compiled regex pattern or a string that compiles to a regex pattern.  The
                returned dict is empty if there are no matches. Excludes path(s) determined by the constructor."""
        return DeepSearch(obj, item, verbose_level=2, exclude_regex_paths=self.excludes, use_regexp=True)

    def is_not_found(self, obj: object, item):
        """Returns None if there are no occurrences of 'item' in object 'obj' else raises a ValueError.  Both keys and
                values of dicts are included in the search.  If 'item' is a list, then all elements of item must not be
                found in list, else a ValueError is raised.  The argument 'item' may be a compiled regex pattern, a
                string that compiles to a regex pattern, or a list of either or both.  Excludes path(s) and failure on
                ordering of elements are determined by the constructor."""

        all_matches = []

        if isinstance(item, list):
            for value in item:
                matches = self.__perform_find(obj, value)
                if len(matches) > 0:
                    all_matches.append(matches)
        else:
            matches = self.__perform_find(obj, item)
            if len(matches) > 0:
                all_matches.append(matches)

        if len(all_matches) > 0:
            raise ValueError("Matches found for items '" + f"{all_matches}" + "'")

        assert len(all_matches) == 0

    def is_found(self, obj: object, item):
        """Returns None if 'item' occurs in object 'obj' else raises a ValueError.  Both keys and values of dicts are
                included in the search.  If 'item' is a list, then all elements of item must occur in the object else a
                ValueError is returned.  The argument 'item' may be a compiled regex pattern, a string that compiles to
                a regex pattern, or a list of either or both. Excludes path(s) and failure on ordering of elements are
                determined by the constructor."""

        not_found_items = []

        if isinstance(item, list):
            for value in item:
                matches = self.__perform_find(obj, value)
                if len(matches) == 0:
                    not_found_items.append(value)
        else:
            matches = self.__perform_find(obj, item)
            if len(matches) == 0:
                not_found_items.append(item)

        if len(not_found_items) > 0:
            raise ValueError("No matches found for items '" + f"{not_found_items}" + "'")

        assert len(not_found_items) == 0

    def __perform_diff(self, expected, actual):
        """Returns a dict with differences between 'expected' and 'actual'.  The returned dict is empty if 'expected'
                and 'actual' are equivalent.  Both 'expected' and 'actual' must be dicts. Excludes path and failure on
                ordering of elements are determined by the constructor.  Elements must match number of repetitions."""
        return DeepDiff(expected, actual, ignore_order=self.ignore_order, report_repetition=True,
                        exclude_regex_paths=self.excludes)

    def __perform_diff_with_eval(self, expected, actual):
        """Returns None if 'expected' and 'actual' are equivalent else returns a ValueError. Both 'expected' and
                'actual' must be dicts.  Excludes path and failure on ordering of elements are determined by the
                constructor.  Elements must match number of repetitions."""

        diffs = self.__perform_diff(expected, actual)

        if len(diffs) > 0:
            raise ValueError(f"{diffs}")

        assert len(diffs) == 0

    def compare(self, expected, actual, label: str):
        """Returns None if 'expected' and 'actual' are equivalent else returns a ValueError. Both 'expected' and
                'actual' must be either dicts or strings that parse as JSON to dicts. Excludes path and failure on
                ordering of elements are determined by the constructor.  Elements must match number of repetitions."""

        if isinstance(expected, str):
            exp = json.loads(expected)
        else:
            exp = expected

        if isinstance(actual, str):
            act = json.loads(actual)
        else:
            act = actual

        return self.__perform_diff_with_eval(exp, act)

    def compare_lists(self, expected, actual: list, label: str):
        """Returns None if 'actual' is equivalent to 'expected' else returns a ValueError.

                The 'actual' argument must be a list.  The 'expected' argument may be a list or a dict.  If a list, then
                'expected' and 'actual' are compared against each other.  If a dict, then 'actual' is equivalent if it
                contains elements with the same repetitions as defined in the 'expected' dict with the key equal to the
                 element in 'actual' and the value equal to the number of repetitions.

                Order of elements is ignored.  Elements must match number of repetitions."""

        expected_list = []

        if isinstance(expected, dict):
            for key, value in expected.items():
                expected_list += list(repeat(key, value))
        else:
            expected_list = expected

        return self.__perform_diff_with_eval({"json-compare-helper-internal-list": expected_list},
                                             {"json-compare-helper-internal-list": actual})

    @staticmethod
    def compare_lists_subset(expected_subset, actual: list):
        """Returns None if the 'actual' list contains at least 'expected_subset' else returns a ValueError.  The
                'actual' list may contain 0 or more additional elements than defined in 'expected_subset'.

                The 'actual' argument must be a list.  The argument 'expected_subset' may be a list or a dict.  In the
                latter case, the key defines the item that must appear in 'actual' and the value defines the number of
                 repetitions.

                Order of elements is ignored.  Elements must match number of repetitions."""

        expected_subset_map = {}

        if isinstance(expected_subset, list):
            for item in expected_subset:
                value = expected_subset_map.get(item)
                if value is None:
                    expected_subset_map[item] = 1
                else:
                    expected_subset_map[item] = value + 1
        else:
            expected_subset_map = expected_subset

        actual_map = {}

        for item in actual:
            value = actual_map.get(item)
            if value is None:
                actual_map[item] = 1
            else:
                actual_map[item] = value + 1

        for key, value in expected_subset_map.items():
            actual_value = actual_map.get(key)
            if actual_value is None:
                raise ValueError("Actual list did not contain element '" + str(key) + "'")
            else:
                if actual_value != value:
                    raise ValueError("Actual list had item '" + str(key) + "' with repetition " + str(actual_value)
                                     + " but required repetition " + str(value))

        return None

    @staticmethod
    def compare_lists_superset(expected_superset, actual: list):
        """Returns None if the 'actual' list contains only elements that appear in 'expected_superset' else returns a
                ValueError.  The 'actual' list cannot contain more elements than are defined in 'expected_superset'; the
                'expected_superset' may define 0 or more values not contained in 'actual'.

                The 'actual' argument must be a list.  The argument 'expected_superset' may be a list or a dict.  In the
                latter case, the key defines the item that must appear in 'actual' and the value defines the number of
                 repetitions.

                Order of elements is ignored.  Elements must match number of repetitions."""

        expected_superset_map = {}

        if isinstance(expected_superset, list):
            for item in expected_superset:
                value = expected_superset_map.get(item)
                if value is None:
                    expected_superset_map[item] = 1
                else:
                    expected_superset_map[item] = value + 1
        else:
            expected_superset_map = expected_superset

        actual_map = {}

        for item in actual:
            value = actual_map.get(item)
            if value is None:
                actual_map[item] = 1
            else:
                actual_map[item] = value + 1

        for key, value in actual_map.items():
            expected_value = expected_superset_map.get(key)
            if expected_value is None:
                raise ValueError("Actual list contained element '" + str(key)
                                 + "' that did not appear in the expected superset")
            else:
                if expected_value != value:
                    raise ValueError("Actual list had item '" + str(key) + "' with repetition " + str(value)
                                     + " but required repetition " + str(expected_value))

        return None

    @staticmethod
    def create_list_from_list_using_filter_regex(regex, source_list: list):
        """Creates and returns a list of those items in the 'source_list' matching the regex in the 'regex'.  The
                argument 'regex' may be a string or a compiled regex object.

                For the 'regex', consider using anchors to explicitly match from the start (or end) of a string.  For
                example, a string such as "^malware--" will explicitly match from the start of a string."""

        def search_funct(element):

            if isinstance(regex, str):
                # 'regex' is a string
                match = re.search(regex, element)
            else:
                # assume 'regex' is a compiled regex pattern object
                match = regex.match(element)

            if match is None:
                return False
            else:
                return True

        return list(filter(search_funct, source_list))

