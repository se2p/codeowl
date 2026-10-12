# Database migrations

Schema and seed data live under [`migration/`](migration), applied by
[Flyway](https://flywaydb.org) on every backend startup — automatically, in order, against
whatever state the target database is actually in. `application.yml`'s `spring.flyway.*` block
is the config; there is nothing to run by hand.

This replaced the old `db/init` bootstrap, which the official Postgres image only ever ran
against an *empty* data volume — a migration added there updated a fresh checkout but never an
existing, already-running database. Flyway tracks what has been applied in
`flyway_schema_history` and applies exactly what is missing, so a schema change reaches an
environment that already exists the same way it reaches a brand new one.

## Naming: `V<n>__description.sql`, sequential, never reused

Flyway orders migrations by the number after `V`, not lexicographically, so there is no
zero-padding trap here the way there was under the old scheme. Bump the version by one for a
new migration and never edit or renumber a migration that has already shipped — once a file's
content changes, its checksum no longer matches what Flyway recorded when it ran, and
`migrate()` refuses to proceed rather than silently apply a different script than the one an
earlier environment already ran under that version number.

## Existing, pre-Flyway databases: `baseline-version`

`V1` through `V6` are exactly the `db/init` files that shipped in the `v.1.0` release (see
`git tag v.1.0`), and that is what a database bootstrapped the old way — production, as of this
writing — is actually at. `spring.flyway.baseline-on-migrate=true` plus
`baseline-version: ${FLYWAY_BASELINE_VERSION:6}` in `application.yml` tells Flyway, the first
time it meets such a database, to record it as already up to date through that version without
re-running `V1`–`V6` (which would fail outright — the tables and data they create already
exist), then apply `V7` onward for real.

That default of `6` is only correct for a database that is genuinely at the `v.1.0` schema. A
database carried further by hand before adopting Flyway needs its own true version passed as
`FLYWAY_BASELINE_VERSION` — there is no way to infer "which migrations already ran" by
inspecting the schema itself, so guess wrong and `migrate()` will try to build on structures
that are not there and fail loudly (not silently corrupt anything). A database Flyway already
tracks, and a genuinely empty one, both ignore this setting entirely: baselining only ever
happens once, the first time `flyway_schema_history` does not exist yet.

## Optional pre-filled content: `seed-data/`

[`seed-data/pre-filled-content.sql`](seed-data/pre-filled-content.sql) holds the worksheets, task
sequences and tasks, and the snippets/eval research dataset that a fresh install used to get by
default via `migration/V1__codeowl_backup.sql`. It is **not** wired into Flyway — the file sits
outside `db/migration/`, so `migrate()` never touches it — and applying it is a manual, one-time
step against an already-migrated database:

```bash
psql "$DATABASE_URL" -f backend/src/main/resources/db/seed-data/pre-filled-content.sql
```

None of it is required for the application to run. `migration/V1__codeowl_backup.sql` still seeds
`versions`, `strategies`, `concepts`, `curriculum_topics` and `concept_curriculum_topic` directly
— generation depends on the first two through a hard foreign key (a fresh, schema-only database
cannot generate anything without them), and the rest is curriculum data kept in place on purpose
rather than moved out as optional content.

[`seed-data/undo-pre-filled-content.sql`](seed-data/undo-pre-filled-content.sql) reverses it —
every table `pre-filled-content.sql` inserts into, emptied in the reverse of its own
FK-dependency order:

```bash
psql "$DATABASE_URL" -f backend/src/main/resources/db/seed-data/undo-pre-filled-content.sql
```

This is a blunt, all-or-nothing undo (it empties those tables outright, the same way
`pre-filled-content.sql` assumes they were empty going in), not a surgical one keyed by the exact
rows that file inserted — safe on a database where that file is the only source of rows in these
tables, destructive on one that has since accumulated real data of its own alongside it.

## Undo scripts

[`undo/`](undo) holds a hand-written rollback for most migrations, named to match
(`undo/V12__....sql` reverses `migration/V12__....sql`). These are **not** wired into Flyway —
Flyway's own automatic `undo` command is a paid Teams feature this project does not have. They
exist for a human to run by hand against a specific environment when a migration needs
reverting:

```bash
psql "$DATABASE_URL" -f backend/src/main/resources/db/undo/V12__curriculum_education_level.sql
```

Run them in reverse order, one at a time, starting from the highest version actually applied —
each one only reverses its own migration's specific change and assumes nothing later is still
depending on it. Each ends with `DELETE FROM flyway_schema_history WHERE version='<n>'`, which
is what lets `migrate()` re-apply that version afterwards if needed, rather than believing it is
still there.

Not every migration has one. `V1` (the base schema) has nothing meaningful to undo to. `V13`
(`merge_duplicate_concepts.sql`) deletes rows and repoints every reference onto the surviving
one — after `ON CONFLICT DO NOTHING` has deduplicated the repointed rows against ones that
already existed, there is no way to tell which is which any more, so there is nothing a script
could restore. Where a migration changes data as well as schema (`V14`, lowercasing three
concept names) the undo restores the data too, because in that case exactly what changed is
fully known; where it is not (case folding in general, is not what `V14`'s undo attempts beyond
those three), the undo says so rather than pretending.

## The database tests run Flyway too

`WorksheetSearchDatabaseTest` and `MarketplaceJourneyDatabaseTest` start a bare Postgres
container with Testcontainers and then run the exact same `migration/` scripts against it via
the Flyway Java API before running any assertions, so the schema under test is the schema that
ships. A migration that breaks a fresh `migrate()` therefore breaks those tests rather than only
breaking the next person who points Flyway at an empty database.

**But only locally.** Both classes are skipped where there is no Docker daemon, and the shared
CI runner is unprivileged, so a `docker:dind` service cannot start there. A green pipeline
consequently says nothing about whether these migrations still apply cleanly. Run `mvn test`
with Docker up after changing anything under `migration/`, and do not take CI's word for it.

### On Windows: enable Docker Desktop's WSL integration first

"Docker up" means up *inside the distro you build in*. Docker Desktop running on Windows is
not enough: without integration the distro has no `docker` CLI and no `/var/run/docker.sock`,
Testcontainers finds no Docker environment, and the two classes above skip — leaving a green
`BUILD SUCCESS` that has verified nothing. It is the same false green as CI, on your own
machine, which makes it much easier to trust by mistake.

Enable it under **Settings → Resources → WSL Integration**, for the distro holding the
checkout. Then `ls -la /var/run/docker.sock` inside that distro should show a socket owned by
`root:docker`, and your user should appear in `id -nG`.

Two things to expect:

- Toggling it **restarts the Docker engine**, which stops running containers. Anything without
  a `restart:` policy stays down until started again.
- Confirm the tests actually ran. `Tests run: N, ... Skipped: 0` is the thing to look for;
  `Skipped: 11` with a green build is the failure mode this whole section is about.

Separately, the daemon's API version is handled in `backend/pom.xml`: Docker Engine 29 refuses
the API 1.32 that docker-java defaults to, so Surefire pins `api.version` via the
`docker.api.version` property. On a daemon older than Docker 25, override it with
`-Ddocker.api.version=1.41` or similar.

## Writing a migration

Make it re-runnable within itself regardless — `ADD COLUMN IF NOT EXISTS`,
`CREATE TABLE IF NOT EXISTS`, and a `DO $$ … $$` guard around constraints, which have no
`IF NOT EXISTS` form — the existing files show the pattern. Flyway itself will not re-run a
migration it has already recorded, but re-runnability is still what lets someone apply the same
file directly with `psql` while debugging without first working out whether it already ran, and
it is what makes writing the matching `undo/` script tractable — an idempotent forward migration
has a single, well-defined "before" state to restore.
