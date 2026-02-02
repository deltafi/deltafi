// ABOUTME: Mock data for the getSystemSnapshotsByFilter GraphQL query.
// ABOUTME: Wraps snapshot data from getSystemSnapshots with pagination metadata.

import getSystemSnapshots from "./getSystemSnapshots";

const snapshots = getSystemSnapshots().getSystemSnapshots;

export default {
  getSystemSnapshotsByFilter: {
    count: snapshots.length,
    offset: 0,
    totalCount: snapshots.length,
    systemSnapshots: snapshots,
  },
};
