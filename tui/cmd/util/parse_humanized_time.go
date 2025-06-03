/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package util

import (
	"fmt"
	"regexp"
	"strconv"
	"strings"
	"time"
)

// ParseHumanizedTime converts humanized time strings to time.Time objects
func ParseHumanizedTime(input string) (time.Time, error) {
	now := time.Now()
	input = strings.ToLower(strings.TrimSpace(input))

	// Handle simple cases first
	switch input {
	case "now":
		return now, nil
	case "today":
		return time.Date(now.Year(), now.Month(), now.Day(), 0, 0, 0, 0, now.Location()), nil
	case "yesterday":
		return now.AddDate(0, 0, -1), nil
	case "tomorrow":
		return now.AddDate(0, 0, 1), nil
	case "everbefore":
		return time.Date(1970, 1, 1, 0, 0, 0, 0, now.Location()), nil
	case "beginning":
		return time.Date(1970, 1, 1, 0, 0, 0, 0, now.Location()), nil
	case "end":
		return time.Date(9999, 12, 31, 23, 59, 59, 0, now.Location()), nil
	case "forever":
		return time.Date(9999, 12, 31, 23, 59, 59, 0, now.Location()), nil
	case "always":
		return time.Date(9999, 12, 31, 23, 59, 59, 0, now.Location()), nil
	}

	// Regular expressions for different humanizedTimePatterns
	humanizedTimePatterns := []struct {
		regex *regexp.Regexp
		parse func([]string) (time.Time, error)
	}{
		// "X days ago", "X weeks ago", etc.
		{
			regexp.MustCompile(`^(\d+)\s+(second|minute|hour|day|week|month|year)s?\s+ago$`),
			func(matches []string) (time.Time, error) {
				num, err := strconv.Atoi(matches[1])
				if err != nil {
					return time.Time{}, err
				}
				unit := matches[2]
				return subtractTime(now, num, unit), nil
			},
		},
		// "in X days", "in X weeks", etc.
		{
			regexp.MustCompile(`^in\s+(\d+)\s+(second|minute|hour|day|week|month|year)s?$`),
			func(matches []string) (time.Time, error) {
				num, err := strconv.Atoi(matches[1])
				if err != nil {
					return time.Time{}, err
				}
				unit := matches[2]
				return addTime(now, num, unit), nil
			},
		},
		// "last week", "next month", etc.
		{
			regexp.MustCompile(`^(last|next)\s+(week|month|year)$`),
			func(matches []string) (time.Time, error) {
				direction := matches[1]
				unit := matches[2]
				multiplier := 1
				if direction == "last" {
					multiplier = -1
				}
				return addTime(now, multiplier, unit), nil
			},
		},
		// "a day ago", "an hour ago", etc.
		{
			regexp.MustCompile(`^an?\s+(second|minute|hour|day|week|month|year)\s+ago$`),
			func(matches []string) (time.Time, error) {
				unit := matches[1]
				return subtractTime(now, 1, unit), nil
			},
		},
	}

	// Try each pattern
	for _, pattern := range humanizedTimePatterns {
		matches := pattern.regex.FindStringSubmatch(input)
		if matches != nil {
			return pattern.parse(matches)
		}
	}

	if retval, err := time.Parse(time.DateOnly, input); err == nil {
		return retval, nil
	} else if retval, err := time.Parse(time.TimeOnly, input); err == nil {
		return retval, nil
	} else if retval, err := time.Parse(time.DateTime, input); err == nil {
		return retval, nil
	} else if retval, err := time.Parse(time.RFC3339, input); err == nil {
		return retval, nil
	}

	return time.Time{}, fmt.Errorf("unable to parse time expression: %s", input)
}

// subtractTime subtracts the specified amount from the given time
func subtractTime(t time.Time, amount int, unit string) time.Time {
	switch unit {
	case "second":
		return t.Add(-time.Duration(amount) * time.Second)
	case "minute":
		return t.Add(-time.Duration(amount) * time.Minute)
	case "hour":
		return t.Add(-time.Duration(amount) * time.Hour)
	case "day":
		return t.AddDate(0, 0, -amount)
	case "week":
		return t.AddDate(0, 0, -amount*7)
	case "month":
		return t.AddDate(0, -amount, 0)
	case "year":
		return t.AddDate(-amount, 0, 0)
	default:
		return t
	}
}

// addTime adds the specified amount to the given time
func addTime(t time.Time, amount int, unit string) time.Time {
	switch unit {
	case "second":
		return t.Add(time.Duration(amount) * time.Second)
	case "minute":
		return t.Add(time.Duration(amount) * time.Minute)
	case "hour":
		return t.Add(time.Duration(amount) * time.Hour)
	case "day":
		return t.AddDate(0, 0, amount)
	case "week":
		return t.AddDate(0, 0, amount*7)
	case "month":
		return t.AddDate(0, amount, 0)
	case "year":
		return t.AddDate(amount, 0, 0)
	default:
		return t
	}
}
