# database-service

`cloud.cholewa:database-service` — the **persistence facade** of the smart home: the other
services never talk to PostgreSQL themselves, they call this one over REST. Reactive (WebFlux,
Spring Data R2DBC), Flyway for the schema. Java 21, Spring Boot 4.1.1
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
- **Bodies are the SDK models** `HouseholdMember` / `MemberPhoneDetails` (smart-home-sdk ≥ 1.4.0)
  with `@Valid`: name 3–50, phone E.164 (`+48505602702`), device name ≤ 50, MAC lowercase and
  colon-separated. The same bounds are in the schema (`V8`, phone since `V9`); change them in the
  SDK and in a new migration together.
- **The phone is an SMS recipient** (SMSAPI, smsapi.pl), hence E.164 without any formatting —
  display formatting is the client's job. SMSAPI's `to` takes `48505602702` or `505602702` and
  does not document a leading `+`: whoever sends the SMS strips the `+`, the registry keeps the
  standard form. The `mac` **query parameter** is lowercased before the lookup; the body
  is not normalised — the SDK pattern rejects uppercase.
- **Updates must keep the stored row's id.** `R2dbcRepository.save()` INSERTs an entity with a
  null id; the `toUpdatedEntity(existing, …)` / `withActive(existing, …)` /
  `withRooms(existing, …)` mappers copy `id`, `createdAt` (and `memberId` / `active` / `role` /
  `rooms`) from the found row. **A new column of `household_members` has to be added to every
  one of them** — MapStruct would otherwise write `null` over it on the next update. A fresh `toEntity(...)` on an update
  path silently creates a second row — this bug existed once.
- **`active` is changed only through `/activate` and `/deactivate`.** It defaults to `true` in
  the SDK model, so if PATCH honoured it, an update that just omitted the field would reactivate
  the member. PATCH keeps the stored value.
- **Role and rooms** (HAS-192, `V11`) — what the web dashboard shows a member. Three rules,
  each the answer to a way an update could change something nobody asked for:
  - **`role` is `null` when not sent** (the SDK model has no default, on purpose). `POST`
    without one stores `RESIDENT` (the mapper's `defaultValue`); `PATCH` without one — or with
    an explicit `null` — keeps the stored role. Never give the role a default anywhere on the
    way in: an update of the phone alone would then turn an admin into a resident.
  - **The rooms are replaced only through `PUT /member/{name}/rooms`.** The SDK model starts
    `rooms` as an empty list, so "not sent" and "none" look the same; `PATCH` therefore ignores
    them, exactly as it ignores `active`. `POST` takes the rooms of a new member.
  - **The model does not check the rooms; the service does** (`checkedRooms`): a room listed
    twice or a `null` among them is `InvalidHouseholdMemberException` → 400
    `INVALID_HOUSEHOLD_MEMBER`, named by the value a client sends (`living room`), and decided
    before any query runs. The list is a list, not a set, so the order survives — it is the
    order the rooms are shown in.
  - An **unknown** role or room never reaches the service: the SDK enum throws inside Jackson
    and `cholewa-commons` answers 400 `Malformed request body` with `Unexpected value '…'`
    (no code). Verified in the controller slice, which uses the real Jackson.
  - Stored as the **constant names** (`ADMIN`, `LIVING_ROOM`), like the enums of
    `eaton_devices`; the API speaks the SDK values (`admin`, `living room`). `rooms` is a
    PostgreSQL **array column** mapped to `List<RoomName>` — Spring Data R2DBC converts both
    ways, an empty list is `{}`. A response leaves an empty `rooms` out (`NON_EMPTY`).
- **Column defaults never apply.** Spring Data R2DBC writes every column of an entity, nulls
  included, so `DEFAULT TRUE` on `active`, `DEFAULT 'RESIDENT'` on `role` or a missing
  `created_at` do not help — the mappers set them. (The default of `role` did its one job in
  `V11`: the members that existed became residents.)
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

## Eaton configuration — query parameters are validated too

`GET /device/configuration/eaton` rejects a `point` outside 1..99 with 400 (HAS-145); before, the
query ran, could not match and answered 404 — a bad request presented as a missing configuration.

- **The constraints sit on the method parameter and need nothing else — no `@Validated` on the
  class.** Since Spring Framework 6.1 WebFlux validates a constrained `@RequestParam` /
  `@PathVariable` itself and raises `HandlerMethodValidationException`. The task (and the first
  version) put `@Validated` on the controller: that switches the built-in validation **off** in
  favour of an AOP proxy answering `ConstraintViolationException`, validates the `@Valid` body
  of the POST a second time, and puts the Java method name into the answer
  (`getEatonDeviceConfiguration.point: …`). Both paths were run in the slice to tell them apart.
- **`InvalidRequestParameterProcessor`** (registered for `HandlerMethodValidationException`)
  turns it into 400 `Invalid request parameter` with the violated constraints' messages as
  `details`; the `cholewa-commons` default would answer a bare "Validation failure". A candidate
  for `cholewa-commons` once a second service needs it.
- **Error messages are English, always** — a rule, not a preference. Bean Validation words a
  violated constraint in the locale of the JVM or the request: Polish on a developer machine,
  English in the cluster (`en_US`). The service has no code for it: `cholewa-commons` pins the
  messages (`ValidationMessagesAutoConfiguration`, ≥ 1.6.0), and the local
  `ValidationMessagesConfig` was deleted with the bump (HAS-175) — it ran after the library's
  customizer and put back an older variant, so do not bring it back. `ValidationMessagesTest`
  starts the whole context on a Polish JVM and expects English; it is a `@SpringBootTest` on
  purpose, a `@WebFluxTest` slice does not load the library's auto-configuration (so a
  default constraint message asserted in a controller slice would still follow the locale of
  the machine). A message that names the parameter is still worth writing
  on the constraint (`message = EatonDataPoint.OUT_OF_RANGE`) — the default says only "must be
  less than or equal to 99".
- The range is `EatonDataPoint.MIN` / `MAX`, and it is stated in three more places code here
  cannot share a constant with: the CHECK of `V7`, the SDK schema (`eaton.yaml`, the bounds of
  the POST body) and `amx-service` (`MessageUtilities.extractDataPoint`). Change all four
  together.
- `amx-service` is not affected: it rejects a data point outside 1..99 itself
  (`MessageUtilities.extractDataPoint`) before it ever asks. Relevant because it relays only a
  404 and turns any other 4xx from here into a 502 logged at ERROR.

## Error handling

`ExceptionHandlerConfig` registers `cholewa-commons`' `GlobalErrorExceptionHandler` with **one
line per domain exception**: `new DomainExceptionProcessor(status, CustomErrorDescription)`
(HAS-175; seven near-identical processor classes before). The response is `message` = the
constant's description, `details` = the exception message (left out for a 5xx, whose message
is internal — it goes to the log with the stack trace instead), `code` = the constant's **name**
(`ErrorId.codeOf`, `cholewa-commons` ≥ 1.7.0). Convention: **4xx logged at WARN, 5xx at
ERROR**, in the shared `Handled [<class>]: <message>` form — a 4xx at ERROR feeds the Grafana
"Error log spike" rule for nothing. Not-found messages for members come from
`HouseholdMemberNotFoundException.forName(name)`, one place for both services.

- **The names of `CustomErrorDescription` are wire contract.** A caller branches on the code:
  `amx-service` relays a 404 to the AMX controller only with `NOT_FOUND_DEVICE_CONFIGURATION`
  (HAS-176). Renaming a constant compiles and passes every other test, so
  `CustomErrorDescriptionTest` writes the names out — when it fails, the change is a breaking
  one, to be made with the callers. The descriptions are free to reword.
- **A new domain exception** is a constant, an exception class and one map entry — no
  processor class. Only causes get a code: `InvalidRequestParameterProcessor` and the
  `cholewa-commons` built-ins (validation, missing body, unknown duplicate) answer without one.
- The description of a constant is the response's `message`, so it says what kind of error it
  is; the specifics (`Unknown Eaton gateway: garden`) are the exception message, worded in the
  service that throws it.

## Database & Flyway

- Connection and pool come from `cholewa-commons` (≥ 1.5.0, `database.*` group); this service
  pins only `database.pool.max-size: 4`, its share of the 22 connections the managed database
  allows (heating 2 / database 4 / water 2 / presence 2 = 10). It was 6 up to and including 0.8.0 (HAS-169):
  the Deployment rolls, so during a rollout the old and the new pod each hold a pool and Flyway
  adds one JDBC connection that no `r2dbc_pool_*` metric shows — 16 + 6 + 1 = 23 in the worst
  case. With the new split the rollout of this service stays at 10 + 4 + 1 = 15, and even
  the three rolling services at once at 21 (`presence-service` uses `Recreate`). Raising any
  pool means re-doing that sum. What it costs: in the 15 days Prometheus keeps, scrapes saw at most 2
  connections in use, but on four days short bursts grew the pool to 6 — with 4 such a burst
  waits for a connection (`max-acquire-time`) instead of opening one, and `amx-service` gives
  up on its lookup after 5 s. If `r2dbc_pool_pending_connections` starts showing in normal
  operation, the answer is `Recreate` on the Deployment and the old size, not a bigger pool
  alone. The line stays even though 4 equals the library default — the default may change. Since 1.5.0 the pool validates every connection on
  acquire — that is what recovers from the hung connection of the 2026-09-26 outage; do not
  replace the library's `ConnectionFactory` with an own bean.
- Flyway runs on startup in `home`/`local`, disabled in `test`. Migrations are append-only:
  never edit an applied `V<n>` — the cluster validates checksums and the pod would not start.
- **Rehearse a migration on a throwaway PostgreSQL, with the released image first** (how `V11`
  was checked, HAS-192): `docker run postgres:17-alpine` on a local port, then the image of
  the **current release** against it (it applies the migrations so far; create a few rows
  through its API), then the new jar with `--database-host=localhost --database-port=…` and
  the other four `--database-*` arguments given explicitly. That is the rollout in small: the
  new migration runs over rows the old version wrote. Two things it needs: the container must
  speak **SSL** (`ssl = on` with a self-signed certificate — `cholewa-commons` connects with
  `sslMode` `REQUIRE`), and nothing may come from the IDEA env file.
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
  `mvn tidy:pom`. The enforcer's `dependencyConvergence` is on. The `apiguardian-api` pin in
  `<dependencyManagement>` is gone since logbook 4.2.0 (with Boot 4.1.1, HAS-145), which
  declares the version JUnit 6 does; should the two disagree again, pin it — do not exclude it.
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
