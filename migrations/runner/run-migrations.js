const runMigration = (migrationName, migrationFile) => {
    var migrationRan = db.migrations.findOne({"name": migrationName});
    if (!migrationRan) {
        load(migrationFile);
        db.migrations.insertOne({name: migrationName, runAt: new Date()});
        print(` -- Completed database migration ${migrationName}`);
    } else {
        print(` -- Skipped migration ${migrationName} as it has already been run`);
    }
}

migrationName = process.env.MIGRATION_NAME
migrationFile = process.env.MIGRATION_FILE

if (!migrationName || !migrationFile) {
    print(`Missing migration name ${migrationName} or file ${migrationFile}`);
} else {
    runMigration(migrationName, migrationFile)
}