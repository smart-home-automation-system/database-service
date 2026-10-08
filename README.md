# database-service

[![CI](https://github.com/smart-home-automation-system/database-service/actions/workflows/CI.yml/badge.svg)](https://github.com/smart-home-automation-system/database-service/actions/workflows/CI.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=smart-home-automation-system_database-service&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=smart-home-automation-system_database-service)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=smart-home-automation-system_database-service&metric=vulnerabilities)](https://sonarcloud.io/summary/new_code?id=smart-home-automation-system_database-service)

![GitHub Release Date - Published_At](https://img.shields.io/github/release-date/smart-home-automation-system/database-service?style=plastic)
![GitHub Release](https://img.shields.io/github/v/release/smart-home-automation-system/database-service?style=plastic)

---

![GitHub top language](https://img.shields.io/github/languages/top/smart-home-automation-system/database-service?style=plastic)
![Java](https://img.shields.io/badge/java-21-yellow?style=plastic)
![SpringBoot](https://img.shields.io/badge/SpringBoot-4.1.1-blue?style=plastic)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=smart-home-automation-system_database-service&metric=coverage)](https://sonarcloud.io/summary/new_code?id=smart-home-automation-system_database-service)
[![Lines of Code](https://sonarcloud.io/api/project_badges/measure?project=smart-home-automation-system_database-service&metric=ncloc)](https://sonarcloud.io/summary/new_code?id=smart-home-automation-system_database-service)

![GitHub issues](https://img.shields.io/github/issues/smart-home-automation-system/database-service?style=plastic)
![GitHub contributors](https://img.shields.io/github/contributors/smart-home-automation-system/database-service?style=plastic)
![GitHub pull requests](https://img.shields.io/github/issues-pr-raw/smart-home-automation-system/database-service?style=plastic)

![GitHub last commit](https://img.shields.io/github/last-commit/smart-home-automation-system/database-service?style=plastic)
![GitHub commit activity](https://img.shields.io/github/commit-activity/m/smart-home-automation-system/database-service?style=plastic)

---

# Description

Persistence facade for the smart-home-automation-system — the other services do not talk to
the database directly, they go through this one. It stores the **configuration of
Eaton devices** (which data point on which gateway corresponds to which device type in
which room) and the **household registry**: the household members and the Wi-Fi devices
(MAC addresses) that represent them, which `presence-service` reads to tell who is at home,
and each member's **role and rooms**, from which the web dashboard decides what that person
sees. PostgreSQL is accessed reactively over **R2DBC** (Spring Data R2DBC), the
schema is managed by **Flyway**, and the whole request path is non-blocking (Spring WebFlux
/ Reactor). Domain models come from the shared `smart-home-sdk`.

# Run locally

- Build: `mvn verify` (JDK 21).
- Ports: local profile `6005` (management `8005`); in the deployed `home` profile the
  service listens on `6200` and Actuator on `8200` like every service in the cluster. The
  ingress routes only 6200, so Actuator is reachable inside the cluster only — that is where
  the Kubernetes probes hit `/actuator/health/{readiness,liveness}` and Prometheus scrapes
  `/actuator/prometheus`.
- Requires a reachable PostgreSQL instance. The connection properties (`database-host`,
  `database-port`, `database-name`, `database-user`, `database-password`) are bound to the
  `database.*` prefix that `cholewa-commons` consumes — the library builds the pooled
  `ConnectionFactory`, this service declares no `DbConfig` of its own. They have placeholder
  defaults and the pool does not open connections eagerly, so the context starts without them
  and fails on the first query instead. Only `database.pool.max-size: 4` is pinned here, as
  this service's share of the 22 backend connections the managed database allows — sized so
  that a rollout, when the old and the new pod each hold a pool, still fits the budget; the
  rest of the pool settings come from the library defaults.
- Flyway derives its JDBC URL from those same properties
  (`jdbc:postgresql://<database-host>:<database-port>/<database-name>`, defaulting to
  `localhost:5432`), so a local run needs no extra flag. Override with `--flyway-url=...`
  only when migrations have to target a different URL than the connection properties
  describe; the deployed environment supplies it explicitly.
- Flyway is enabled in `home`/`local` and disabled in the `test` profile.

# API

Base path `/home` (`spring.webflux.base-path`). Inside the cluster the services call it
directly over k8s DNS (`http://database-service:6200`) — `amx-service` for the Eaton lookup
(`internal.service.database` in its configuration). From outside, `api-gateway-service` routes
`/home/device/configuration/**` and `/home/household/**` here — both unauthenticated, like every
route of the gateway.

| Method | Path | Description |
|---|---|---|
| `POST` | `/home/device/configuration/eaton` | Register an Eaton device configuration (`EatonDeviceConfiguration`: point, room, type, gateway — all four required, `point` in the range 1..99). Returns `201 Created`; an incomplete body or a `point` outside the range returns `400 Bad Request`; a configuration already registered for that point + gateway returns `409 Conflict`. |
| `GET` | `/home/device/configuration/eaton?point=<n>&gateway=<name>` | Look up the configuration for a data point on a gateway. `point` is a number in the range 1..99, `gateway` is `blinds` or `lights`, matched case-insensitively (`amx-service` sends `BLINDS`). Returns `200 OK` with `EatonConfigurationResponse`; `404 Not Found` when no configuration exists for the pair; `400 Bad Request` when `point` is outside the range or not a number, or `gateway` is not one of the known values. |

## Household registry

Members are addressed by **name** and their devices by **MAC address**: both are unique in
the database, so a client never has to look up a surrogate id. Bodies are the
`smart-home-sdk` models `HouseholdMember` (name 3–50 characters, phone in the international
**E.164** format without spaces or dashes, e.g. `+48505602702` — it is an SMS recipient, and
formatting it for display is up to the client) and
`MemberPhoneDetails` (name up to 50 characters, MAC lowercase and colon-separated, e.g.
`aa:bb:cc:dd:ee:ff`); a body breaking those rules returns `400 Bad Request`.

Since 0.9.0 a member also has a **role** (`admin` or `resident`) and **rooms** (names of
`RoomName`, e.g. `office`, `living room`) — what the web dashboard shows that person:

```json
{ "name": "Anna", "phone": "+48505602702", "active": true,
  "role": "admin", "rooms": ["sanctum", "office"],
  "devices": [ { "name": "iPhone", "mac": "aa:bb:cc:dd:ee:ff" } ] }
```

- A member always has a role; one registered without it is a `resident`.
- The rooms come back in the order they were given — the order they are shown in. **A member
  without rooms has no `rooms` field at all** (empty lists are left out, like `devices`): read
  a missing `rooms` as none.
- Each of the three things an update could change by accident has an operation of its own:
  `active` (activate / deactivate), the rooms (`PUT …/rooms`) and the devices. `PATCH` changes
  the name, the phone and — only when the body names one — the role.
- Send the changes of one member **one after the other**: each call writes the whole member
  from what it read a moment before, so a `PATCH` and a `PUT …/rooms` running at the same
  time can undo each other.

| Method | Path | Description |
|---|---|---|
| `GET` | `/home/household` | All members sorted by name, each with its `role`, `rooms` and `devices`. `200 OK` with `[]` while the registry is empty. |
| `GET` | `/home/household/profiles` | What the web dashboard needs, and nothing else: the **active** members sorted by name, each with `name`, `role` and `rooms` (`HouseholdProfile`; `rooms` left out when empty) — **no phone, no devices**. This is the read every browser in the house makes; a member who is switched off is not in it. `200 OK` with `[]` when nobody is active. |
| `POST` | `/home/household/member` | Register a member (`HouseholdMember`; `devices` in the body are ignored). Always created active; a `resident` unless the body says `"role": "admin"`; with the `rooms` of the body, if any. `201 Created`; `409 Conflict` when the name (in any letter case — `anna` clashes with `Anna`) or the phone is taken; `400` for an unknown role or room, or a room listed twice. |
| `PATCH` | `/home/household/member/{name}` | Change a member's name and phone, and the role when the body has one — without `role` (or with `null`) the member keeps theirs. Keeps the member's activity and rooms — `active` and `rooms` in the body are ignored. `200 OK` with the member, their rooms and devices; `404` for an unknown member; `409` when the new name (in any letter case) or phone belongs to another member — changing only the case of the own name is fine; `400` for an unknown role. |
| `PUT` | `/home/household/member/{name}/rooms` | Replace the member's rooms with the list in the body — a JSON array of room names in display order, e.g. `["sanctum", "office"]`; `[]` leaves the member without rooms. `200 OK` with the member; `404` for an unknown member; `400` for an unknown room, a room listed twice, `null` among them, or a body that is not an array. |
| `DELETE` | `/home/household/member/{name}` | Remove a member together with their devices. `204 No Content`; `404` for an unknown member. |
| `POST` | `/home/household/member/{name}/activate` | Mark a member active. `200 OK` with the member and their devices; `404` for an unknown member. |
| `POST` | `/home/household/member/{name}/deactivate` | Mark a member inactive, e.g. while away for longer. `200 OK` with the member and their devices; `404` for an unknown member. |
| `POST` | `/home/household/member/{name}/device` | Register a device of the member (`MemberPhoneDetails`). `201 Created`; `404` for an unknown member; `409` when the MAC is already registered or the member already has a device of that name. |
| `PATCH` | `/home/household/member/{name}/device?mac=<mac>` | Change the name and MAC of the member's device. `200 OK`; `404` for an unknown member or device; `409` as above. |
| `DELETE` | `/home/household/member/{name}/device?mac=<mac>` | Remove the member's device. `204 No Content`; `404` for an unknown member or device. |

The `mac` query parameter is matched case-insensitively (`AA:BB:…` finds `aa:bb:…`).

**Which MAC address to register.** Phones hide their hardware address on Wi-Fi, so register
the address the phone actually uses **on the home network**, as the UniFi controller shows it
for that client:

- **iPhone / iPad** — Settings → Wi-Fi → ⓘ next to the home network → *Private Wi-Fi
  Address*. Keep it on **Fixed** (the address then stays stable for that network) and do
  **not** switch the home network to *Rotating* (iOS 18): a rotating address changes over
  time and the member would stop being recognised.
- **Android 10+** — uses a stable randomized address per network by default (*Use randomized
  MAC*); leave that setting as it is for the home network and register the address shown in
  the network details.

Forgetting and re-joining the network can give the phone a new address — update the device
then (`PATCH …/device?mac=<old>`).

Errors are rendered through `cholewa-commons`' `GlobalErrorExceptionHandler`, so failures
come back in the shared `Errors` JSON contract. An error of the domain also carries a `code` —
a stable name of the cause, for a caller that has to tell errors apart without parsing text:

```json
{ "errors": [ { "message": "Device configuration not found",
                "details": "Device not found for point: 7 on gateway: blinds",
                "code": "NOT_FOUND_DEVICE_CONFIGURATION" } ] }
```

| `code` | Status | When |
|---|---|---|
| `NOT_FOUND_DEVICE_CONFIGURATION` | 404 | no Eaton configuration for the point + gateway |
| `CONFIGURATION_EXIST` | 409 | a configuration is already registered for the point + gateway |
| `UNKNOWN_GATEWAY` | 400 | `gateway` is not one of the known values |
| `NOT_FOUND_HOUSEHOLD_MEMBER` | 404 | no member of that name |
| `HOUSEHOLD_CONFLICT` | 409 | the name or the phone belongs to another member |
| `INVALID_HOUSEHOLD_MEMBER` | 400 | the rooms list a room twice, or contain `null` |
| `NOT_FOUND_MEMBER_DEVICE` | 404 | the member has no device with that MAC |
| `DEVICE_EXIST` | 409 | the MAC is registered already, or the member has a device of that name |

The codes are API: a caller may branch on them, so they do not change without their callers.
`message` and `details` are for people and may be reworded. Every other error — a rejected
body or parameter, an unknown path, an unexpected failure — has no `code`, which is how a 404
of the routing differs from "no such record". That includes a role or a room the registry
does not know: the value fails while the body is read, and the answer is a `400` naming it —

```json
{ "errors": [ { "message": "Malformed request body", "details": "Unexpected value 'attic'" } ] }
```

# Database

- **Access:** reactive, via `r2dbc-postgresql` and Spring Data R2DBC repositories.
- **Migrations:** Flyway (JDBC driver) from `src/main/resources/db/migration`.
- **Schema:** table `eaton_devices` (`point`, `room`, `type`, `gateway` + audit timestamps).
  The original `device_configuration` table was superseded by it and dropped in `V6`.
- **Constraints** (`V7`): `(point, gateway)` is unique — it is the natural key of an Eaton
  device. Every gateway numbers its own devices, so the same `point` on a *different*
  gateway is perfectly valid and stays allowed; only the exact pair cannot repeat. `point`
  is additionally checked to be within `1..99`, the range an Eaton gateway addresses.
  A duplicate pair surfaces as `409` and an out-of-range `point` as `400`, not `500` —
  see the API table.
- **Household registry** (`V8`–`V10`): `household_members` (`name` unique ignoring letter case
  since `V10`, `phone` unique, phone
  checked to be E.164 since `V9` — `V8` stored `xxx-xxx-xxx` and `V9` converted the existing
  numbers to `+48…`; `active` defaulting to true) and `member_devices` (`mac` unique across the whole
  registry and format-checked, device `name` unique per member, `member_id` with
  `ON DELETE CASCADE`). The `active` and `created_at` defaults are set in the mappers, not
  left to the column defaults: Spring Data R2DBC writes every column of an entity, nulls
  included, so a column default would never apply.
- **Role and rooms** (`V11`): `household_members.role` (`ADMIN` / `RESIDENT`, checked; the
  column default `RESIDENT` is what made every member registered before a resident) and
  `household_members.rooms`, an **array column** (`TEXT[]`) holding the `RoomName` constants in display
  order — the rooms of a member are only ever read and replaced as a whole. The schema refuses
  a `NULL` among them; that a room is listed once is checked by the service, which can say so
  in its answer.
