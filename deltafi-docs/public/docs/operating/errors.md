# DeltaFile Error Handling and Recovery

When an Action fails during DeltaFile processing, the corresponding DeltaFile enters an ERROR state. The errored DeltaFile is then displayed in the DeltaFi UI with information on the cause of the error and additional context supplied by the Action.

DeltaFi provides three methods for dealing with errors:

## Resume

The first method for recovering from errors is called Resume. When you Resume an errored DeltaFile, the Actions that caused the error are restarted, meaning DeltaFile processing picks up where it left off.

To Resume an errored DeltaFile, follow these steps:

- Navigate to the Errors page on the DeltaFi UI.
- Right click on the DeltaFile and click resume. You can multi-select by clicking several files before doing this. In addition, errored DeltaFiles are grouped together on the Per Flow and Per Message tabs, where they can also be retried in bulk.
- Click Modify Metadata to review and optionally modify the original source metadata of the DeltaFile.
- Click "Resume."

Note that it is not possible to modify any other part of the DeltaFile, including intermediate Action-produced metadata, before resuming.

## Replay

The second error recovery method in DeltaFi is called Replay. It lets you restart a DeltaFile by creating a new copy of the original file as if it were being reingressed.

The replayed DeltaFile has a parent/child relationship with the original file and can be replayed only once. If you need another copy, replay the most recently created child.

Note that replay works for any DeltaFile, not just the ones in error state.

To Replay a DeltaFile, follow these steps:

- Navigate to the DeltaFi UI and find the DeltaFile you wish to replay on the Search screen. Click the DID of that DeltaFile to enter the DeltaFile Viewer.
- Click the Menu button in the upper right then click on the "Replay" button.
- Click Modify Metadata to review and optionally modify the original source metadata of the DeltaFile.
- Click "Replay."

Note that, as with Resume, it is not possible to modify any other part of the DeltaFile before replaying.

## Acknowledge

You can also acknowledge an error, which provides a reason for why the error occurred. This action will remove the error from the list of errors displayed in the UI, even if the DeltaFile was not completed.

Acknowledge can be performed from either the Errors page or the DeltaFile Viewer.

## Error Thresholds and Ingress Flow Blocking

In order to maintain a high level of data quality and system stability, DeltaFi allows you to set a maximum number of allowable errors within an ingress flow. This error threshold ensures that when a specific number of unacknowledged errors are reached, the ingress flow will be blocked, preventing any further data from entering the flow. This feature is particularly useful for identifying and addressing issues within a flow before they have a significant impact on the overall data processing pipeline.

### Configuring Error Thresholds

To configure an error threshold for an ingress flow, use the setMaxErrors GraphQL mutation:

```graphql
mutation {
  setMaxErrors(flowName: "example-ingress-flow", maxErrors: 10)
}
```

In this example, the error threshold is set to 10. This means that if there are 10 or more unacknowledged active errors
within this ingress flow, the flow will be blocked, and no further data will be allowed to enter.

_NOTE: GUI support for setting maxErrors will be coming shortly._

### Unblocking an Ingress Flow

When an ingress flow is blocked due to reaching the error threshold, it is important to address the underlying issues
causing the errors. Errors can be handled in two ways: by acknowledging the errors or by retrying the actions that led
to the errors. If the retries are successful, the errors will be considered resolved.

After handling the errors, either by acknowledging them or successfully retrying the actions as documented earlier in
this guide, the system will reevaluate the ingress flow's status. If the number of unacknowledged active errors falls
below the threshold, the flow will be unblocked and new data will be allowed to enter once again.

By utilizing this error threshold feature, you can effectively manage the error handling process within your DeltaFi
system, ensuring that data quality and system stability are maintained throughout your data processing pipeline.
