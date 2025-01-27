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
package api

type image struct {
	Name string `json:"name"`
	Tag  string `json:"tag"`
}

type version struct {
	App       string `json:"app"`
	Container string `json:"container"`
	Group     string `json:"group"`
	Image     image  `json:"image"`
}

type VersionsResponse struct {
	Versions []version `json:"versions"`
}

func (v *VersionsResponse) ToTable() Table {
	table := Table{
		Columns: []string{"App", "Container", "Image", "Version"},
		Rows:    [][]string{},
	}

	for _, version := range v.Versions {
		table.Rows = append(table.Rows, []string{
			version.App,
			version.Container,
			version.Image.Name,
			version.Image.Tag,
		})
	}
	return table
}

func (v *VersionsResponse) ToJoinedTable() Table {
	table := Table{
		Columns: []string{"App", "Image"},
		Rows:    [][]string{},
	}

	for _, version := range v.Versions {
		table.Rows = append(table.Rows, []string{
			version.App,
			version.Image.Name + ":" + version.Image.Tag,
		})
	}
	return table
}
