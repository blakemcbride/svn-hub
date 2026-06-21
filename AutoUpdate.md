# SvnHub Auto-Update Facility

SvnHub keeps its **database current with the deployed code automatically, at
server startup, with no manual SQL step**. A deploy is just a WAR swap (plus a
restart); the running server detects the database is behind the code and brings
it forward itself.

This emulates OwnSona's auto-update mechanism
(`/home/blake/GitHub.blakemcbride/Ownsona/AutoUpdate.md`), adapted to the Kiss
framework.

> "Auto-update" means the server migrates the **schema and data** it finds to
> match the new code. There is **no** facility that updates the application
> code/WAR itself — code updates are operator-driven deploys.

---

## 1. The two stages (run at startup, in order)

`KissInit.init2(Connection)` is the startup hook (called once after the DB is
open, before the app serves). It runs two walkers in a fixed order:

| # | Stage | Class | Granularity | Tracks |
|---|---|---|---|---|
| 1 | Schema migration | `com.svnhub.migrate.SchemaMigrator` | whole database | `db_version` table |
| 2 | Per-row upgrade | `com.svnhub.migrate.RecordMigrator` | each `repository` row | `record_version` column |

Schema is first because a migration may create the very columns the per-row
stage reads (`Migration002AddRecordVersion` adds `record_version`).

### Fail-closed (important Kiss difference from OwnSona)

OwnSona runs migrations in a servlet's static initializer, so a failure makes
the servlet refuse to load. In Kiss, `init2` runs via `GroovyService` which
**catches and logs** exceptions — so a failed migration would otherwise be
silently swallowed and the app would serve on a stale schema.

To get the same "never serve a half-migrated DB" guarantee, a **schema-migration
failure marks the schema not-ready (`SchemaStatus`) and `Login.login` then
refuses all logins** — the single auth chokepoint, so the app is effectively
closed. A loud banner is printed to `catalina.out`:

```
* * * SCHEMA MIGRATION FAILED — logins are blocked until fixed
* * * <reason>
```

The end user sees the framework's generic "Invalid login." (the real reason is
the startup banner) — fix the cause and restart. The **per-row** stage runs only
when the schema is ready and **never** blocks startup (its per-row failures are
logged and counted).

### Two version namespaces

- **`db_version`** — integer for the whole database (`db_version` table, one row
  per applied migration). Owner: `SchemaMigrator`. Expected =
  `MigrationRegistry.CURRENT_DB_VERSION` (currently **3**).
- **`record_version`** — integer column on each `repository` row. Owner:
  `RecordMigrator`. Expected = `RecordUpgraderRegistry.CURRENT_RECORD_VERSION`
  (currently **2**).

v1 is the `schema.sql` baseline (never a Migration object); registered
migrations start at v2.

---

## 2. Stage 1 — `SchemaMigrator` (schema / `db_version`)

`runOnStartup()`:
1. `MigrationRegistry.validate()` — fail fast on a malformed registry.
2. Bootstrap `db_version` (`CREATE TABLE IF NOT EXISTS`, seed v1 with
   `ON CONFLICT DO NOTHING`), read `MAX(version)` as `current`.
3. **Refuse if `current > target`** (DB ahead of code) — throws → fail-closed.
4. Apply each registered migration with `version() > current`, in order.

**Per-migration transaction:** each migration runs on its own connection
(`MainServlet.openNewConnection()` / `closeConnection(conn, ok)`); the migration's
DDL **and** its `INSERT INTO db_version` row commit together. Any failure rolls
both back, so `db_version` is never advanced past a migration that didn't fully
apply; the next startup retries from the same point.

**`Migration` contract:** `version()` (one greater than the previous; v2 is the
first), `name()`, `apply(Connection)`. **Additive only** (`ADD COLUMN/CREATE …
IF NOT EXISTS` — never DROP/RENAME/rewrite), **idempotent**, **permanent** (never
edit a shipped migration; write a new one).

Current chain:
```
v2  Migration002AddRecordVersion   (adds the record_version column itself)
v3  Migration003AddIndexes         (repository_owner_idx)
```

---

## 3. Stage 2 — `RecordMigrator` (per-row / `record_version`)

Walks `repository` rows whose `record_version` is below
`CURRENT_RECORD_VERSION` and applies the registered `RecordUpgrader` chain.

`runOnStartup()`: validate → paginate ids (`record_version < target`, ascending
id, chunk 500) → upgrade each row. **Per-row failures are caught, logged, and
counted — they never throw or block startup.**

**Per-row transaction:** each row is upgraded on its own connection; the chain
runs while `current < target`, then `record_version` is bumped to target **in
the same transaction**. A row deleted between scan and upgrade is treated as done.

**`RecordUpgrader` contract:** `fromVersion()`/`toVersion()` (= from + 1),
`name()`, `upgrade(Connection, repoId)`. **Strictly additive** (fill a new field
from data the row already has — never overwrite/transform/delete), **idempotent**,
**per-row isolated**, must **not** bump `record_version` itself.

Current chain:
```
v1 -> v2   DefaultBranchUpgrader   (set default_branch='trunk' where NULL and the repo has /trunk)
```

---

## 4. How to add an auto-applied change

### A schema change (new migration)
1. Create `src/main/precompiled/com/svnhub/migrate/MigrationNNN_Name.java`
   implementing `Migration`; additive DDL with `IF NOT EXISTS` in `apply`.
2. Register it in `MigrationRegistry` (in order) and bump `CURRENT_DB_VERSION`.
3. Commit together. `./bld build` + restart (precompiled requires it).

### A per-row backfill (new upgrader)
1. Create a `RecordUpgrader` in the same package; `upgrade` fills new fields only
   (additive + idempotent); `toVersion() = fromVersion() + 1`.
2. Register it in `RecordUpgraderRegistry` (in order) and bump
   `CURRENT_RECORD_VERSION`.
3. Commit together.

> **Lockstep invariant:** `CURRENT_DB_VERSION` ↔ migration registry and
> `CURRENT_RECORD_VERSION` ↔ upgrader registry must change in the same commit.
> Both `validate()` methods fail fast at startup; `RegistryTest` checks them.

> **Build ordering note:** the `bld` build compiles `src/test/core` *before*
> `src/main/precompiled`, so the first build after adding a precompiled class a
> test references needs the precompiled classes present. If a clean build fails
> with "cannot find symbol" for a new migrate class, bootstrap-compile it once:
> `javac -proc:none -cp "work/exploded/WEB-INF/classes:work/exploded/WEB-INF/lib/*" -d work/exploded/WEB-INF/classes src/main/precompiled/com/svnhub/migrate/*.java`
> then re-run `./bld build`.

---

## 5. Failure & recovery summary

| Stage | Per-unit failure | Effect on startup | Recovery |
|---|---|---|---|
| `SchemaMigrator` | a migration throws | schema not-ready → **logins blocked**; `db_version` not bumped | fix + restart; retries from same version |
| `SchemaMigrator` | DB ahead of code | schema not-ready → **logins blocked** | deploy newer code or restore a snapshot |
| `RecordMigrator` | one row throws | logged + counted; **startup continues** | next startup retries the rows still below target |

Operational stance: take a DB backup before any deploy that bumps
`CURRENT_DB_VERSION` — it is the rollback net.

---

## 6. Where things live

```
src/main/precompiled/com/svnhub/migrate/
    SchemaStatus.java              # ready/error flag; gates Login.login (fail-closed)
    Migration.java                 # stage-1 interface + contract
    MigrationRegistry.java         # ordered list + CURRENT_DB_VERSION + validate()
    SchemaMigrator.java            # stage 1: schema walker
    Migration002AddRecordVersion.java
    Migration003AddIndexes.java
    RecordUpgrader.java            # stage-2 interface + contract
    RecordUpgraderRegistry.java    # ordered list + CURRENT_RECORD_VERSION + validate()
    RecordMigrator.java            # stage 2: per-row walker
    DefaultBranchUpgrader.java     # v1 -> v2 backfill
src/main/backend/KissInit.groovy   # init2 drives both stages (fail-closed)
src/main/backend/Login.groovy      # refuses login when schema not ready
src/test/core/com/svnhub/migrate/RegistryTest.java
schema.sql                         # the v1 baseline (fresh installs)
```
