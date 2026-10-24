# database-service

[![CI](https://github.com/smart-home-automation-system/database-service/actions/workflows/CI.yml/badge.svg)](https://github.com/smart-home-automation-system/database-service/actions/workflows/CI.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=smart-home-automation-system_database-service&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=smart-home-automation-system_database-service)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=smart-home-automation-system_database-service&metric=vulnerabilities)](https://sonarcloud.io/summary/new_code?id=smart-home-automation-system_database-service)

![GitHub Release Date - Published_At](https://img.shields.io/github/release-date/smart-home-automation-system/database-service?style=plastic)
![GitHub Release](https://img.shields.io/github/v/release/smart-home-automation-system/database-service?style=plastic)

---

![GitHub top language](https://img.shields.io/github/languages/top/smart-home-automation-system/database-service?style=plastic)
![Java](https://img.shields.io/badge/java-21-yellow?style=plastic)
![SpringBoot](https://img.shields.io/badge/SpringBoot-4.1.2-blue?style=plastic)
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
sees - and the **device registry**: every device of the house with its room, its kind and
the way it is reached, which the device services read - and the **calendar of the house**:
which days are days off, by the law and by the household. PostgreSQL is accessed reactively over **R2DBC** (Spring Data R2DBC), the
schema is managed by **Flyway**, and the whole request path is non-blocking (Spring WebFlux
/ Reactor). Domain models come from the shared `smart-home-sdk`.

# Run locally

- Build: `mvn verify` (JDK 21). With a running **Docker** the build also starts a PostgreSQL in
  it (Testcontainers, `postgres:17-alpine`) and runs the migrations, the repositories and the
  API against that throwaway database — the test classes under
  `src/test/java/cloud/cholewa/data/postgres`. Without a Docker those classes are **skipped**,
  not failed: the build stays green and reports them in its count of skipped tests, so a
  change to a migration, an entity or a mapper is only checked on a machine that has one (CI
  does, and fails if it ever does not). The tests never connect to anything but that
  container.
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
`/home/device/configuration/**`, `/home/device/registry/**`, `/home/household/**` and
`/home/calendar/**` here (the device registry and the calendar since the gateway 0.5.0) — all
unauthenticated, like every route of the gateway, the writes included.

| Method | Path | Description |
|---|---|---|
| `POST` | `/home/device/configuration/eaton` | Register an Eaton device configuration (`EatonDeviceConfiguration`: point, room, type, gateway — all four required, `point` in the range 1..99). Returns `201 Created`; an incomplete body or a `point` outside the range returns `400 Bad Request`; a configuration already registered for that point + gateway returns `409 Conflict`. |
| | | *Since the registry (`V13`):* a configuration is stored as a device of the registry (below), so the `type` `other` - which has no kind there - is refused with `400`, and a point a device of the registry already holds is the same `409`. |
| `GET` | `/home/device/configuration/eaton?point=<n>&gateway=<name>` | Look up the configuration for a data point on a gateway. `point` is a number in the range 1..99, `gateway` is `blinds` or `lights`, matched case-insensitively (`amx-service` sends `BLINDS`). Returns `200 OK` with `EatonConfigurationResponse` - its `type` is `temperature sensor`, `blinds`, `light`, `dimmer`, `push button` or `motion sensor` (the last two since HAS-288, for `amx-service` on `smart-home-sdk` 1.9.0 or later); `404 Not Found` when no configuration exists for the pair, or the device there is of a kind the Eaton configuration has no type for; `400 Bad Request` when `point` is outside the range or not a number, or `gateway` is not one of the known values. |

## Device registry

The registry of the devices of the house (`SmartDevice` of `smart-home-sdk` >= 1.8.0): what a
device is, where it is and how it is reached. The device services (weather, blinds, light,
heating) read it; so will the dashboard. **It is the one table of devices**: the Eaton
configuration above is the same rows, seen through the API `amx-service` asks - a device
registered there shows up here, and the other way round. The gateway routes
`/home/device/registry/**` since its 0.5.0 (HAS-225), the writes below included: whoever reaches
the ingress can move or remove an Eaton device, and with it what `amx-service` reads for its
point.

| Method | Path | Description |
|---|---|---|
| `GET` | `/home/device/registry?kind=<kind>&room=<room>&enabled=<true\|false>` | The devices, sorted by room and then by name (ignoring case). Every filter is optional and they combine; `kind` and `room` take the values of the SDK (`opening sensor`, `living room`), in any case; a filter left empty (`kind=`) is a filter not given. `200 OK`, `[]` when nothing matches. `400` for a kind or a room the SDK does not know, or an `enabled` that is not a boolean. |
| `GET` | `/home/device/registry/{id}` | One device by the id the registry gave it. `200 OK`; `404` with the code `NOT_FOUND_DEVICE` for an id nothing is registered under; `400` for an id that is not a number. |
| `POST` | `/home/device/registry` | Registers a device; the body is a `SmartDevice`. `201 Created` with the device and the `id` it got - an `id` in the body is ignored. `400` for a body the model refuses (a required field, a bound - no code), `400` with the code `INVALID_DEVICE` for a device the registry refuses (below), `409` with the code `DEVICE_CONFLICT` when its binding is taken. |
| `PUT` | `/home/device/registry/{id}` | Replaces the device by the body, **whole**: what the body leaves out is gone, and `enabled` is taken from it like everything else. The `id`, the `kind` and the time of registration stay - a body of another kind is a `400` `INVALID_DEVICE`. `200 OK` with the device; `404` `NOT_FOUND_DEVICE`; the refusals of `POST`. |
| `POST` | `/home/device/registry/{id}/enable`, `.../{id}/disable` | Switches one device on or off for the services and leaves the rest of it. `200 OK` with the device; `404` `NOT_FOUND_DEVICE`. |
| `DELETE` | `/home/device/registry/{id}` | Removes the device for good. `204 No Content`; `404` `NOT_FOUND_DEVICE`; `409` `DEVICE_CONFLICT` for an opening sensor that blinds still name in `openingSensors`. |

```json
[
  {"id": 2, "name": "Kitchen ceiling", "room": "kitchen", "kind": "light", "enabled": false,
   "vendor": "eaton", "eaton": {"gateway": "lights", "point": 44}, "lightKind": "dimmer"},
  {"id": 1, "name": "Office window", "room": "office", "kind": "blinds", "enabled": true,
   "vendor": "shelly",
   "shelly": {"address": "192.0.2.17", "generation": 3, "deviceId": "shelly2pmg3-a1b2c3d4e5f6", "channel": 0},
   "placement": "external", "positioning": true, "openingSensors": [7]}
]
```

What a reader can rely on:

- **A device has exactly one binding** - `shelly`, `eaton` or `bthome` - and the other two are
  not in the JSON at all (not `null`, not `{}`). The schema guarantees the one.
- **The fields of one kind only** (`lightKind`, `placement`, `positioning`) are left out when
  the row has none; a missing `positioning` is "not known", never `false`.
- **`openingSensors` of blinds** (since 0.16.0, SDK 1.11.0, HAS-290) are the ids of the opening
  sensors on their window - in the order sent, two for a window with two sashes. Left out when
  there is none: a window without a sensor, which `blinds-service` takes as closed. Each id is a
  registered opening sensor; a switched-off one is still listed.
- **A relay with several outputs is a device per channel**, each with its own `id`; they share
  `shelly.address` and `shelly.deviceId` and differ by `shelly.channel` (0 to 3, an output). Since
  0.17.0 (SDK 1.12.0, HAS-260) a `presence sensor` may have a zone as its channel instead - 200 to
  299, as in `presencezone:200` of a Shelly Presence Gen4; any other kind with a zone, or a channel
  of 4 to 199, is `INVALID_DEVICE`. The same Presence may be a `bthome gateway` without a channel
  next to it.
- **`enabled: false` devices are in the answer** unless `enabled=true` is asked for - a service
  that must not use a switched-off device filters.
- **A Bluetooth sensor names no gateway**: `bthome` is its address alone. Any device of the
  kind `bthome gateway` may deliver its readings, and an address is in the registry once.
- **An `appliance`** (since 0.18.0, SDK 1.13.0, HAS-272) is a washing machine, a dryer or a
  dishwasher, registered as the Shelly plug it runs on, with `appliance` - how `appliance-service`
  reads a cycle from the draw of the plug and how it reminds the household:

  ```json
  {"id": 64, "name": "Dishwasher", "room": "kitchen", "kind": "appliance", "enabled": true, "vendor": "shelly",
   "shelly": {"address": "192.0.2.64", "generation": 1, "channel": 0},
   "appliance": {"runningAboveWatts": 20, "idleBelowWatts": 3, "minRunMinutes": 5, "finishedAfterMinutes": 10,
                 "reminderEveryMinutes": 30, "maxReminders": 4, "quietFrom": "22:00", "quietTo": "07:00"}}
  ```

  All eight settings are there for an appliance and none for another kind; the quiet times are a
  local time of the house, `HH:mm`. **A reader of every kind has to know the kind first**: a
  client on an SDK before 1.13.0 that reads the registry without `?kind=` (the dashboard) fails on
  it, so no appliance is registered before such a reader moved.
- **A device of the Eaton configuration has a name the registry gave it** - its room, its
  kind and its point (`Living room temperature sensor 45`) - because a configuration has none.
- A device is addressed by `id` here, unlike a member of the household (by name): it has no
  natural key across the three bindings.

What a writer has to know:

- **The registry ties a device together where the model does not** - a device that breaks one
  of these is a `400` with the code `INVALID_DEVICE`, and `details` says which:
  - exactly one binding;
  - the vendor `eaton` and the `eaton` binding go together and only together; a `shelly`
    binding is of the vendor `shelly`; a `bthome` binding is of `shelly` or `other`;
  - the kind says which bindings it is reached through:

    | kind | bindings |
    |---|---|
    | `blinds`, `light`, `push button` | `shelly`, `eaton` |
    | `opening sensor`, `motion sensor`, `presence sensor`, `temperature sensor` | `shelly`, `eaton`, `bthome` |
    | `weather station` | `bthome` |
    | `bthome gateway` | `shelly` |
    | `appliance` (since 0.18.0) | `shelly` - the plug the appliance runs on |

  - a Shelly channel is an output, 0 to 3, of any kind - or a zone, 200 to 299 (`presencezone:200`),
    of a `presence sensor` alone (since 0.17.0, HAS-260);
  - a Shelly of generation 2 or later comes with its `deviceId` - the id it reports under
    over MQTT. Without it the device could be driven and never heard: the services find the
    device of an event by that id. A Gen1 device has none to give;
  - `lightKind` only on a `light`; `placement` and `positioning` only on `blinds`. None of the
    three is required;
  - `openingSensors` only on `blinds`, each id once, no `null`, and each one a registered device
    of the kind `opening sensor` - an id nobody has, or a device of another kind, is named in
    `details`. An id below 1 fails the model (`400` without a code);
  - `appliance` (since 0.18.0, SDK 1.13.0, HAS-272) on an `appliance` and on nothing else, and an
    `appliance` always with it - its eight settings, each required and bounded by the model
    (`400` without a code), and `idleBelowWatts` below `runningAboveWatts`.
- **A binding is one device** - `409` with the code `DEVICE_CONFLICT`, and `details` names
  what is taken: a channel of a Shelly by its address, a channel of a Shelly by its MQTT id,
  a point of an Eaton gateway, a Bluetooth address. A Shelly without a channel is one device
  as a whole, next to which its channels may still be registered.
- **`PUT` is the whole device, not a patch.** Send everything the device should be; a client
  that edits a device reads it, changes it and sends it back. There is no partial update: the
  model has five required fields, and nothing in it tells "left out" from "cleared".
- **`PUT` without `openingSensors` removes the link** - the list is part of the whole device.
  An opening sensor that blinds name cannot be removed (`409` `DEVICE_CONFLICT`, `details`
  names the blinds): send the blinds without it first.
- **The kind cannot be changed**: the services keep what they know of a device under its id.
  A device of another kind is removed and registered again. The room, the name, the binding
  and with it the vendor may all change under one id.
- **The registry is the Eaton configuration**: moving an Eaton device to another room changes
  the room `amx-service` reads for the next datagram of its point; removing it leaves the
  point unconfigured (`404` to the lookup), and for a room sensor that is the end of that
  room's temperature readings. A point registered through either API is taken for the other.
- **Calls for one device go one after the other** - every write stores the whole row, as for
  the members of the household.

## Calendar

The calendar of the house (`CalendarDay` of `smart-home-sdk` >= 1.8.0): which days the
household works. The schedules that differ between workdays and days off - the blinds first -
ask for it. A day is a `day off` for one of three reasons, and a `workday` otherwise:

1. **the household added it** (`PUT` below) - the only days that are stored;
2. **it is a statutory holiday of Poland** - computed, never stored: 1 and 6 January, Easter
   Sunday and Monday, 1 and 3 May, Pentecost Sunday, Corpus Christi, 15 August, 1 and 11
   November, 24 (since 2025), 25 and 26 December;
3. **it is a Saturday or a Sunday.**

The gateway routes `/home/calendar/**` since its 0.5.0 (HAS-225).

| Method | Path | Description |
|---|---|---|
| `GET` | `/home/calendar/days?from=<date>&to=<date>` | Every day of the range, in the order of the calendar: `date`, `type` (`workday` / `day off`) and, for a day off, its `reason`. Both dates are required, written `2026-05-01` and nothing else (no time, no offset); the start is included and the end is not. `200 OK`. `400` (no code) for a date that cannot be read, a range that does not start before it ends, one longer than 2 years or one outside the years 2000 to 2099. |
| `PUT` | `/home/calendar/days-off/{date}` | The household adds a day off; the body is `{"reason": "..."}`, required, not blank, at most 100 characters, one line (no control characters); the spaces around it are not stored. **Idempotent**: a day set twice is stored once, with the reason of the second call. `200 OK` with the day as the calendar now answers it. `400` (no code) for a date that cannot be read or lies outside 2000 to 2099, and for a body without a reason. |
| `DELETE` | `/home/calendar/days-off/{date}` | Takes back a day the household added. `204 No Content`; `404` with the code `NOT_FOUND_DAY_OFF` when the day is not stored. |

```json
[ { "date": "2026-04-30", "type": "workday" },
  { "date": "2026-05-01", "type": "day off", "reason": "Labour Day" },
  { "date": "2026-05-02", "type": "day off", "reason": "Saturday" } ]
```

What a client has to know:

- **Read `type`, not `reason`.** The reason is text for people - what the household wrote, the
  English name of the holiday, or `Saturday` / `Sunday` - and may be reworded. It does not say
  which of the three made the day a day off.
- **The reason of the household comes first**: a holiday or a Saturday it added reads with its
  reason. Taken back, the day is a day off again by its rule, under the name of the rule.
- **A holiday or a weekend cannot be made a workday.** They are not stored, so a `DELETE` of
  one is the `404` - and the day stays a day off.
- **A workday carries no `reason`** - the field is left out.
- **The holidays are those of the law as it stands**: Epiphany counts from 2011 and Christmas
  Eve from 2025, the years they became days off. A holiday added by a later law needs a release.

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
- A member can also have **permissions** — what they may do in the dashboard beyond their
  role, e.g. `"permissions": ["heating_switch"]`: the switch of the heating of the whole house
  on their own page. Nobody has one until it is granted; like the rooms, an empty list is left
  out of the answer. A permission decides what the dashboard offers and nothing more — no
  service checks it.
- Each of the four things an update could change by accident has an operation of its own:
  `active` (activate / deactivate), the rooms (`PUT …/rooms`), the permissions
  (`PUT …/permissions`) and the devices. `PATCH` changes the name, the phone and — only when
  the body names one — the role.
- Send the changes of one member **one after the other**: each call writes the whole member
  from what it read a moment before, so a `PATCH` and a `PUT …/rooms` running at the same
  time can undo each other.

| Method | Path | Description |
|---|---|---|
| `GET` | `/home/household` | All members sorted by name, each with its `role`, `rooms` and `devices`. `200 OK` with `[]` while the registry is empty. |
| `GET` | `/home/household/profiles` | What the web dashboard needs, and nothing else: the **active** members sorted by name, each with `name`, `role`, `rooms` and `permissions` (`HouseholdProfile`; `rooms` and `permissions` left out when empty) — **no phone, no devices**. This is the read every browser in the house makes; a member who is switched off is not in it. `200 OK` with `[]` when nobody is active. |
| `POST` | `/home/household/member` | Register a member (`HouseholdMember`; `devices` in the body are ignored). Always created active; a `resident` unless the body says `"role": "admin"`; with the `rooms` and the `permissions` of the body, if any. `201 Created`; `409 Conflict` when the name (in any letter case — `anna` clashes with `Anna`) or the phone is taken; `400` for an unknown role, room or permission, or a room or a permission listed twice. |
| `PATCH` | `/home/household/member/{name}` | Change a member's name and phone, and the role when the body has one — without `role` (or with `null`) the member keeps theirs. Keeps the member's activity, rooms and permissions — `active`, `rooms` and `permissions` in the body are ignored. `200 OK` with the member, their rooms and devices; `404` for an unknown member; `409` when the new name (in any letter case) or phone belongs to another member — changing only the case of the own name is fine; `400` for an unknown role. |
| `PUT` | `/home/household/member/{name}/rooms` | Replace the member's rooms with the list in the body — a JSON array of room names in display order, e.g. `["sanctum", "office"]`; `[]` leaves the member without rooms. `200 OK` with the member; `404` for an unknown member; `400` for an unknown room, a room listed twice, `null` among them, or a body that is not an array. |
| `PUT` | `/home/household/member/{name}/permissions` | Replace the member's permissions with the list in the body — a JSON array of `MemberPermission` values, e.g. `["heating_switch"]`; `[]` takes them all away. `200 OK` with the member; `404` for an unknown member; `400` for an unknown permission, one listed twice, `null` among them, or a body that is not an array. |
| `DELETE` | `/home/household/member/{name}` | Remove a member together with their devices. `204 No Content`; `404` for an unknown member. |
| `POST` | `/home/household/member/{name}/activate` | Mark a member active. `200 OK` with the member and their devices; `404` for an unknown member. |
| `POST` | `/home/household/member/{name}/deactivate` | Mark a member inactive, e.g. while away for longer. `200 OK` with the member and their devices; `404` for an unknown member. |
| `POST` | `/home/household/member/{name}/device` | Register a device of the member (`MemberPhoneDetails`). `201 Created`; `404` for an unknown member; `409` when the MAC is already registered or the member already has a device of that name. |
| `PATCH` | `/home/household/member/{name}/device?mac=<mac>` | Change the name and MAC of the member's device. `200 OK`; `404` for an unknown member or device; `409` as above. |
| `DELETE` | `/home/household/member/{name}/device?mac=<mac>` | Remove the member's device. `204 No Content`; `404` for an unknown member or device. |
| `GET` | `/home/household/push-config` | The public VAPID key the installed application subscribes its browser with (since 0.18.0, HAS-272): `{"publicKey": "..."}` - `{}` when the deployment has none (`household.push.public-key`, in the manifest the environment variable **`HOUSEHOLD_PUSH_PUBLICKEY`** - a misspelt name binds nothing and leaves the answer `{}` without an error), and the application then offers no push. `200 OK`. |
| `GET` | `/home/push-subscriptions?members=` (limits) | Names are split on commas and matched as sent, without trimming: a member whose name holds a comma cannot be asked for, ` Ola` with a space matches nobody. |
| `PUT` | `/home/household/member/{name}/push-subscriptions` | Store the Web Push subscription of a browser of the member (`PushSubscription`: `endpoint` https, `keys` `p256dh` and `auth` base64url, optional `userAgent`; a `createdAt` in the body is ignored). **Upsert by the endpoint**: a browser that subscribes again replaces what it said, and one that subscribes for another member is from then on that member's. `200 OK` with the stored subscription; `404` for an unknown member; `409` with `MEMBER_SWITCHED_OFF` for a member who is switched off; `400` (no code) for a body the model refuses. |
| `DELETE` | `/home/household/member/{name}/push-subscriptions?endpoint=<endpoint>` | Remove a subscription of the member. `204 No Content`, also when the member had none of that endpoint (a browser may unsubscribe twice); `404` for an unknown member; `400` without an endpoint. |
| `GET` | `/home/push-subscriptions?members=<name>,<name>` | **Inside the cluster only** - not routed by the gateway: the subscriptions of the named members who are switched on, keys included, in the order they were stored; the read of `notification-service`. Names in any letter case; a name the registry does not know names nobody. `200 OK`, `[]` when there is none; `400` without a name, with an empty one or with more than 20. |

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
| `INVALID_HOUSEHOLD_MEMBER` | 400 | the rooms list a room twice, or contain `null`; the same for the permissions |
| `MEMBER_SWITCHED_OFF` | 409 | a push subscription for a member who is switched off (since 0.18.0) |
| `NOT_FOUND_MEMBER_DEVICE` | 404 | the member has no device with that MAC |
| `DEVICE_EXIST` | 409 | the MAC is registered already, or the member has a device of that name |
| `NOT_FOUND_DEVICE` | 404 | no device of the registry under that id |
| `INVALID_DEVICE` | 400 | a device the model accepts and the registry does not: no binding or two, a binding that is not of the vendor or of the kind, a Shelly of generation 2 or later without its `deviceId`, a field of another kind, a change of the kind, `openingSensors` that are no registered opening sensors (or repeat one, or hold `null`), a Shelly channel of 4 to 199 or a zone (200 to 299) on any kind but `presence sensor`, an `appliance` without its settings or settings on another kind, an `idleBelowWatts` not below `runningAboveWatts` |
| `DEVICE_CONFLICT` | 409 | the binding of the device is taken by another device of the registry; or the device is an opening sensor that blinds still name, and cannot be removed |
| `NOT_FOUND_DAY_OFF` | 404 | the household added no day off on that date - a holiday and a weekend are days off without being stored |

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
- **Migrations:** Flyway (JDBC driver) from `src/main/resources/db/migration`. The build
  applies all of them to an empty PostgreSQL in Docker, and runs the ones that change existing
  rows over rows an earlier version would have left (see Run locally).
- **Schema:** one table of devices, `devices` (`V13`, below). The Eaton configuration lived in
  `eaton_devices` until then (and in `device_configuration` before `V6`); `V13` copied its
  rows into `devices`, and `V14` dropped `eaton_devices` - after copying what a version before
  `V13` could still have stored there in the meantime.
- **Calendar** (`V15`): `days_off` - `date` (the primary key), `reason` (not blank, at most 100
  characters) and `created_at`. Only the days the household added; the holidays and the
  weekends are worked out by the service.
- **Constraints of an Eaton point** (`V7` on the old table, `V13` on `devices`): `(gateway,
  point)` is unique — it is the natural key of an Eaton
  device. Every gateway numbers its own devices, so the same `point` on a *different*
  gateway is perfectly valid and stays allowed; only the exact pair cannot repeat. `point`
  is additionally checked to be within `1..99`, the range an Eaton gateway addresses.
  A duplicate pair surfaces as `409` and an out-of-range `point` as `400`, not `500` —
  see the API table.
- **Device registry** (`V13`): `devices` - `name`, `room`, `kind`, `vendor`, `enabled`, the
  three bindings as columns (`shelly_address`, `shelly_generation`, `shelly_device_id`,
  `shelly_channel`; `eaton_gateway`, `eaton_point`; `bthome_address`) and `light_kind`, `placement`, `positioning`, and since `V16` `opening_sensors` (`BIGINT[]`, never `NULL`, `{}` by default; only blinds hold ids - `devices_opening_sensors_of_blinds` - and none is `NULL` - `devices_opening_sensors_no_null`). Checks: exactly one binding
  is filled, a binding is whole or absent, the vendor `EATON` goes with the Eaton binding and
  with no other, `light_kind` only on a light, `placement` and `positioning` only on blinds,
  and the bounds of the SDK models (address, generation 1..4, channel 0..3, or since `V17`
  200..299 when the kind is `PRESENCE_SENSOR`; point 1..99, the
  two id formats, the Bluetooth address). Unique: a channel of a Shelly, by its address
  (`devices_shelly_address_channel_uq`) and by the id it reports over MQTT
  (`devices_shelly_device_id_channel_uq`) - both indexes on `COALESCE(shelly_channel, -1)`,
  so a Shelly without a channel counts as one; a point of an Eaton gateway
  (`devices_eaton_gateway_point_uq`); a Bluetooth sensor, by its address
  (`devices_bthome_address_uq`).
- **Appliances** (`V18`): eight nullable columns of `devices`, `appliance_running_above_watts`
  to `appliance_max_reminders` (`INT`) and `appliance_quiet_from` / `appliance_quiet_to`
  (`TIME`, to the minute). All filled exactly when the kind is `APPLIANCE`
  (`devices_appliance_of_appliances`), within the bounds of the SDK (`devices_appliance_range`),
  idle below running (`devices_appliance_idle_below_running`).
- **Push subscriptions** (`V19`): `push_subscriptions` - `member_id` (`ON DELETE CASCADE`),
  `endpoint` (unique - `push_subscriptions_endpoint_uq`, https only), `p256dh` and `auth`
  (base64url of the lengths a browser gives), `user_agent`, `created_at`, and `last_used_at`
  for the sender to come (HAS-276), empty until then. A table of its own, never a column of
  `household_members`.
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
- **Permissions** (`V12`): `household_members.permissions`, an array column like the rooms,
  holding the `MemberPermission` constants (`HEATING_SWITCH`); empty for every member until
  one is granted.
