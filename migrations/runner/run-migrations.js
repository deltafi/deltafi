function runMigration(migrationName, migrationFile) {
  let start = performance.now();
  load(migrationFile);
  db.migrations.insertOne({name: migrationName, runAt: new Date()});
  let timeTaken = (performance.now() - start)/1000;
  print(`   -- Completed database migration ${migrationName}`);
  print(`      Execution time: ${timeTaken.toFixed(3)} seconds`);
}

function runMigrationOnce(migrationName, migrationFile) {
    var migrationRan = db.migrations.findOne({"name": migrationName});
    if (!migrationRan) {
        runMigration(migrationName, migrationFile)
    } else {
        print(`   -- Skipped migration ${migrationName} as it has already been run`);
    }
}

function getJSFileNames(directory) {
    const files = fs.readdirSync(directory);
    const jsFiles = files.filter(file => path.extname(file) === '.js');
    return jsFiles;
}

const directory = process.env.MIGRATION_PATH;
const files = process.env.MIGRATION_FILES;
const force = (process.env.MIGRATION_FORCE !== undefined)

let jsFiles = getJSFileNames(directory);

if (files !== undefined) jsFiles = files.split(' ');

print(`Running ${jsFiles.length} migrations:`);

jsFiles.forEach((file, index) => {
  const filePath = path.join(directory, file);
  const fileName = path.basename(file);

  print(`   -- Running database migration ${fileName} (${index + 1})`);
  force ? runMigration(fileName, filePath) : runMigrationOnce(fileName, filePath);
  print('');
});
