db.deletePolicy.updateMany({}, {$unset: {"locked": 1}});
db.systemSnapshot.updateMany({}, {$unset: {"deletePolicies.timedPolicies.$[].locked" : 1}})
db.systemSnapshot.updateMany({}, {$unset: {"deletePolicies.diskSpacePolicies.$[].locked" : 1}})

