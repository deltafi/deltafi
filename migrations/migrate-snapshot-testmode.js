// Add test flow lists to snapshots
db.systemSnapshot.updateMany({testIngressFlows: null}, {$set: {testIngressFlows: []}});
db.systemSnapshot.updateMany({testEgressFlows: null}, {$set: {testEgressFlows: []}});
