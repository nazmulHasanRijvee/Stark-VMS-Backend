# Stark VMS Backend

A REST API for the Stark Vehicle Management System. It connects the Flutter Web client to PostgreSQL and supports the complete vehicle workflow: sign in, view vehicles, register, edit, and remove records.

- **Deployment:** Render Docker Web Service with PostgreSQL.
- **Development workflow:** Codex-assisted backend development and MCP server setup for connected development tools.

## Features

- **Authentication:** Registration and login with BCrypt password hashing and signed, stateless JWT access tokens.
- **Vehicle management:** Database-backed CRUD, search, status filtering, pagination, and dashboard counts.
- **Duplicate protection:** Vehicle license numbers are trimmed and normalized before checks, with a PostgreSQL unique constraint as a final safeguard. Duplicates return a conflict response.
- **Idempotent demo seeding:** Re-running the optional demo seeder adds only missing sample rows and does not reset an existing admin password. Seeding is off by default and excluded in production.
- **Spring Security and CORS:** Protected API routes require a bearer token. CORS allows local Flutter development origins and can be limited to the exact deployed frontend origin in Render.
- **Role groundwork:** `ROLE_USER` and `ROLE_ADMIN` are represented in accounts and tokens for future admin permissions. Currently both roles have the same vehicle access.
- **Database migrations:** Flyway manages schema changes; Hibernate validates the migrated schema at startup.

## Technology

Java 21 | Spring Boot | Spring MVC and Security | Spring Data JPA | PostgreSQL | Flyway | JWT (Nimbus) | Maven | Docker | Render

## Run locally

### Prerequisites

- JDK 21
- PostgreSQL, with a database such as `stark_vms` and a user that can access it

Set the connection and JWT values in your terminal or IDE run configuration. `VMS_JWT_SECRET` must be Base64-encoded random key material containing at least 32 bytes. Spring Boot does not load `.env` files automatically; `.env.example` is a variable-name reference.

PowerShell example:

```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/stark_vms"
$env:SPRING_DATASOURCE_USERNAME = "your_postgres_user"
$env:SPRING_DATASOURCE_PASSWORD = "your_postgres_password"

$jwtBytes = New-Object byte[] 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($jwtBytes)
$env:VMS_JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
$rng.Dispose()

$env:VMS_JWT_ACCESS_TOKEN_LIFETIME = "PT24H" # Optional; use PT60H for 60 hours
$env:VMS_CORS_ALLOWED_ORIGINS = "http://localhost:*,http://127.0.0.1:*"
./mvnw.cmd spring-boot:run
```

The API starts on `http://localhost:8080`. Flyway applies migrations on startup. Health check: `http://localhost:8080/actuator/health`. To run the automated checks, use `./mvnw.cmd test`.

## Render configuration

Create a Render PostgreSQL database and a Docker Web Service from this repository using the root `Dockerfile`. Keep both services in the same region and use the database's **internal** connection details. Set these environment variables on the web service:

| Variable | Value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `production` |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<internal-host>:5432/<database-name>` |
| `SPRING_DATASOURCE_USERNAME` | Render database username |
| `SPRING_DATASOURCE_PASSWORD` | Render database password |
| `VMS_JWT_SECRET` | New Base64-encoded random key (at least 32 bytes); keep it secret |
| `VMS_JWT_ACCESS_TOKEN_LIFETIME` | `PT60H` for a 60-hour access token, or your chosen duration |
| `VMS_CORS_ALLOWED_ORIGINS` | Exact frontend origin, e.g. `https://your-site.netlify.app` |
| `APP_SEED_DEMO_DATA` | `false` |

Render supplies the service port. The app binds to `0.0.0.0` and uses `PORT` when provided. Set the Render health check path to `/actuator/health`. Do not put database credentials or JWT secrets in source control.

## API overview

Base path: `/api/v1`. Successful and error responses use a consistent JSON envelope.

| Method | Endpoint | Access |
|---|---|---|
| `POST` | `/auth/register` | Public |
| `POST` | `/auth/login` | Public; returns a JWT access token |
| `GET` | `/vehicles` | Bearer token |
| `GET` | `/vehicles/{vehicleNumber}` | Bearer token |
| `POST` | `/vehicles` | Bearer token |
| `PUT` | `/vehicles/{vehicleNumber}` | Bearer token |
| `DELETE` | `/vehicles/{vehicleNumber}` | Bearer token |
| `GET` | `/dashboard/stats` | Bearer token |

For protected routes, send `Authorization: Bearer <accessToken>`. CORS controls which browser origins may call the API; configure the exact frontend origin in Render. Local development defaults allow `localhost` and `127.0.0.1` on any port. API authentication is enforced separately by Spring Security.

## Project requirements covered

The backend provides login, vehicle list and CRUD operations, Flutter Web REST integration, and persistent PostgreSQL data in place of mock vehicle records. It also validates duplicate vehicle numbers and includes migration, authentication, and deployment configuration for the application workflow.
