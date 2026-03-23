// ABOUTME: Tests for the HelloWorldGoTimedIngressAction.
// ABOUTME: Covers memo-based index tracking, conditional metadata, and join-group assignment.

package actions

import (
	"context"
	"fmt"
	"strconv"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	actionkit "gitlab.com/deltafi/deltafi/deltafi-go-action-kit/v2"
)

func newIngressInput(memo string) (actionkit.IngressInput, *actionkit.TestContext) {
	tc := actionkit.NewTestContext("ingress-did")
	tc.FlowName = "hello-world-go-timed-data-source"
	tc.Memo = memo
	return actionkit.IngressInput{ActionContext: tc.ActionContext}, tc
}

func TestTimedIngress_FirstRun(t *testing.T) {
	input, tc := newIngressInput("")
	event := actionkit.RunIngress(t, &HelloWorldGoTimedIngress{}, input)
	ingress := event.RequireIngress(t)

	require.Equal(t, "0", ingress.Memo)
	assert.False(t, ingress.ExecuteImmediate)
	require.Len(t, ingress.IngressItems, 1)

	item := ingress.IngressItems[0]
	assert.Equal(t, "hello-world-go-timed-data-source-0", item.DeltaFileName)
	assert.Equal(t, "0", item.Metadata["index"])

	// index 0: index%3 == 0, no hello/helloMode metadata
	assert.NotContains(t, item.Metadata, "hello")

	// Verify content was saved
	require.Len(t, item.Content, 1)
	data, err := item.Content[0].LoadString(context.Background(), tc.Store)
	require.NoError(t, err)
	assert.Equal(t, "Some content, part 0", data)
}

func TestTimedIngress_IncrementsMemo(t *testing.T) {
	input, _ := newIngressInput("4")
	event := actionkit.RunIngress(t, &HelloWorldGoTimedIngress{}, input)
	ingress := event.RequireIngress(t)

	assert.Equal(t, "5", ingress.Memo)
	assert.Equal(t, "5", ingress.IngressItems[0].Metadata["index"])
}

func TestTimedIngress_Index1_TransformMetadata(t *testing.T) {
	// index%3 == 1 → hello=transform, helloMode=basic
	input, _ := newIngressInput("0") // memo 0 → index 1
	event := actionkit.RunIngress(t, &HelloWorldGoTimedIngress{}, input)
	item := event.RequireIngress(t).IngressItems[0]

	assert.Equal(t, "transform", item.Metadata["hello"])
	assert.Equal(t, "basic", item.Annotations["helloMode"])
}

func TestTimedIngress_Index2_JoinMetadata(t *testing.T) {
	// index%3 == 2 → hello=join, helloMode=advanced, join-group based on even/odd
	input, _ := newIngressInput("1") // memo 1 → index 2
	event := actionkit.RunIngress(t, &HelloWorldGoTimedIngress{}, input)
	item := event.RequireIngress(t).IngressItems[0]

	assert.Equal(t, "join", item.Metadata["hello"])
	assert.Equal(t, "advanced", item.Annotations["helloMode"])
	assert.Equal(t, "even", item.Metadata["join-group"])
}

func TestTimedIngress_Index5_JoinGroupOdd(t *testing.T) {
	// index 5: index%3==2, index%2==1 → join-group=odd
	input, _ := newIngressInput("4") // memo 4 → index 5
	event := actionkit.RunIngress(t, &HelloWorldGoTimedIngress{}, input)
	item := event.RequireIngress(t).IngressItems[0]

	assert.Equal(t, "join", item.Metadata["hello"])
	assert.Equal(t, "odd", item.Metadata["join-group"])
}

func TestTimedIngress_AllIndexModPatterns(t *testing.T) {
	action := &HelloWorldGoTimedIngress{}

	tests := []struct {
		memo      string
		wantIndex int
		wantHello string
		wantGroup string
	}{
		{"", 0, "", ""},           // index%3==0: no hello
		{"0", 1, "transform", ""}, // index%3==1: transform
		{"1", 2, "join", "even"},  // index%3==2, even
		{"2", 3, "", ""},          // index%3==0
		{"3", 4, "transform", ""}, // index%3==1
		{"4", 5, "join", "odd"},   // index%3==2, odd
	}

	for _, tt := range tests {
		t.Run(fmt.Sprintf("memo=%s", tt.memo), func(t *testing.T) {
			input, _ := newIngressInput(tt.memo)
			event := actionkit.RunIngress(t, action, input)
			item := event.RequireIngress(t).IngressItems[0]

			gotIndex, _ := strconv.Atoi(item.Metadata["index"])
			assert.Equal(t, tt.wantIndex, gotIndex)
			assert.Equal(t, tt.wantHello, item.Metadata["hello"])
			if tt.wantGroup != "" {
				assert.Equal(t, tt.wantGroup, item.Metadata["join-group"])
			}
		})
	}
}
