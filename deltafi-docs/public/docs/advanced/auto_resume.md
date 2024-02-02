# Automatic Resume

A DeltaFile may encounter an error during action execution. When this occurs, the DeltaFile is placed in an ERROR stage,
and no further actions will be executed. A DeltaFile can be manually resumed using the UI, which will attempt the failed
action again. This works well for the occasional error, but for errors that occur regularly, an automatic resume policy
can be created. This allows the DeltaFile to be scheduled for an automatic resume based on configurable delay settings.

## Resume Policies

A Resume Policy consist of the following:

* id (auto-generated UUID)
* name
* errorSubstring
* flow (sourceInfo flow)
* action (full action name)
* actionType
* maxAttempts
* priority
* backOff

The backOff properties are:

* delay
* maxDelay
* multiplier
* random

## Behavior

When an action error occurs, the details of the error are compared to all resume policies in decreasing priority order. In order for there to be a match, the error cause, sourceInfo flow, action, and/or action type from the action error must match
the set of those fields included in the resume policy.
For the error cause search, the comparison uses a substring match of the policy `errorSubstring`;
the other fields require an exact match.
In addition, the number of attempt for that action must be below the `maxAttempts` value of the resume policy.
The value for `action` must include prefixed flow name (e.g., `smoke.SmokeFormatAction`). A resume policy for `NoEgressFlowConfiguredAction` does not require the flow name prefix.

It is possible for more than one resume policy to match an action error. Only the first policy that matches will be applied to the DeltaFile. Because of this, careful consideration should be used when setting the `priority` of your resume policy. An initial  `priority` will be calculated for each resume rule upon creation based on the complexity of the match criteria.
The more specific the policy, the higher the priority.
Calculated priority ranges from 5o to 250.
An `errorSubstring` with at least 11 characters is worth 100; shorter values are worth 50.
If the `action` is specified, that is worth 100.
However, if `action` is not included,  `actionType` is worth 50 when set.
If the `flow` is specified, that is worth 50.


When a resume policy match is found, an automatic resume of the DeltaFile is scheduled. The `name` of the resume policy which was applied is recorded in the DeltaFile `nextAutoResumeReason` field.

Scheduling is determined using the `backOff` properties. The only required property is `delay`. When this is the only
field present, the DeltaFile is scheduled for automatic resume by adding the `delay` (in seconds) to the stop time of
the action which encountered the error.

When `random` is TRUE, the calculated delay is a random value between `delay` and `maxDelay`. `maxDelay` is required
when `random` is TRUE.

When `multiplier` is set, the delay is calculated by multiplying the `delay`, `multiplier`, and number of attempts for
that action. Optionally, this can be capped when the `maxDelay` is set.

For instance, if the backOff is:

```json
{
  "delay": 100,
  "maxDelay": 500,
  "multiplier": 2,
  "random": false
}
```

Then the delays will be computed as follows:

* 1 attempt: 200; 100 * 2 * 1
* 2 attempts: 400; 100 * 2 * 2
* 3 attempts: 500; 100 * 2 * 3 (600), but `maxDelay` is 500

## Task

DeltaFi will frequently search for DeltaFiles that are ready to be automatically resumed. This frequency is controlled
by the `autoResumeCheckFrequency` system property. Because this search does not run constantly, there may be a small
delay between a DeltaFile's scheduled automatic resume time, and the actual time it is resumed.

## Examples

The following automatic resume policy will match on any action in the `passthrough` flow. It will allow an errored
action to be RETRIED up to 10 times. The scheduling for attempts will increase by one minute (60 seconds) each time, up
to a maximum of 5 minutes (300 seconds).

```json
{
  "id": "88bc7429-7adf-4bb1-b23f-3922993e0a1a",
  "name": "auto-resume-passthrough",
  "flow": "passthrough",
  "maxAttempts": 10,
  "priority": 50,
  "backOff": {
    "delay": 60,
    "maxDelay": 300,
    "multiplier": 1
  }
}
```

The automatic resume policy below will be triggered by any ENRICH action that produces an error cause containing the
string `JsonException`. The action will be attempted up to 4 times, with a random delay between 60-120 seconds.

```json
{
  "id": "a2b08968-866a-4080-bc28-1d7e7c81ada8",
  "name": "resume-json-errors",
  "errorSubstring": "JsonException",
  "actionType": "TRANSFORM",
  "maxAttempts": 4,
  "priority": 150,
  "backOff": {
    "delay": 60,
    "maxDelay": 120,
    "random": true
  }
}
```
