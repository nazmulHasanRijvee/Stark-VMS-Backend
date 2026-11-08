# Stark VMS Backend

Separate Spring Boot backend for the Stark VMS Flutter Web application.

## Stack and package

- Java 21
- Spring Boot 4.1.2
- Maven Wrapper 3.3.4, configured to use Maven 3.10.0
- Base package: `com.stark.vms`
- PostgreSQL with Flyway versioned migrations
- Hibernate validates the migrated schema; it does not update production or local schemas

## Local configuration

Keep local PostgreSQL settings in the ignored file `config/application-local.properties`, outside `src/main/resources`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/stark_vms
spring.datasource.username=your_local_postgres_user
spring.datasource.password=your_local_password
```

Activate the `local` profile in IntelliJ. For PowerShell, set the environment variables below and run `./mvnw.cmd spring-boot:run`. Spring Boot reads `config/application-local.properties` when the local profile is active.

The app now requires a strong JWT signing secret at startup. Generate 32 random bytes once, Base64-encode them, and store the result as `VMS_JWT_SECRET` in the IntelliJ run configuration or your process environment. Keep the same value between restarts so existing local tokens remain valid. The default access-token lifetime is 24 hours; override it with `VMS_JWT_ACCESS_TOKEN_LIFETIME`, using a Spring duration such as `PT24H`.

For example, generate a value in PowerShell with:

```powershell
$jwtKeyBytes = New-Object byte[] 32
$jwtKeyGenerator = [Security.Cryptography.RandomNumberGenerator]::Create()
$jwtKeyGenerator.GetBytes($jwtKeyBytes)
$env:VMS_JWT_SECRET = [Convert]::ToBase64String($jwtKeyBytes)
$jwtKeyGenerator.Dispose()
```

Set the `SPRING_PROFILES_ACTIVE` variable to `local`, then run:

```powershell
./mvnw.cmd spring-boot:run
```

Spring Boot does not load `.env` files by itself. `.env.example` contains variable names and placeholders only; use process environment variables or a tool that explicitly loads `.env`. Never commit database passwords, JWT keys, or demo passwords.

## Schema and migrations

Flyway migrations create `app_users` and `vehicles`, including generated numeric IDs, uniqueness constraints, required fields, canonical email/vehicle-number checks, allowed user roles, and the three allowed vehicle status values. Applied migrations are kept immutable; new schema changes use a new versioned migration. Database uniqueness races are translated to domain conflict exceptions.

`spring.jpa.hibernate.ddl-auto=validate` checks that the migrated schema matches the entities. `spring.jpa.open-in-view` and SQL logging are disabled. The only exposed Actuator endpoint is `/actuator/health`, with health details hidden.

## Domain and business logic

- `auth`: `AppUser` / `app_users`, `AppUserRepository`, registration and login DTOs, `AuthService`, BCrypt password hashing, and Nimbus-based signed access tokens.
- `vehicle`: `Vehicle` / `vehicles`, `VehicleRepository`, request and response DTOs, status values, and `VehicleService` create/read/update/delete operations.
- `common.error`: domain validation, conflict, not-found, invalid-credential, and invalid-token exceptions.
- `config`: password encoder, UTC clock, and explicitly enabled demo seeder.

Emails are trimmed and lowercased with `Locale.ROOT` before lookup and storage. Vehicle numbers are trimmed and uppercased with `Locale.ROOT` for create, update, lookup, and duplicate checks. A duplicate race is handled by the unique database constraint and translated to the same domain exception as a pre-check duplicate.

Registration accepts full name, email, and password, assigns `ROLE_USER`, hashes the password with BCrypt, and returns `UserResponse` without issuing a token. Login accepts `username` containing the email, verifies the password, and returns one signed JWT access token. The token includes user ID as subject, normalized email, role metadata, issue time, and expiration. `ROLE_ADMIN` and `ROLE_USER` are identity metadata only; they do not change vehicle permissions. The HTTP API uses stateless bearer authentication and has no refresh-token flow.

## Demo data

Demo rows are disabled by default. Enable them only in a local/demo environment with profile `demo` or `APP_SEED_DEMO_DATA=true`; set `VMS_DEMO_PASSWORD` to provide the seeded admin password. The default `demo` profile sets the seed property, and the seeder is excluded when `production` or `prod` is active. It creates the `admin@stark.com` classroom account and the four reference vehicles only when those rows are missing. An existing account's password is never reset.

To combine the local database settings with demo data, activate `local,demo` in IntelliJ. Do not enable demo seeding in production.

## Tests and build

Run the tests and package the executable JAR with:

```powershell
./mvnw.cmd package
```

Tests use the `test` profile and an in-memory H2 database. Flyway applies all versioned migrations there; the tests do not need a developer's local PostgreSQL credentials or JWT secret.

## REST API

The API base path is `/api/v1`. All successful responses use `{ "success": true, "message": "...", "data": ... }`. Errors use `{ "success": false, "message": "...", "timestamp": "...", "fieldErrors": { ... } }`; `fieldErrors` is omitted when there are no field validation errors. Unexpected errors return a generic message and a correlation ID is logged for diagnosis. Responses do not expose persistence entities, password hashes, JWTs in errors, SQL details, or stack traces.

| Method | Path | Access | Result |
|---|---|---|---|
| POST | `/api/v1/auth/register` | Public | 201; safe user only, no token |
| POST | `/api/v1/auth/login` | Public | 200; access token and safe user inside `data` |
| GET | `/api/v1/vehicles` | Bearer token | Search/filter page inside `data` |
| GET | `/api/v1/vehicles/{vehicleNumber}` | Bearer token | Vehicle inside `data` |
| POST | `/api/v1/vehicles` | Bearer token | 201; created vehicle inside `data` |
| PUT | `/api/v1/vehicles/{vehicleNumber}` | Bearer token | 200; updated vehicle inside `data` |
| DELETE | `/api/v1/vehicles/{vehicleNumber}` | Bearer token | 200; success envelope |
| GET | `/api/v1/dashboard/stats` | Bearer token | Database-backed counts inside `data` |

Vehicle list query parameters are `search`, `status`, `page` (default 0), and `size` (default 10, maximum 100). Search checks vehicle number, company name, brand, and model without case sensitivity. Status is an exact value: `Active`, `In Service`, or `Inactive`. Page data contains `content`, `page`, `size`, `totalElements`, and `totalPages`; ordering is `updatedAt` descending then `id` ascending. Vehicle responses include `id`, `createdAt`, and `updatedAt`; requests accept only `vehicleNumber`, `companyName`, `vehicleType`, `brand`, `model`, and `status`.

All vehicle and dashboard routes require `Authorization: Bearer <accessToken>`. Tokens are signed with `VMS_JWT_SECRET` and expire according to `VMS_JWT_ACCESS_TOKEN_LIFETIME` (default `PT24H`; `PT60H` means 60 hours). The API is stateless: it has no cookies, server sessions, or refresh tokens. Any valid authenticated account can access the vehicle and dashboard routes; role values are informational. Registration returns 201 with the created user only; registration does not automatically log the user in.

`/actuator/health` remains the only exposed Actuator endpoint and is permitted without authentication. Health details are hidden. CORS permits `GET`, `POST`, `PUT`, `DELETE`, and `OPTIONS`, with `Content-Type` and `Authorization` headers. Local defaults allow `http://localhost` and `http://127.0.0.1` on any port, without credentialed cookies. The only accepted wildcard patterns are those two local loopback origins. For Render, set `VMS_CORS_ALLOWED_ORIGINS` to the exact Netlify frontend origin, such as `https://your-site.netlify.app`; do not use a wildcard for a deployed origin.

## Flutter integration handoff

- Set `BASE_URL` to the backend origin plus `/api/v1`; ensure endpoint paths are joined without discarding that base path.
- Send the login form's `username` value as the backend `username` field, where it contains the email address.
- Unwrap the top-level `data` field before reading `accessToken`, `user`, vehicle, or page fields.
- Save only `accessToken` for the current session; send `Authorization: Bearer <accessToken>` on protected calls and clear the session on 401.
- Map API `companyName` to Flutter `company` and API `vehicleType` to Flutter `type` in the DTO/adapter.
- For a successful registration, show confirmation and route to login; there is no automatic login or email verification in this contract.
- Encode `vehicleNumber` as one URL path segment for read/update/delete.

For local Flutter Web development, the default CORS list supports any HTTP port on `localhost` or `127.0.0.1`, including ports 5000 and 6000. For deployment, configure the exact Netlify origin in the Render backend environment using `VMS_CORS_ALLOWED_ORIGINS`. Keep local wildcard patterns out of deployed configuration.

## Part 2 verification

Part 2 verification previously passed with 21 tests, including H2/Flyway schema validation, normalization and duplicate checks, BCrypt, JWT expiry/signature and the configured 60-hour lifetime, demo idempotency, and production seed exclusion. The app also started against the local PostgreSQL `stark_vms` database with the IntelliJ JWT settings; Flyway was up to date, Hibernate validated the schema, and `/actuator/health` reported `UP`. Demo seeding was disabled during that check.

For the current API implementation, run `./mvnw.cmd package`. Integration tests use the `test` profile and H2, exercise auth, validation, bearer protection, vehicle CRUD/search/paging, dashboard counts, and CORS, and do not require local PostgreSQL credentials. The latest `./mvnw.cmd package` completed successfully: 30 tests passed with no failures or errors, and the executable JAR was built. A smoke run of that JAR with the `local` profile connected to PostgreSQL and `/actuator/health` returned `UP`; demo seeding was disabled. The smoke run used a temporary JWT key and port and stopped its process afterward.
