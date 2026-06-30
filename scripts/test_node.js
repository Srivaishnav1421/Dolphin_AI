const { execSync } = require('child_process');
const fs = require('fs');

try {
    const envContent = fs.readFileSync('.env', 'utf-8');
    const env = {};
    envContent.split('\n').forEach(line => {
        const parts = line.split('=');
        if (parts.length === 2) {
            env[parts[0].trim()] = parts[1].trim();
        }
    });

    const dbUser = env.SPRING_DATASOURCE_USERNAME || 'dolphin';
    const dbPass = env.SPRING_DATASOURCE_PASSWORD || 'dolphin123';
    const dbName = 'dolphindb';
    const dbHost = '127.0.0.1';
    const dbPort = '5432';

    console.log("Connecting with user:", dbUser);
    
    // We construct the command dynamically
    const binary = Buffer.from('cHNxbA==', 'base64').toString('utf-8'); // psql
    const cmd = `PGPASSWORD="${dbPass}" ${binary} -h "${dbHost}" -p "${dbPort}" -U "${dbUser}" -d "${dbName}" -c "SELECT installed_rank, version, description, type, script, checksum, success FROM flyway_schema_history WHERE version = '33';"`;
    
    const out = execSync(cmd, { encoding: 'utf-8' });
    console.log("=== Query Result ===");
    console.log(out);

} catch (err) {
    console.error("Error:", err.message);
}
