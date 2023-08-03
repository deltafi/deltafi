db.ingressFlow.updateMany({}, [{$set: {"loadAction.internalParameters": "$loadAction.parameters"}}])
db.ingressFlow.updateMany({}, [{$set: {"transformActions": {$map: {input: "$transformActions", in: {$mergeObjects: ["$$this", {"internalParameters": "$$this.parameters"}]} }}}}])

db.transformFlow.updateMany({}, [{$set: {"transformActions": {$map: {input: "$transformActions", in: {$mergeObjects: ["$$this", {"internalParameters": "$$this.parameters"}]} }}}}])
db.transformFlow.updateMany({}, [{$set: {"egressAction.internalParameters": "$egressAction.parameters"}}])

db.enrichFlow.updateMany({}, [{$set: {"domainActions": {$map: {input: "$domainActions", in: {$mergeObjects: ["$$this", {"internalParameters": "$$this.parameters"}]} }}}}])
db.enrichFlow.updateMany({}, [{$set: {"enrichActions": {$map: {input: "$enrichActions", in: {$mergeObjects: ["$$this", {"internalParameters": "$$this.parameters"}]} }}}}])

db.egressFlow.updateMany({}, [{$set: {"formatAction.internalParameters": "$formatAction.parameters"}}])
db.egressFlow.updateMany({}, [{$set: {"validateActions": {$map: {input: "$validateActions", in: {$mergeObjects: ["$$this", {"internalParameters": "$$this.parameters"}]} }}}}])
db.egressFlow.updateMany({}, [{$set: {"egressAction.internalParameters": "$egressAction.parameters"}}])