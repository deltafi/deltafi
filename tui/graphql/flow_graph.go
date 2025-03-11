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
package graphql

const flowGraphQuery = `
query GetFlowGraph($flowName: String!) {
  getFlowGraph(flowName: $flowName) {
    dataSources {
      name
      type
      flowStatus {
        state
        testMode
      }
      publishTopics
    }
    transforms {
      name
      flowStatus {
        state
        testMode
      }
      subscribe {
        topic
        condition
      }
      publishTopics
    }
    dataSinks {
      name
      flowStatus {
        state
        testMode
      }
      subscribe {
        topic
        condition
      }
    }
  }
}`

type FlowGraphResponse struct {
	GetFlowGraph struct {
		DataSources []struct {
			Name          string     `json:"name"`
			Type          string     `json:"type"`
			FlowStatus    FlowStatus `json:"flowStatus"`
			PublishTopics []string   `json:"publishTopics"`
		} `json:"dataSources"`
		Transforms []struct {
			Name          string         `json:"name"`
			FlowStatus    FlowStatus     `json:"flowStatus"`
			Subscribe     []Subscription `json:"subscribe"`
			PublishTopics []string       `json:"publishTopics"`
		} `json:"transforms"`
		DataSinks []struct {
			Name       string         `json:"name"`
			FlowStatus FlowStatus     `json:"flowStatus"`
			Subscribe  []Subscription `json:"subscribe"`
		} `json:"dataSinks"`
	} `json:"getFlowGraph"`
}

type FlowStatus struct {
	State    string `json:"state"`
	TestMode bool   `json:"testMode"`
}

type Subscription struct {
	Topic     string `json:"topic"`
	Condition string `json:"condition"`
}

type flowGraphResponse struct {
	GetFlowGraph FlowGraphResponse `json:"getFlowGraph"`
}

func (r *flowGraphResponse) Extensions() map[string]interface{} {
	return nil
}

// GetFlowGraphData is a helper function that wraps the generated GetFlowGraph query
func GetFlowGraphData(flowName string) (*GetFlowGraphResponse, error) {
	return GetFlowGraph()
}
