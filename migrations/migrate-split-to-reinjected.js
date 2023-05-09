db.deltaFile.updateMany(
    { "actions.state": "SPLIT" },
    { $set: { "actions.$[elem].state" : "REINJECTED" } },
    { arrayFilters: [ { "elem.state": "SPLIT" } ] }
)