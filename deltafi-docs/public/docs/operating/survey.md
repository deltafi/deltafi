# DeltaFi Survey Capability

DeltaFi provides a survey capability that can be used to monitor data before it enters the system. You can use survey to report metadata about data that you are considering sending through DeltaFi.

To use survey, you can submit a POST request to the `/survey` endpoint, passing in the following query parameters:

- `flow`: The name of the flow being surveyed (required).
- `bytes`: The number of bytes being surveyed (optional).
- `files`: The number of files being surveyed (optional).
- `subflow`: The name of the subflow being surveyed (optional).
- `direction`: The direction of the data flow (optional, default is "none").

Example usage:

```bash
curl -X POST "http://localhost:8080/survey?flow=myflow&bytes=100000&files=5&subflow=subflow1&direction=incoming"
     -H "Content-Type: application/json"
```

When a survey request is received, DeltaFi logs the survey data and increments the following metrics:

- `deltafi.survey.files`: The number of files surveyed.
- `deltafi.survey.bytes`: The number of bytes surveyed.
- `deltafi.survey.subflow.files`: The number of files surveyed in the subflow (if a subflow is specified).
- `deltafi.survey.subflow.bytes`: The number of bytes surveyed in the subflow (if a subflow is specified).

You can view these metrics in your DeltaFi monitoring dashboard.

