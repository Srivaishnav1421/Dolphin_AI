const target = 1503156954;

const makeTable = () => {
    let c;
    const table = [];
    for (let n = 0; n < 256; n++) {
        c = n;
        for (let k = 0; k < 8; k++) {
            c = ((c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1));
        }
        table[n] = c;
    }
    return table;
};
const table = makeTable();

function crc32(str) {
    let crc = 0 ^ (-1);
    const bytes = Buffer.from(str, 'utf-8');
    for (let i = 0; i < bytes.length; i++) {
        crc = (crc >>> 8) ^ table[(crc ^ bytes[i]) & 0xFF];
    }
    return (crc ^ (-1)) >>> 0;
}

// Generate base candidates
const id_variations = ["id VARCHAR(36) PRIMARY KEY", "id VARCHAR(36) PRIMARY KEY,", "id VARCHAR(255) PRIMARY KEY"];
const ws_variations = ["workspace_id VARCHAR(255) NOT NULL", "workspace_id VARCHAR(36) NOT NULL"];
const prov_variations = ["provider_id VARCHAR(255) NOT NULL", "provider_id VARCHAR(50) NOT NULL"];
const cred_variations = ["credentials_json TEXT", "credentials_json TEXT,"];
const time_variations = [
    ["created_at TIMESTAMP", "updated_at TIMESTAMP"],
    ["created_at TIMESTAMP WITHOUT TIME ZONE", "updated_at TIMESTAMP WITHOUT TIME ZONE"],
    ["created_at TIMESTAMP NOT NULL", "updated_at TIMESTAMP NOT NULL"],
    ["created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP", "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"]
];

// Let's also consider if the original file had validation_status
const val_variations = [
    [],
    [
        "validation_status VARCHAR(30) NOT NULL DEFAULT 'PENDING_VALIDATION'",
        "last_validated_at TIMESTAMP",
        "last_validation_message VARCHAR(1000)"
    ]
];

// Let's test a simpler set of candidate SQL templates
// We can reconstruct the SQL text and calculate its CRC32
function test(sql) {
    let normalized = sql.replace(/\r\n/g, '\n').replace(/\r/g, '\n');
    for (let newlines = 0; newlines <= 5; newlines++) {
        let text = normalized + '\n'.repeat(newlines);
        if (crc32(text) === target) {
            console.log("FOUND MATCH!");
            console.log(text);
            process.exit(0);
        }
    }
}

// Candidate A:
// CREATE TABLE IF NOT EXISTS integration_settings (
//     id VARCHAR(36) PRIMARY KEY,
//     workspace_id VARCHAR(255) NOT NULL,
//     provider_id VARCHAR(255) NOT NULL,
//     credentials_json TEXT,
//     created_at TIMESTAMP,
//     updated_at TIMESTAMP
// );
// 
// CREATE INDEX IF NOT EXISTS idx_integration_settings_workspace_provider
//     ON integration_settings(workspace_id, provider_id);

const template1 = (valCols, timeCols, indexSemicolon) => {
    let cols = [
        "    id VARCHAR(36) PRIMARY KEY,",
        "    workspace_id VARCHAR(255) NOT NULL,",
        "    provider_id VARCHAR(255) NOT NULL,",
        "    credentials_json TEXT,"
    ];
    if (valCols) {
        cols.push("    validation_status VARCHAR(30) NOT NULL DEFAULT 'PENDING_VALIDATION',");
        cols.push("    last_validated_at TIMESTAMP,");
        cols.push("    last_validation_message VARCHAR(1000),");
    }
    if (timeCols) {
        cols.push("    created_at TIMESTAMP,");
        cols.push("    updated_at TIMESTAMP");
    } else {
        // remove trailing comma from last column
        let lastIdx = cols.length - 1;
        if (cols[lastIdx].endsWith(",")) {
            cols[lastIdx] = cols[lastIdx].slice(0, -1);
        }
    }
    
    const createTable = `CREATE TABLE IF NOT EXISTS integration_settings (
${cols.join('\n')}
);`;

    const index = `CREATE INDEX IF NOT EXISTS idx_integration_settings_workspace_provider
    ON integration_settings(workspace_id, provider_id)${indexSemicolon ? ';' : ''}`;

    return [
        createTable,
        createTable + "\n\n" + index,
        createTable + "\n" + index
    ];
};

// Test permutations
for (let val of [true, false]) {
    for (let time of [true, false]) {
        for (let semi of [true, false]) {
            const sqls = template1(val, time, semi);
            for (let s of sqls) {
                test(s);
                // try lower case keywords
                test(s.toLowerCase());
                // try uppercase keywords
                test(s.replace(/varchar/g, 'VARCHAR').replace(/timestamp/g, 'TIMESTAMP').replace(/text/g, 'TEXT'));
            }
        }
    }
}

console.log("No match found in first set of permutations.");
