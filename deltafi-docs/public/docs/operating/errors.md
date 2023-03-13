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