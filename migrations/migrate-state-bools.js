db.deltaFile.aggregate([
    {
        $set: {
            inFlight: { $in: ["$stage", ["INGRESS", "ENRICH", "EGRESS"]] }
        }
    },
    {
        $set: {
            terminal: {
                $and: [
                    { $eq: ["$inFlight", false] },
                    { $or: [{ $ne: ["$stage", "ERROR"] }, { $ne: ["$acknowledgedError", null] }] },
                    { $or: [{ $eq: [{ $type: "$pendingAnnotationsForFlows" }, "missing"] },
                            {$eq: ["$pendingAnnotationsForFlows", null] },
                            { $and: [
                                    { $isArray: "$pendingAnnotationsForFlows" },
                                    { $eq: [{ $size: "$pendingAnnotationsForFlows" }, 0] }
                                ]}] }
                ]
            }
        }
    },
    {
        $set: {
            contentDeletable: {
                $and: [
                    { $eq: ["$terminal", true] },
                    { $or: [{ $eq: [{ $type: "$contentDeleted" }, "missing"] },
                            { $eq: ["$contentDeleted", null] }
                            ]},
                    { $ne: ["$totalBytes", 0] }]
            }
        }
    },
    {
        $merge: {
            into: "deltaFile",
            whenMatched: "merge"
        }
    }
]);
