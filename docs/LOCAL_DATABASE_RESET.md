# Local Database Reset

Use this only for local development. Never reset a production, live, or shared customer database from these steps.

## Confirm The Active Database

At backend startup, DolphinAI logs the active runtime identity:

```text
DolphinAI Runtime: profile=dev, dbHost=localhost, dbPort=5432, dbName=dolphindb, schema=public, user=dolphin, product=PostgreSQL, flyway=OK(...)
```

Authenticated dev users can also check:

```bash
curl http://localhost:8000/api/system/runtime
```

The local frontend should be opened at:

```text
http://localhost:4200
```

The local backend should be:

```text
http://localhost:8000
```

Avoid using `http://127.0.0.1:4200` unless you have also configured matching backend CORS origins.

## Option A: Use A New Empty Database

Recommended for clean verification because it does not touch the existing `dolphindb`.

```bash
createdb dolphindb_clean
```

Then set:

```env
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/dolphindb_clean
```

Restart the backend. Flyway will create the schema. If `FIRST_RUN_OWNER_EMAIL` and `FIRST_RUN_OWNER_PASSWORD` are configured, only the first owner/org/workspace bootstrap should be created. Leads, campaigns, revenue, and analytics must remain empty until real actions create rows.

## Option B: Backup Then Reset Current Dev DB

Only after explicit approval:

```bash
pg_dump dolphindb > backup_dolphindb_YYYYMMDD.sql
```

Then, only for local/dev:

```sql
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
```

Restart the backend and allow Flyway migrations to recreate the schema.

## Option C: Docker Isolated Dev DB

Use Docker Compose with a separate PostgreSQL service name, database name, and volume. Do not reuse production credentials or production volumes.

## Guardrails

* Never reset production.
* Never reset unless active profile is `dev` or `local`.
* Never reset without a backup.
* Never reset without explicit confirmation.
* Never reset a database whose name includes `prod`, `production`, or `live`.
* Never reset a database whose host is not `localhost` or `127.0.0.1`.

## Optional Guarded Script

The guarded helper is:

```bash
./scripts/reset-local-dev-db.sh
```

It prints the active host/database, creates a backup, requires typing:

```text
RESET LOCAL DEV DATABASE
```

and then resets only the local `public` schema.
