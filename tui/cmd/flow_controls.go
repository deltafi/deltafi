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
package cmd

import (
	"fmt"
	"github.com/deltafi/tui/graphql"
)

var flowDisplay = map[graphql.FlowType]string{
	graphql.FlowTypeRestDataSource:  "rest data source",
	graphql.FlowTypeTimedDataSource: "timed data source",
	graphql.FlowTypeTransform:       "transform",
	graphql.FlowTypeDataSink:        "data sink",
}

func startFlow(flowType graphql.FlowType, flowName string) error {
	_, err := setFlowState(flowType, flowName, graphql.FlowStateRunning)

	display := flowDisplay[flowType]
	if err != nil {
		return wrapInError("Could not start "+display+" named "+flowName, err)
	}

	fmt.Println("Successfully started " + display + " named " + flowName)
	return nil
}

func stopFlow(flowType graphql.FlowType, flowName string) error {
	_, err := setFlowState(flowType, flowName, graphql.FlowStateStopped)

	display := flowDisplay[flowType]
	if err != nil {
		return wrapInError("Could not stop "+display+" named "+flowName, err)
	}

	fmt.Println("Successfully stopped " + display + " named " + flowName)
	return nil
}

func pauseFlow(flowType graphql.FlowType, flowName string) error {
	_, err := setFlowState(flowType, flowName, graphql.FlowStatePaused)

	display := flowDisplay[flowType]
	if err != nil {
		return wrapInError("Could not pause "+display+" named "+flowName, err)
	}

	fmt.Println("Successfully paused " + display + " named " + flowName)
	return nil
}

func setFlowState(flowType graphql.FlowType, flowName string, state graphql.FlowState) (*graphql.SetFlowStateResponse, error) {
	return graphql.SetFlowState(flowType, flowName, state)
}
