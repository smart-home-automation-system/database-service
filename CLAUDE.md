# database-service

`cloud.cholewa:database-service` — the **persistence facade** of the smart home: the other
services never talk to PostgreSQL themselves, they call this one over REST. Reactive (WebFlux,
Spring Data R2DBC), Flyway for the schema. Java 21, Spring Boot 4.1.0
(`spring-boot-starter-parent`), Maven. Local port **6005** (management **8005**); in the deployed
`home` profile **6200** with Actuator on **8200**, where the probes and the Prometheus scrape go.
Docker image `magikabdul/database-service`; the pom keeps `0.0.1-SNAPSHOT`, the released version
comes from the git tag.

Org-wide conventions and working rules (PR flow, branch naming `feature/HAS-<n>`, "user writes
service code, Claude reviews", public-repo hygiene, reactive everywhere, own libraries always on
their latest release) live in the workspace `organization.md` — this file only covers what is
specific to this repo. When opened as part of the workspace, those rules apply here too.

## What it stores

Two independent domains, one package each under `cloud.cholewa.data`, both following the same
layering `api` → `service` → `repository`, with MapStruct mappers between the entities and the
`smart-home-sdk` models:

- **`device.eaton`** — Eaton device configuration: which data point on which gateway is which
  device in which room. Table `eaton_devices`. Consumer: `amx-service`, which looks up every
  incoming datagram (`GET /home/device/configuration/eaton?point=&gateway=`) — roughly every
  30 s, day and night, so this endpoint going quiet is what the Grafana rule
  `database-service not answering` watches.
- **`household`** — the household registry (HAS-150, epic HAS-147): members (`household_members`)
  and their Wi-Fi devices (`member_devices`, MAC addresses). Source of truth about who lives
  here; `presence-service` reads it to match UniFi clients to members and keeps only presence
  state and history itself. The API is in the README.

Routing: inside the cluster callers use k8s DNS (`http://database-service:6200`).
`api-gateway-service` routes only `/device/configuration/**` here — **`/household/**` has no
gateway route**; add one there (a static route in `RoutesConfig`) before anything outside the
cluster, e.g. `web-application`, needs the registry.

## Household registry — the parts worth knowing before changing anything

- **Addressing by natural keys.** Members by `name`, devices by `mac` — both unique in the
  schema. No surrogate id is exposed; the SDK models have none. Keep it that way rather than
  adding ids a client would first have to look up.
- **Bodies are the SDK models** `HouseholdMember` / `MemberPhoneDetails` (smart-home-sdk ≥ 1.2.0)
  with `@Valid`: name 3–50, phone `xxx-xxx-xxx`, device name ≤ 50, MAC lowercase and
  colon-separated. The same bounds are in the schema (`V8`); change them in the SDK and in a new
  migration together. The `mac` **query parameter** is lowercased before the lookup; the body
  is not normalised — the SDK pattern rejects uppercase.
- **Updates must keep the stored row's id.** `R2dbcRepository.save()` INSERTs an entity with a
  null id; the `toUpdatedEntity(existing, …)` / `withActive(existing, …)` mappers copy `id`,
  `createdAt` (and `memberId` / `active`) from the found row. A fresh `toEntity(...)` on an update
  path silently creates a second row — this bug existed once.
- **`active` is changed only through `/activate` and `/deactivate`.** It defaults to `true` in
  the SDK model, so if PATCH honoured it, an update that just omitted the field would reactivate
  the member. PATCH keeps the stored value.
- **Column defaults never apply.** Spring Data R2DBC writes every column of an entity, nulls
  included, so `DEFAULT TRUE` on `active` or a missing `created_at` do not help — the mappers set
  both.
- **Duplicates are mapped by constraint name.** `DuplicateKeyException` is registered globally
  (`ExceptionHandlerConfig`) for the Eaton configuration (409 "Device configuration already
  exists"). The household services catch it first and turn it into `HouseholdException` /
  `MemberDeviceException` (409) based on the constraint in the driver message
  (`household_members_name_uq`, `household_members_phone_uq`, `member_devices_mac_uq`,
  `member_devices_member_name_uq`). **Renaming a constraint in a migration breaks this mapping**
  — the unknown constraint falls through to the Eaton message. The constants live in the
  services.
- `GET /household` reads the whole registry in two queries (members + all devices, grouped by
  `memberId`) — fine for ~10 members, no paging by design. `HouseholdMember` is not
  `Comparable`: sort with an explicit comparator (the no-arg `collectSortedList()` threw
  `ClassCastException` with two members).
- Deleting a member cascades to its devices in the database (`ON DELETE CASCADE`), not in code.

## Error handling

`ExceptionHandlerConfig` registers `cholewa-commons`' `GlobalErrorExceptionHandler` with one
processor per domain exception (`error/processor/`). Convention: **4xx logged at WARN, 5xx at
ERROR**, in the shared `Handled [<class>]: <message>` form — a 4xx at ERROR feeds the Grafana
"Error log spike" rule for nothing. Not-found messages for members come from
`HouseholdMemberNotFoundException.forName(name)`, one place for both services.

## Database & Flyway

- Connection and pool come from `cholewa-commons` (≥ 1.5.0, `database.*` group); this service
  pins only `database.pool.max-size: 6`, its share of the 22 connections the managed database
  allows (heating 8 / database 6 / water 4). Since 1.5.0 the pool validates every connection on
  acquire — that is what recovers from the hung connection of the 2026-09-26 outage; do not
  replace the library's `ConnectionFactory` with an own bean.
- Flyway runs on startup in `home`/`local`, disabled in `test`. Migrations are append-only:
  never edit an applied `V<n>` — the cluster validates checksums and the pod would not start.
- **A local run talks to the production database.** The `local` profile itself carries no
  connection properties; the IDEA run configuration (`home,local`) loads them from an env file
  outside the repo, and that file points at the managed production database. A local run
  therefore applies migrations and writes real data — most likely how `V8` reached production on
  2026-08-15 (applied as the app user, the same day the file was written), before any release
  contained it. Before running
  locally with a new migration, be sure it is final; verify CRUD on the deployed pod instead of
  locally when the data would be junk.
- Ids are `INT` in the schema and `Long` in the entities (Spring Data converts) — the same in
  every table, not a mistake.

## Build, tests & gotchas

- `mvn verify` (JDK 21). `tidy-maven-plugin:check` runs in `verify`, so after editing the pom run
  `mvn tidy:pom`. The enforcer's `dependencyConvergence` is on — `apiguardian-api` is pinned in
  `<dependencyManagement>` because logbook and JUnit 6 disagree; do not exclude it instead.
- logbook needs the optional `spring-boot-http-client` module on Boot 4.1 (already declared).
- Mockito runs as an explicit `-javaagent` in the surefire `argLine`, with **`@{argLine}` first**
  so JaCoCo's agent from the Sonar workflow survives — dropping it zeroes the coverage and fails
  the gate. Surefire activates the `test` profile for every class and includes `*Test`/`*IT`
  only (a class named `...Tests` would silently not run).
- Tests: controller slices with `@WebFluxTest` + `@Import(ExceptionHandlerConfig.class)` (so the
  real error mapping and statuses are asserted) and `@MockitoBean` services; service tests with
  Mockito on the **real generated mapper** (`@Spy ... = new XxxMapperImpl()`) wherever the
  mapper's handling of ids matters. Reactive assertions via `publisher.as(StepVerifier::create)`.
  The SonarCloud gate requires ≥ 80 % coverage on new code.

## CI/CD & deployment

`CI.yml` (build on push to `main`/`feature/**`), `sonar.yml` (SonarCloud + JaCoCo), `release.yml`
(GitHub release → Docker image `magikabdul/database-service` with the tag as version). The
manifest is `deployment-tools/workshop/database-service.yaml` (image tag pinned there, env block
with the database properties from the secret). Release flow: the `release` skill.
