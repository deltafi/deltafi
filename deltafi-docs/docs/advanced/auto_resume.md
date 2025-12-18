# Automatic Resume

A DeltaFile may encounter an error during action execution. When this occurs, the DeltaFile is placed in an ERROR stage, and no further actions will be executed. A DeltaFile can be manually resumed using the UI, which will attempt the failed action again. This works well for the occasional error, but for errors that occur regularly, an automatic resume policy can be created. This allows the DeltaFile to be scheduled for an automatic resume based on configurable delay settings.

## Resume Policy Fields

A Resume Policy consists of the following fields:

| Field | Required | Description |
|-------|----------|-------------|
| `id` | No | Auto-generated UUID if not provided |
| `name` | Yes | Unique name for the policy |
| `errorSubstring` | No* | Substring to match in the error cause |
| `dataSource` | No* | Data source name to match |
| `action` | No* | Full action name to match (e.g., `my-flow.MyAction`) |
| `maxAttempts` | Yes | Maximum number of retry attempts (must be > 1) |
| `priority` | No | Priority for matching (higher = matched first). Auto-calculated if not provided |
| `backOff` | Yes | Back-off configuration for retry delays |

*At least one of `errorSubstring`, `dataSource`, or `action` must be specified.

### BackOff Configuration

| Field | Required | Description |
|-------|----------|-------------|
| `delay` | Yes | Base delay in seconds before retry |
| `maxDelay` | No | Maximum delay in seconds (required if `random` is true) |
| `multiplier` | No | Multiply delay by this value times the attempt number |
| `random` | No | If true, use a random delay between `delay` and `maxDelay` |

## Matching Behavior

When an action error occurs, the details of the error are compared to all resume policies in decreasing priority order. For a policy to match:

1. The number of attempts for that action must be below `maxAttempts`
2. If `errorSubstring` is set, the error cause must contain it (substring match)
3. If `dataSource` is set, it must exactly match the DeltaFile's data source
4. If `action` is set, it must exactly match the action name (including flow prefix)

All specified criteria must match (AND logic). Only the first matching policy is applied.

### Priority Calculation

If `priority` is not specified, it is automatically calculated based on the specificity of the match criteria:

| Criteria | Points |
|----------|--------|
| `errorSubstring` with 11+ characters | 100 |
| `errorSubstring` with fewer characters | 50 |
| `action` specified | 100 |
| `dataSource` specified | 50 |

More specific policies should have higher priority to ensure they match before less specific ones.

## Delay Calculation

The retry delay is calculated based on the `backOff` configuration:

**Fixed delay:**
```json
{ "delay": 60 }
```
Always waits 60 seconds before retry.

**Exponential backoff:**
```json
{ "delay": 30, "maxDelay": 300, "multiplier": 2 }
```
- Attempt 1: 30 × 2 × 1 = 60 seconds
- Attempt 2: 30 × 2 × 2 = 120 seconds
- Attempt 3: 30 × 2 × 3 = 180 seconds
- Attempt 4: 30 × 2 × 4 = 240 seconds
- Attempt 5: 30 × 2 × 5 = 300 seconds (capped at maxDelay)

**Random jitter:**
```json
{ "delay": 60, "maxDelay": 120, "random": true }
```
Each retry waits a random time between 60 and 120 seconds. Useful for avoiding thundering herd problems with rate limits.

## Background Task

DeltaFi periodically searches for DeltaFiles ready for automatic resume. This frequency is controlled by the `autoResumeCheckFrequency` system property. There may be a small delay between a DeltaFile's scheduled resume time and the actual resume.

## Examples

### Retry Timeouts

Retry any action that fails with a timeout error, up to 3 times with a 1-minute delay:

```json
{
  "name": "retry-timeouts",
  "errorSubstring": "timeout",
  "maxAttempts": 3,
  "backOff": {
    "delay": 60
  }
}
```

### Retry Connection Errors with Exponential Backoff

Retry connection errors with increasing delays:

```json
{
  "name": "retry-connection-errors",
  "errorSubstring": "connection refused",
  "maxAttempts": 5,
  "backOff": {
    "delay": 30,
    "maxDelay": 300,
    "multiplier": 2
  }
}
```

### Retry Rate Limits with Jitter

Retry rate limit errors with random delays to avoid synchronized retries:

```json
{
  "name": "retry-rate-limits",
  "errorSubstring": "429",
  "maxAttempts": 10,
  "backOff": {
    "delay": 60,
    "maxDelay": 300,
    "random": true
  }
}
```

### Retry Specific Data Source

Retry all errors from a flaky external API:

```json
{
  "name": "retry-external-api",
  "dataSource": "external-api-source",
  "maxAttempts": 5,
  "backOff": {
    "delay": 120,
    "maxDelay": 600,
    "multiplier": 2
  }
}
```

### Retry Specific Action

Retry failures from a specific egress action:

```json
{
  "name": "retry-http-egress",
  "action": "my-flow.HttpEgressAction",
  "maxAttempts": 3,
  "backOff": {
    "delay": 60
  }
}
```

### High-Priority Specific Match

A specific error in a specific flow, with high priority to match before generic rules:

```json
{
  "name": "partner-validation-retry",
  "dataSource": "partner-feed",
  "action": "partner-feed.ValidateAction",
  "errorSubstring": "schema validation failed",
  "maxAttempts": 2,
  "priority": 200,
  "backOff": {
    "delay": 300
  }
}
```

## CLI Management

Auto resume rules can be managed using the `deltafi auto-resume` command (alias: `deltafi ar`).

### Listing Rules

```bash
deltafi auto-resume list
```

### Creating Rules

Save your rule definition to a JSON or YAML file and load it:

```bash
deltafi auto-resume load my-rule.json
```

You can load multiple files at once, or a file containing an array of rules:

```bash
deltafi auto-resume load rule1.json rule2.json rule3.json
```

To replace all existing rules with a new set:

```bash
deltafi auto-resume load --replace-all rules.json
```

### Viewing a Rule

```bash
deltafi auto-resume get my-rule
deltafi auto-resume get my-rule -o yaml
```

### Exporting Rules

Export all rules for backup or migration:

```bash
deltafi auto-resume export > rules.json
deltafi auto-resume export -o yaml > rules.yaml
```

### Deleting Rules

Delete a specific rule by name:

```bash
deltafi auto-resume delete my-rule
```

Delete all rules:

```bash
deltafi auto-resume clear
```

Use `-f` to skip confirmation prompts.

### Applying Rules to Existing Errors

When you create a new rule, it will only apply to future errors. To apply rules to existing errored DeltaFiles:

```bash
deltafi auto-resume apply my-rule
deltafi auto-resume apply rule1 rule2 rule3
```

### Testing Rules

Test a rule against existing errors without saving it:

```bash
deltafi auto-resume dry-run my-rule.json
```

This will report how many DeltaFiles would match the rule.
