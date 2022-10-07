// Change ingress flow plan class and add type, then add actionType to transform actions and load action in ingress
db.ingressFlowPlan.updateMany({}, {$set: {_class: "org.deltafi.common.types.IngressFlowPlan", type: "INGRESS"}});
db.ingressFlow.updateMany({}, {$set: {"transformActions.$[].actionType": "TRANSFORM"}});
db.ingressFlow.updateMany({}, {$set: {"loadAction.actionType": "LOAD"}});

// Change enrich flow plan class and add type, then add actionType to domain and enrich actions in enrich
db.enrichFlowPlan.updateMany({}, {$set: {_class: "org.deltafi.common.types.EnrichFlowPlan", type: "ENRICH"}});
db.enrichFlow.updateMany({}, {$set: {"domainActions.$[].actionType": "DOMAIN"}});
db.enrichFlow.updateMany({}, {$set: {"enrichActions.$[].actionType": "ENRICH"}});

// Change egress flow plan class and add type, then add actionType to format action, validation actions, and egress action in egress
db.egressFlowPlan.updateMany({_class: "org.deltafi.core.types.EgressFlowPlan"}, {$set: {_class: "org.deltafi.common.types.EgressFlowPlan", type: "EGRESS"}});
db.egressFlow.updateMany({}, {$set: {"formatAction.actionType": "FORMAT"}});
db.egressFlow.updateMany({}, {$set: {"validateActions.$[].actionType": "VALIDATE"}});
db.egressFlow.updateMany({}, {$set: {"egressAction.actionType": "EGRESS"}});
