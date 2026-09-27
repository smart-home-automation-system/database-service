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
`api-gateway-service` routes `/device/configuration/**` and `/household/**` here (one `database`
route with both paths, HAS-150). A new top-level path of this service needs adding to that route,
or it answers "No static resource" (404) from outside the cluster. Nothing on the gateway is
authenticated — the registry's names, phones and MACs are open to whoever reaches the ingress.

## Household registry — the parts worth knowing before changing anything

- **Addressing by natural keys.** Members by `name`, devices by `mac` — both unique in the
  schema. No surrogate id is exposed; the SDK models have none. Keep it that way rather than
  adding ids a client would first have to look up.
- **Bodies are the SDK models** `HouseholdMember` / `MemberPhoneDetails` (smart-home-sdk ≥ 1.3.0)
  with `@Valid`: name 3–50, phone E.164 (`+48505602702`), device name ≤ 50, MAC lowercase and
  colon-separated. The same bounds are in the schema (`V8`, phone since `V9`); change them in the
  SDK and in a new migration together.
- **The phone is an SMS recipient** (SMSAPI, smsapi.pl), hence E.164 without any formatting —
  display formatting is the client's job. SMSAPI's `to` takes `48505602702` or `505602702` and
  does not document a leading `+`: whoever sends the SMS strips the `+`, the registry keeps the
  standard form. The `mac` **query parameter** is lowercased before the lookup; the body
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
- **Duplicates are mapped by constraint name, per service.** No global processor for
  `DuplicateKeyException` (there was one for the Eaton message until HAS-150, and it leaked that
  message into every other duplicate): each service maps its own duplicates to a domain exception
  (409) — Eaton → `DeviceConfigurationExistsException` (the existence check and the
  `eaton_devices_point_gateway_uq` constraint of V7 give the same 409), household →
  `HouseholdException` / `MemberDeviceException` by the constraint in the driver message
  (`household_members_name_upper_uq`, `household_members_phone_uq`,
  `member_devices_mac_uq`, `member_devices_member_name_uq`). **Renaming a constraint or index in a
  migration breaks this mapping** — an unknown one falls through to the `cholewa-commons` default
  (409 "Duplicate Key", no details). The constants live in the services.
- **Names are unique ignoring letter case** (V10, a unique index on `upper(name)`): lookups are
  `...IgnoreCase`, and a case-sensitive `UNIQUE (name)` once let `anna` sit next to `Anna`, after
  which every lookup of the name found two rows and failed with 500. **`upper`, not `lower`** —
  Spring Data's `...IgnoreCase` compares `UPPER(name) = UPPER(?)`, and the two functions disagree
  for some letters. A rename to another member's name hits the index and is mapped to the 409;
  changing only the case of the own name updates the same row. There is no Java-side copy of the
  rule on PATCH on purpose — the index is the single definition. Names sort case-insensitively.
- **Member responses carry the member's devices** (POST/PATCH/activate/deactivate, like GET), so a
  client replacing its cached member with a response does not lose them; devices in a member
  payload are ignored — they are managed through the device endpoints.
- **An empty registry is `200 []`**, not 404 — `presence-service` polls it.
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
