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
package containers

import (
	"context"
	"sort"
	"strings"
	"time"

	"github.com/Masterminds/semver/v3"
	"oras.land/oras-go/v2/registry/remote"
)

func ListTags(context context.Context, imageRef string) ([]string, error) {
	repo, err := remote.NewRepository(imageRef)
	if err != nil {
		return nil, err
	}

	tags := []string{}

	err = repo.Tags(context, "", func(t []string) error {
		tags = append(tags, t...)
		return nil
	})

	time.Sleep(1 * time.Second)

	if err != nil {
		return nil, err
	}

	return tags, nil
}

func ListNewerVersions(context context.Context, imageRef string, version *semver.Version, limit int) ([]*semver.Version, error) {

	versions := []*semver.Version{}
	tags, err := ListTags(context, imageRef)
	if err != nil {
		return nil, err
	}

	for _, tag := range tags {
		if strings.Contains(tag, "linux") || strings.Contains(tag, "darwin") {
			continue
		}

		tagVersion, err := semver.NewVersion(tag)
		if err != nil {
			return nil, err
		}

		if tagVersion.Compare(version) > 0 {
			versions = append(versions, tagVersion)
		}
	}

	// Sort versions in descending order
	sort.Slice(versions, func(i, j int) bool {
		return versions[i].Compare(versions[j]) > 0
	})

	if limit > 0 && len(versions) > limit {
		versions = versions[:limit]
	}

	return versions, nil
}
