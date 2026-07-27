# database-service

[![CI](https://github.com/smart-home-automation-system/database-service/actions/workflows/CI.yml/badge.svg)](https://github.com/smart-home-automation-system/database-service/actions/workflows/CI.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=smart-home-automation-system_database-service&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=smart-home-automation-system_database-service)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=smart-home-automation-system_database-service&metric=vulnerabilities)](https://sonarcloud.io/summary/new_code?id=smart-home-automation-system_database-service)

![GitHub Release Date - Published_At](https://img.shields.io/github/release-date/smart-home-automation-system/database-service?style=plastic)
![GitHub Release](https://img.shields.io/github/v/release/smart-home-automation-system/database-service?style=plastic)

---

![GitHub top language](https://img.shields.io/github/languages/top/smart-home-automation-system/database-service?style=plastic)
![Java](https://img.shields.io/badge/java-21-yellow?style=plastic)
![SpringBoot](https://img.shields.io/badge/SpringBoot-4.1.0-blue?style=plastic)
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
the database directly, they go through this one. Today it stores the **configuration of
Eaton devices**: which data point on which gateway corresponds to which device type in
which room. PostgreSQL is accessed reactively over **R2DBC** (Spring Data R2DBC), the
schema is managed by **Flyway**, and the whole request path is non-blocking (Spring WebFlux
/ Reactor). Domain models come from the shared `smart-home-sdk`.

# Run locally

- Build: `mvn verify` (JDK 21).
- Ports: local profile `6005` (management `8005`); in the deployed `home` profile the
  service listens on `6200` like every service in the cluster.
- Requires a reachable PostgreSQL instance. The R2DBC connection properties
  (`database-host`, `database-port`, `database-name`, `database-user`, `database-password`)
  have placeholder defaults, so the context starts without them and fails on the first
  query instead.
- `flyway-url` has no usable default — the placeholder is not a JDBC URL, so Flyway
  refuses to parse it and the application does not start. Pass a real one, e.g.
  `--flyway-url=jdbc:postgresql://localhost:5432/<database>`.
- Flyway is enabled in `home`/`local` and disabled in the `test` profile.

# API

Base path `/home` (`spring.webflux.base-path`); external traffic reaches it through
`api-gateway-service`.

| Method | Path | Description |
|---|---|---|
| `POST` | `/home/device/configuration/eaton` | Register an Eaton device configuration (`EatonDeviceConfiguration`: point, room, type, gateway). Returns `201 Created`; a configuration already registered for that point + gateway returns `400 Bad Request`. |
| `GET` | `/home/device/configuration/eaton?point=<n>&gateway=<name>` | Look up the configuration for a data point on a gateway. Returns `200 OK` with `EatonConfigurationResponse`; `404 Not Found` when no such configuration exists. |

Errors are rendered through `cholewa-commons`' `GlobalErrorExceptionHandler`, so failures
come back in the shared `Errors` JSON contract.

# Database

- **Access:** reactive, via `r2dbc-postgresql` and Spring Data R2DBC repositories.
- **Migrations:** Flyway (JDBC driver) from `src/main/resources/db/migration`.
- **Schema:** table `eaton_devices` (`point`, `room`, `type`, `gateway` + audit timestamps).
  The original `device_configuration` table was superseded by it and dropped in `V6`.
- **Constraints** (`V7`): `(point, gateway)` is unique — it is the natural key of an Eaton
  device. Every gateway numbers its own devices, so the same `point` on a *different*
  gateway is perfectly valid and stays allowed; only the exact pair cannot repeat. `point`
  is additionally checked to be within `1..99`, the range an Eaton gateway addresses.
  A violation of either surfaces as `400`, not `500` — see the API table.
