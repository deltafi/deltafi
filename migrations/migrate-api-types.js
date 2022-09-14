db.deletePolicy.updateMany({_class: "org.deltafi.core.domain.api.types.DiskSpaceDeletePolicy"}, {$set: {_class: "org.deltafi.core.types.DiskSpaceDeletePolicy"}});
db.deletePolicy.updateMany({_class: "org.deltafi.core.domain.api.types.TimedDeletePolicy"}, {$set: {_class: "org.deltafi.core.types.TimedDeletePolicy"}});
db.deletePolicy.updateMany({_class: "org.deltafi.core.domain.types.DiskSpaceDeletePolicy"}, {$set: {_class: "org.deltafi.core.types.DiskSpaceDeletePolicy"}});
db.deletePolicy.updateMany({_class: "org.deltafi.core.domain.types.TimedDeletePolicy"}, {$set: {_class: "org.deltafi.core.types.TimedDeletePolicy"}});
