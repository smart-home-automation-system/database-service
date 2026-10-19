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

Four independent domains, one package each under `cloud.cholewa.data`, all following the same
layering `api` → `service` → `repository`, with MapStruct mappers between the entities and the
`smart-home-sdk` models:

- **`device.eaton`** — Eaton device configuration: which data point on which gateway is which
  device in which room. **No table of its own since `V13`** (HAS-222): a configuration is a row
  of `devices` with an Eaton binding, and this package is the API over those rows that
  `amx-service` asks. Consumer: `amx-service`, which looks up every
  incoming datagram (`GET /home/device/configuration/eaton?point=&gateway=`) — roughly every
  30 s, day and night, so this endpoint going quiet is what the Grafana rule
  `database-service not answering` watches.
- **`household`** — the household registry (HAS-150, epic HAS-147): members (`household_members`)
  and their Wi-Fi devices (`member_devices`, MAC addresses). Source of truth about who lives
  here; `presence-service` reads it to match UniFi clients to members and keeps only presence
  state and history itself. The API is in the README.

- **`device.registry`** — the device registry (HAS-222, epic HAS-218): every device of the house
  with its room, its kind and the way it is reached. Table `devices`. Read by the device
  services at their start and every few minutes, written by the administration page to come
  (HAS-231) and from Bruno. Routed by the gateway since its 0.5.0 (HAS-225).

- **`calendar`** - the calendar of the house (HAS-224): which days are days off. The statutory
  holidays of Poland are computed, the days the household adds are the table `days_off`. Read
  by the schedules of the blinds to come. Routed by the gateway since its 0.5.0 (HAS-225).

Routing: inside the cluster callers use k8s DNS (`http://database-service:6200`).
`api-gateway-service` routes `/device/configuration/**`, `/device/registry/**`, `/household/**`
and `/calendar/**` here (one `database` route with all of them; the registry and the calendar
since HAS-225). A new top-level path of this service - and a third branch of `/device` - needs
adding to that route, or it answers "No static resource" (404) from outside the cluster. **The
tails of that route are open**: an endpoint added below a routed path is reachable from outside
the day it is deployed, every method, without a gateway change. **Treat the whole service as
published**: the gateway matches the path as it was sent, so a dot segment below a tail
(`/household/../x`) is forwarded too, and an unrouted path is hidden only while no hop
normalises it. An endpoint that must stay inside the cluster does not belong in this service
as the route stands. Nothing on the gateway is authenticated — the names, phones and MACs of
the household registry, the names, rooms and LAN addresses of the device registry, and the
writes of both registries and of the calendar are open to whoever reaches the ingress.

## Household registry — the parts worth knowing before changing anything

- **Addressing by natural keys.** Members by `name`, devices by `mac` — both unique in the
  schema. No surrogate id is exposed; the SDK models have none. Keep it that way rather than
  adding ids a client would first have to look up.
- **Bodies are the SDK models** `HouseholdMember` / `MemberPhoneDetails` (smart-home-sdk ≥ 1.6.0)
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
  `withRooms(existing, …)` / `withPermissions(existing, …)` mappers copy `id`, `createdAt` (and
  `memberId` / `active` / `role` / `rooms` / `permissions`) from the found row. **A new column of `household_members` has to be added to every
  one of them** — MapStruct would otherwise write `null` over it on the next update. A fresh `toEntity(...)` on an update
  path silently creates a second row — this bug existed once.
- **`active` is changed only through `/activate` and `/deactivate`.** It defaults to `true` in
  the SDK model, so if PATCH honoured it, an update that just omitted the field would reactivate
  the member. PATCH keeps the stored value.
- **Role and rooms** (HAS-192, `V11`) — what the web dashboard shows a member:
  - **`role` is `null` when not sent** (the SDK model has no default, on purpose). `POST`
    without one stores `RESIDENT`; `PATCH` without one — or with an explicit `null` — keeps
    the stored role. Both are decided in **`HouseholdMemberMapper`** (`defaultValue` in
    `toEntity`, the expression in `toUpdatedEntity`) and nowhere else. Never give the role a
    default anywhere on the way in: an update of the phone alone would then turn an admin into
    a resident.
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
    PostgreSQL **array column** (`TEXT[]`) mapped to `List<RoomName>` — Spring Data R2DBC
    converts both ways, an empty list is `{}`. A response leaves an empty `rooms` out
    (`NON_EMPTY`). The mapping is Spring Data's and the driver's, not code of this service —
    `HouseholdRegistryOnPostgresTest` runs it on a real PostgreSQL (HAS-207; see Build, tests
    & gotchas), which is what tells whether a Spring Boot bump kept it.
  - **A constant renamed or removed in the SDK breaks reading**: a row still holding the old
    name cannot be converted, and `GET /household` then fails for the whole registry (the same
    holds for `room`, `kind`, `vendor`, `eaton_gateway`, `light_kind` and `placement` of `devices`). Such an SDK change comes with
    a migration of the rows here. **A renamed value is not**: SDK 1.10.0 (HAS-289) writes
    `HALL_UP` as `hall up` instead of `hall_up` - the rows keep `HALL_UP`, only the JSON
    changes, and the SDK still reads `hall_up` for one release
    (`DeviceRegistryControllerTest` pins both spellings). Every reader of the registries has
    to be on 1.10.0 before a room is set to it.
  - **Every write is the whole row.** `save()` writes all columns from what was read a moment
    before, so two requests for the same member running at once — `PATCH` and `PUT …/rooms`
    fired together by a form — can undo each other, and both answer 200. No version column
    guards it (one admin, a handful of edits a year); a client sends the calls **one after
    the other**. True of `PATCH` next to activate / deactivate as well.
  - `PATCH` **ignores** `rooms` in its body without checking them, as it ignores `active` and
    `devices` — a refusal would break a client that sends back the member it read.
- **Column defaults never apply.** Spring Data R2DBC writes every column of an entity, nulls
  included, so `DEFAULT TRUE` on `active`, `DEFAULT 'RESIDENT'` on `role` or a missing
  `created_at` do not help — the mappers set them. (The default of `role` did its one job in
  `V11`: the members that existed became residents.)
- **Duplicates are mapped by constraint name, per service.** No global processor for
  `DuplicateKeyException` (there was one for the Eaton message until HAS-150, and it leaked that
  message into every other duplicate): each service maps its own duplicates to a domain exception
  (409) — Eaton → `DeviceConfigurationExistsException` (the existence check and the
  `devices_eaton_gateway_point_uq` constraint of V13 give the same 409), household →
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
- **Permissions** (HAS-202, `V12`) — what a member may do in the dashboard beyond their role,
  first of all `heating_switch`. They are **the rooms once more**: a `TEXT[]` column of
  constant names, a list the SDK model cannot tell from "not sent", so `PATCH` ignores them,
  `POST` takes those of a new member, and **`PUT /member/{name}/permissions`** replaces them
  (`checkedPermissions`: one listed twice or a `null` is a 400 `INVALID_HOUSEHOLD_MEMBER`).
  Three things beyond the rooms:
  - **`withPermissions` is the fourth mapper that copies a row** (`toUpdatedEntity`,
    `withActive`, `withRooms`): each names every column, and a column forgotten in one of them
    is written as `null` by that operation — here it would take a permission away on a change
    of a phone number. The service tests assert the permission after each of the four, and
    `HouseholdRegistryOnPostgresTest` compares the stored row before and after each of them.
    (What the database does with such a `null` today, seen there with a mapper broken on
    purpose: `rooms` and `permissions` are `NOT NULL`, so the update is refused and the
    operation fails — the grant is not lost silently. A nullable column would be.)
  **Never call a blocking helper of `OnPostgres` (`row`, `rows`) inside `assertNext` of a
  `StepVerifier`**: it runs on a reactive thread and fails the expectation with a message
  that shows the value and not the cause (HAS-222). Take the result with `block(PATIENCE)`
  first, as `DeviceRegistryOnPostgresTest` does.
  - **A new value of `MemberPermission` must reach every reader before it is stored.**
    `presence-service` reads the whole registry in one call and fails on a value its SDK does
    not know. Today it is on an SDK without the field and ignores it; from the day it has the
    field, grant a new permission only after it runs the release that knows it.
  - **A rollout is safe both ways** (rehearsed on a throwaway PostgreSQL): the released version
    reads and writes next to the new column - its statements name their own columns - and a
    permission granted by the new pod survives a `PATCH` through the old one.
- **`GET /household/profiles` is the read of the web dashboard** (HAS-211) — every browser in
  the house calls it, at every start. It answers the **active** members with `name`, `role`,
  `rooms` and `permissions` as `HouseholdProfile`, a model with no phone and no devices. Three rules:
  - **Nothing else may ever be added to that body.** The model has no field for it (pinned in
    the SDK), and `should_return_profiles_of_active_members_without_reading_devices` compares
    whole profiles off the real mapper — MapStruct copies every property two classes share by
    name, so a `phone` added to the model would be filled from the row without a line changing
    here. What the dashboard needs beyond a profile gets its own decision, not a field.
  - **Leaving out the switched-off members is this service's rule**: the model has no
    `active`, so a client cannot tell. The filter is the derived query `findAllByActiveTrue`,
    run on a real PostgreSQL by `HouseholdRegistryOnPostgresTest` since HAS-207.
  - It reads the rows whole (the phone stops at the mapper) and never touches `member_devices`.
    A three-column projection would be a second, untested path for the `TEXT[]` rooms column.
  The full `GET /household` stays for `presence-service` and household administration; both
  are open to whoever reaches the gateway until it validates tokens.
- **An empty registry is `200 []`**, not 404 — `presence-service` polls it.
- `GET /household` reads the whole registry in two queries (members + all devices, grouped by
  `memberId`) — fine for ~10 members, no paging by design. `HouseholdMember` is not
  `Comparable`: sort with an explicit comparator (the no-arg `collectSortedList()` threw
  `ClassCastException` with two members).
- Deleting a member cascades to its devices in the database (`ON DELETE CASCADE`), not in code.

## Device registry - the parts worth knowing before changing anything

`device.registry` (HAS-222, `V13`, table `devices`): the devices of the house as `SmartDevice` of
`smart-home-sdk` >= 1.8.0 - the reads (HAS-222) and the writes (HAS-223, below).

- **A row is flat, the model is nested.** The three bindings (`shelly`, `eaton`, `bthome`) are
  columns of the one row; exactly one of them is filled. Each is told by the column it cannot
  do without - `shelly_address`, `eaton_gateway`, `bthome_address` - in the check of the schema
  (`devices_one_binding`) and in the mapper alike. Change one, change the other.
- **`SmartDeviceMapper` is written by hand, on purpose** - the one mapper here that is not
  MapStruct. MapStruct fills a nested target whenever its source object is there, so every
  device would come out with all three bindings, two of them objects of nulls; the model is
  written with `NON_EMPTY`, which drops a `null` and keeps an empty object, and a reader tells
  the binding by its presence. And the model has no setter for the id, only the builder.
  `SmartDeviceMapperTest` compares whole models, so a column added to the entity and forgotten
  in the mapper fails there once the test names it.
- **The query is built, not derived** (`DeviceRepository`, `R2dbcEntityTemplate` + `Criteria`):
  three optional filters. A hand-written `(:kind IS NULL OR kind = :kind)` leaves PostgreSQL
  unable to type a parameter that is null. An enum in a criterion reaches the database as the
  name of its constant - `DeviceRegistryOnPostgresTest` runs every filter on a real PostgreSQL.
- **The order is the service's**, in Java: the room and the name as a client reads them (the
  SDK value, so `bathroom up` before `office`), the name ignoring case, the id last.
  `SmartDevice` is not `Comparable` - an explicit comparator, as for the members.
- **A filter is the SDK value, read by the service** (`opening sensor`, `living room`): Spring
  would convert an enum parameter by the name of its constant. One the SDK does not know is a
  `ServerWebInputException` - 400 without a code, the way an unknown room in a body is answered.
  The case does not matter (the enums of the SDK read their values ignoring it) and an empty
  filter is no filter, like an empty `enabled`.
- **Unique per binding, by a name each** - `devices_shelly_address_channel_uq`,
  `devices_shelly_device_id_channel_uq`, `devices_eaton_gateway_point_uq`,
  `devices_bthome_address_uq`; the writes map a duplicate by these names (the
  rule of the household: renaming one in a migration breaks the mapping). **The two of a
  Shelly are indexes on an expression**, `(..., COALESCE(shelly_channel, -1))`: the task asked
  for a plain `UNIQUE (address, channel)`, under which a Shelly without a channel could be
  registered any number of times - two NULLs are never equal. A device may still be
  registered once without a channel (a gateway) and once per channel. **The second one, on
  the MQTT id, came from the review**: the services find the device of an event by that id,
  and a Shelly registered again under a new address would be two devices for one event. A
  Gen1 device has no id and is not counted by it.
- **The schema says what it can of "the binding fits the vendor and the kind"** - what needs
  one row only: the vendor `EATON` and the Eaton binding go together and only together
  (`devices_eaton_vendor_binding`), `light_kind` is of a light, `placement` and `positioning`
  are of blinds. The rest is `DeviceRules`, in the service: which kinds a Shelly or a Bluetooth
  binding may carry. PostgreSQL names
  the first check that fails in the alphabetical order of the constraints - a test of one
  check needs a row that breaks no other.
- **No `@NotNull` on the entity**: nothing validates an entity here, the schema says what may
  not be null. (The household entities carry such annotations from before; they do nothing.)
- **`id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY`** - without the `NOT NULL UNIQUE` the
  older tables add, which only buys a second index on the id.
- **The schema repeats the bounds of the SDK models** (generation 1..4, channel 0..3 or a zone
  200..299 of a presence sensor - `V17`, HAS-260, the one bound the SDK states wider, 0..299, point
  1..99, the formats): change them in the SDK and in a new migration together. The point range
  is now stated in a fifth place (see the Eaton section).
- **The id is exposed** - against "no surrogate id" of the household. A device has no natural
  key across three bindings, and the SDK model carries the id. The model also *reads* an id a
  client sends (`readOnly` only took the setter away): the writes ignore it - `POST` stores
  without one, `PUT` takes the id of the path.
- **One table of devices** (owner, 2026-10-12: "no two tables for the same thing"). The first
  version of this task left the Eaton configuration in `eaton_devices` next to `devices`, an
  Eaton point named in both with nothing tying them. `V13` now copies the configurations in
  and the Eaton API reads and writes `devices`:
  - **`EatonConfigurationMapper` is where the two vocabularies meet**: a `SmartDeviceType` is a
    `DeviceKind`, a `dimmer` is a `light` with `light_kind` `DIMMER`, and back; a `push button`
    and a `motion sensor` (HAS-288, SDK 1.9.0) are the kinds of the same names. A kind the old
    vocabulary has no word for (an opening or a presence sensor on a data point) is **no
    configuration at all - the lookup answers 404**, because `amx-service` throws on a datagram
    of the type `other` every time it arrives. `other` itself has no kind: `POST` refuses it
    (400, the code of an invalid configuration - until `V13` it was stored and never read), and
    the migration stops on such a row on purpose (`kind` and `name` come out NULL) - the house
    has none. All three switches of the mapper name every constant: a type or a kind added to
    the SDK stops the compiler there. The `CASE` of `V13` is the same mapping a third time
    (from before the two new types; a migration is never edited).
  - **`amx-service` reads what the lookup answers** and publishes a `DeviceEvent` for blinds,
    lights, dimmers (since its 2.1.0, HAS-229), push buttons and motion sensors (HAS-288). A new
    type is answered only once `amx-service` runs an SDK that knows it - on an older one every
    datagram of that point fails its lookup. The Eaton devices of the house were registered
    device by device on 2026-10-15, each one tested.
  - **A device registered by the registry is a configuration to the Eaton API**: `POST` for
    its point answers 409, and that API has no update or delete - a wrong room is corrected
    through the registry (`PUT`), not here.
  - **A configuration has no name, the registry needs one**: `<Room> <kind> <point>`
    (`Living room temperature sensor 45`), worded the same in the migration (SQL) and in the
    mapper (Java). `EatonConfigurationMapperTest` and the rehearsal in
    `SchemaMigrationOnPostgresTest` pin the same strings - change both.
  - **The lookup ignores `enabled`**: `amx-service` has to read the datagram of a device
    whatever the services do with it.
  - **`eaton_devices` is gone since `V14`** (HAS-223). `V13` left it in place because the
    version before it read the table while the two ran side by side in the rollout; the
    version after it (0.12.0) does not, so dropping it one release later cost nothing.
    `V14` first copies what `devices` lacks (`WHERE NOT EXISTS` by gateway and point, the
    mapping and the names of `V13` a fourth time): a configuration the old version stored
    after `V13` - during that rollout, or after a rollback - was in `eaton_devices` only. A
    row of the type `OTHER` stops `V14` as it stops `V13`, and the table stays. **Worth
    repeating for any table a migration replaces: copy in one release, drop in the next.**
    A rollback below `V13` is no longer possible without restoring the table by hand.
  - The room sensors of the AMX system are the kind `temperature sensor`, added to the SDK for
    this (1.8.0). At the migration the house had 13 of them, all on the gateway `BLINDS`, and
    nothing else in the table.
- **A Bluetooth sensor is bound to no gateway** (owner, 2026-10-12: a second gateway in range
  should be redundancy, not a second device). The row has `bthome_address` and no gateway
  column; the address is unique in the registry (`devices_bthome_address_uq`), so a sensor is
  one device of one kind. Which gateways may deliver it is not stored: every enabled device
  of the kind `bthome gateway`.
- **The writes** (HAS-223): `POST`, `PUT /{id}`, `POST /{id}/enable|disable`, `DELETE /{id}`.
  - **`PUT`, not `PATCH`** (owner, 2026-10-12, against the wording of the task). `SmartDevice`
    has five required fields, so a part of it does not get past `@Valid`; a `PATCH` would
    have needed a model of its own in the SDK, with "left out means unchanged" and no way to
    clear a field. `PUT` replaces the device by the payload, whole. **`enabled` is taken from
    it** - the model has no default for it, which is the whole reason the household needed
    separate operations for `active`; `enable` / `disable` exist as the switch of a list.
  - **The id, the kind and `created_at` survive a `PUT`; nothing else does.** A change of the
    kind is refused (400): the services will keep what they know of a device under its id.
    The binding and the vendor may change - an Eaton actor replaced by a Shelly keeps its id.
  - **`DeviceRules` is what the model leaves open**: one binding; the vendor `eaton` only with
    the Eaton binding and the other way round; a Shelly binding only for the vendor `shelly`
    (a Bluetooth sensor is `shelly` or `other`); and per kind the bindings it is reached
    through - `blinds` / `light` / `push button`: shelly, eaton; the four sensors: any; `weather station`:
    bthome; `bthome gateway`: shelly. The switch names every kind, so a kind added to the SDK
    stops the compiler there. The matrix is the service's own reading of "the binding fits
    the vendor and the kind" - the README states it; change both.
  - **A Shelly of generation 2 or later needs its `deviceId`** (owner, 2026-10-12; the SDK
    leaves it optional, for Gen1). Without it no MQTT event finds the device - it can be
    driven and never heard, with no error anywhere - and the unique index on the id does not
    count the row, so the same Shelly could be registered again under a new address. The
    schema does not say it: a row written past the API may still lack the id.
  - **Not asked for**: a `lightKind` on a light, a `placement` on blinds - "not known" is an
    answer the model allows.
  - **The rules answer before the schema does** - in words, by the values a client sends. The
    one bound the model and the schema count differently is the name: `@Size` counts UTF-16
    units, `char_length` characters, so the rules count code points. Anything that still
    reaches a `CHECK` is the bare 400 "Data integrity violation" of `cholewa-commons`;
    `DeviceRegistryWritesOnPostgresTest` asserts that a body the model refuses is not
    answered that way.
  - **A taken binding is found by the schema alone** (`DuplicateKeyException`, mapped by the
    name of the index to 409 `DEVICE_CONFLICT`): no check before the write - the registry has
    one writer. When both indexes of a Shelly would fire, PostgreSQL names the address one.
  - **On `PUT` the rules run before the row is read**, so a refused device runs no query -
    and a body of another kind may be told about its binding before it is told that the
    kind cannot change. Both are `INVALID_DEVICE`.
  - **Every write stores the whole row** (`R2dbcEntityTemplate.update`). `DeviceEntityMapper`
    names every column for a new row and a replacement; `withEnabled` copies the row with
    `toBuilder`. `DeviceEntityMapperTest` maps a device with everything filled and asserts
    that no column comes out null - the test a new column has to pass.
  - **A write here is a write to the Eaton configuration**: a `PUT` that moves an Eaton
    device changes the room `amx-service` reads for its point, a `DELETE` leaves the point
    unconfigured - for a room sensor, the end of that room's readings in `heating-service`.
    Nothing warns; the administration page (HAS-231) should.
- **The opening sensors of blinds** (HAS-290, `V16`, SDK 1.11.0): `opening_sensors BIGINT[]` of
  the ids of the sensors on the window of one blinds - per blind, never per room; `blinds-service`
  reads a window without a sensor as closed.
  - **An array column, the rooms of a member once more**, not a join table: it is read and
    replaced with the whole device and keeps the order. What a foreign key would give is code:
    `DeviceRules` keeps the list to blinds, without a `null` or a repeat (`List.of()` throws on
    `contains(null)` - hence the stream); the service asks for the ids
    (`DeviceRepository.findAllById`) and names every one that is no registered opening sensor
    (`INVALID_DEVICE`); `remove` refuses a sensor that blinds still name (`DEVICE_CONFLICT`,
    the blinds in `details`), found by reading the blinds - a few dozen rows, one writer.
    "Re-kinding" a sensor needs no rule of its own: no kind changes on a `PUT`.
  - **`NOT NULL DEFAULT '{}'` is for the rollout**: the released version's entity does not
    name the column, so its inserts get the default and its `PUT` leaves the link alone
    (rehearsed with 0.15.1 on a throwaway PostgreSQL). It also deletes a sensor in use
    without asking - only while the two run side by side.
  - **`SmartDeviceMapper` leaves an empty list unset** (the builder leaves it `null`): the JSON
    omits it either way, and the comparisons of whole models stay as they were.
    `DeviceEntityMapper` writes `List.of()` for a list sent as `null`.
  - **Read it back as text** in the tests on PostgreSQL (`array_to_string`): the driver answers
    a `Long[]`, equal to nothing but itself.
- **A new `DeviceKind` must reach every reader before a row holds it**, and a constant renamed
  in the SDK needs a migration of the rows here - the rules of the roles and the rooms.

## Calendar - the parts worth knowing before changing anything

`calendar` (HAS-224, `V15`, table `days_off`): which days the household works, answered as
`CalendarDay` of the SDK. Read by the schedules of `blinds-service` to come; the days of the
household will be written by the dashboard and from Bruno.

- **Only the word of the household is stored.** The holidays are computed (`PolishHolidays`)
  and the weekends read off the date - a table of holidays would have to be filled for every
  year, and a year nobody filled would read as a year without holidays.
- **`PolishHolidays` knows the law, not only the dates**: Epiphany from 2011, Christmas Eve from
  2025 (the task listed 6 January for the whole range). A holiday the law adds is a line there,
  a test in `PolishHolidaysTest` and a release. The years 2026 to 2030 are written out in that
  test by hand, from a printed calendar - not computed a second time.
- **2000 to 2099 is a bound of the API, not of the Easter rule** (which holds for any Gregorian
  year): a range outside it is a 400, and so is a day off set outside it - it could never be
  read back.
- **The order of the reasons is the household, the holiday, the weekend** (`CalendarService.day`).
  `CalendarDay` has no field for *why* a day is off beyond the text, and no client should need
  one: the schedules read `type`.
- **The reason is required** (owner's task said `{reason}`; required is the choice that can be
  relaxed later without breaking a caller). `DayOffRequest` is a record of this service, the
  one body here that is not an SDK model: the SDK has the day as it is answered, with a `type`
  a client does not choose. Its bound, 100, is the one of `CalendarDay.reason` and of the
  column - three places.
- **`DayOffRepository` is not an `R2dbcRepository`**: the key is the date a client names, and
  `save()` of an entity whose id is set is always an `UPDATE`. "Set this day" is one statement,
  `INSERT ... ON CONFLICT (date) DO UPDATE`, which keeps `created_at` of a day set again - and
  it is PostgreSQL's own, so `CalendarOnPostgresTest` is its only test.
- **Dates are read by a pattern, said on every parameter** (`@DateTimeFormat(pattern =
  "yyyy-MM-dd")`). Left to the default, Spring reads a `LocalDate` parameter in the short form
  of the locale of the JVM; `iso = DATE` - the first version - also reads `2026-05-04+02:00` and
  drops the offset without a word. Spring resolves a pattern strictly, so 30 February is a 400.
- **Check the years before adding to a date** (`checkRange`): a year of nine digits is a
  `LocalDate` too, and `plusYears` on the last of them throws - a 500 where a 400 was meant.
- **The reason is one line of text**: `DayOffRequest` refuses a control character (NUL is
  valid in JSON and not storable as text in PostgreSQL), and the service stores the reason
  without the spaces around it.
- **A refused body is answered by the name of its field** (`details: "reason"`) - that is how
  `cholewa-commons` answers a `WebExchangeBindException`; a `message` on the constraints of the
  record would be shown nowhere.
- **Nothing can make a holiday a workday.** Not asked for; it would be a second kind of row
  (a day *on*), not a `DELETE`.
- A new table for the tests on PostgreSQL: `OnPostgres.emptyTheTables` empties it too.

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
  cannot share a constant with: the CHECK of `V13` on `devices.eaton_point` (and of `V7` on the old table), the SDK schema (`eaton.yaml`, the bounds of
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
  `sslMode` `REQUIRE`), and nothing may come from the IDEA env file. Since HAS-207 the build
  does the part of this that needs no released image (`SchemaMigrationOnPostgresTest`: every
  migration from empty, and the ones that change rows over rows written before them); the
  rehearsal is still the only check of **the released version running next to the new
  schema**, which is what a rolling deploy does.
- **`database.ssl-mode` set to `disable` switches SSL off in the pooled connection factory** —
  that much the tests on PostgreSQL prove: they reach a container without SSL through the
  `ConnectionFactory` of `cholewa-commons` with that property. Nothing more is proven. In the
  rehearsal of HAS-192 `-Ddatabase.ssl-mode=DISABLE` given to `docker run` had no effect, and
  why is **a guess, not re-run on that image**: the `ENTRYPOINT` is
  `java … -jar application.jar`, so what follows the image name lands after `-jar`, as a
  program argument, where `-D` means nothing — which would make it
  `--database.ssl-mode=disable`, or the environment variable `DATABASE_SSLMODE`, for an image.
  **Tried in the rehearsal of `V13` (HAS-222): `--database.ssl-mode=disable` after the image
  name works** - the released image ran against a `postgres:17-alpine` without SSL, with the
  five `database-*` values as `-e` variables. So a rehearsal no longer needs a PostgreSQL
  that speaks SSL.
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

- `mvn verify` (JDK 21; with a running Docker it also runs the tests on PostgreSQL, below).
  `tidy-maven-plugin:check` runs in `verify`, so after editing the pom run
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
- **The tests on a real PostgreSQL** (HAS-207) are the classes under
  `src/test/java/cloud/cholewa/data/postgres`, all extending `OnPostgres`:
  `SchemaMigrationOnPostgresTest`, `HouseholdRegistryOnPostgresTest`,
  `EatonConfigurationOnPostgresTest`, `DeviceRegistryOnPostgresTest`,
  `DeviceRegistryWritesOnPostgresTest`, `CalendarOnPostgresTest`. What to know before touching them:
  - **They start the whole application** (`@SpringBootTest`, nothing mocked) on a
    `postgres:17-alpine` in Docker (Testcontainers, versions managed by Boot). The `test`
    profile switches Flyway off; `OnPostgres` switches it on and points `database.*` and
    `spring.flyway.*` at the container, so the schema is built by **the application's own
    Flyway** from the real migrations, and the queries run through the pooled connection of
    `cholewa-commons` (with `database.ssl-mode=disable`). Not a `@DataR2dbcTest` slice like
    the history tests of `heating-service` and `water-service`: here the services, the
    mappers and the error mapping are the subject, and the slice loads none of them.
  - **Every address is set explicitly**, the JDBC URL of Flyway included — an environment
    variable or an env file of the IDE must never be able to point these tests at the
    production database. Keep it that way when adding a property.
  - **One container, one context for all of them**: the container is started on first use and
    never stopped (Ryuk removes it with the JVM). A `@Container` field would stop it after
    each class while Spring keeps the cached context, whose pool then points at a dead port.
    The tables are emptied before every test.
  - **Rows are read back with plain SQL**, the arrays written out (`array_to_string`) — an
    assertion through the entity would have the mapping under test on both sides.
  - **Without a Docker they are skipped, not failed** (`@Testcontainers(disabledWithoutDocker
    = true)`): `mvn verify` stays green and the skipped classes show in its count.
    `DockerInCiTest` closes the hole that leaves: it runs only where `CI=true` (GitHub
    Actions) and fails when there is no Docker. On a developer machine it is itself the one
    skipped test of a normal build — `Skipped: 1` is the expected tail, `Skipped: 4` or more
    means the Docker was not running.
  - **What they are for**: a Spring Boot / Spring Data / driver bump, a new migration, a new
    column (add it to the before/after comparisons), a renamed constraint. A migration that
    changes existing rows gets a rehearsal in `SchemaMigrationOnPostgresTest`: migrate a
    schema of its own to the version before, write the old rows, migrate on.
  - They cost about 10 s of a build, most of it the start of the container and the context.

## CI/CD & deployment

`CI.yml` (build on push to `main`/`feature/**`), `sonar.yml` (SonarCloud + JaCoCo), `release.yml`
(GitHub release → Docker image `magikabdul/database-service` with the tag as version). The
manifest is `deployment-tools/workshop/database-service.yaml` (image tag pinned there, env block
with the database properties from the secret). Release flow: the `release` skill.
