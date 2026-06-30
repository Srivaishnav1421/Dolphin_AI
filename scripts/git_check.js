const { execSync } = require('child_process');

try {
    const env = { ...process.env, PGPASSWORD: 'dolphin123' };
    
    // List all schemas starting with verify_
    console.log("=== Listing verify_ schemas ===");
    const schemas = execSync(`psql -h 127.0.0.1 -p 5432 -U dolphin -d dolphindb -t -c "SELECT schema_name FROM information_schema.schemata WHERE schema_name LIKE 'verify_%';"`, { env, encoding: 'utf-8' });
    console.log(schemas);

    const schemaList = schemas.trim().split('\n').map(s => s.trim()).filter(Boolean);
    for (let schema of schemaList) {
        console.log(`=== Tables in ${schema} ===`);
        const tables = execSync(`psql -h 127.0.0.1 -p 5432 -U dolphin -d dolphindb -t -c "SELECT table_name FROM information_schema.tables WHERE table_schema = '${schema}';"`, { env, encoding: 'utf-8' });
        console.log(tables);
        
        try {
            console.log(`=== Querying users count in ${schema} ===`);
            const count = execSync(`psql -h 127.0.0.1 -p 5432 -U dolphin -d dolphindb -t -c "SELECT COUNT(*) FROM ${schema}.users;"`, { env, encoding: 'utf-8' });
            console.log(`Count: ${count.trim()}`);
        } catch (e) {
            console.log(`Query failed: ${e.message}`);
        }
    }

} catch (err) {
    console.error("Error:", err.message);
}
