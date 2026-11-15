# Stark VMS Backend

Spring Boot REST API for the Stark Vehicle Management System. It connects the Flutter Web client to PostgreSQL and implements authentication, vehicle management, document history, and maintenance records.

- **Stack:** Java 21, Spring Boot, Spring Security, Spring Data JPA, PostgreSQL, Flyway, Nimbus JWT, Maven, Docker.
- **Deployment:** Docker Web Service on Render with managed PostgreSQL; vehicle files are stored in a private Supabase Storage bucket.
- **Development workflow:** Codex-assisted backend development and MCP server setup for connected development tools.

## Features

- BCrypt password hashing and stateless JWT access-token authentication.
- Database-backed vehicle CRUD, search, status filters, allow-listed sorting, pagination, and dashboard metrics.
- Vehicle numbers are trimmed and normalized; application checks and a database unique constraint prevent duplicates, including concurrent requests.
- Vehicle states are `Active`, `Inactive`, and `Maintenance`. Adding a maintenance record does not change vehicle state.
- Private vehicle document uploads, sanitized filenames, signature-verified PDF/JPEG/PNG files (maximum 10 MB), optional expiry dates, retained renewal history, and authenticated short-lived download URLs.
- Maintenance history with date, type, non-negative BDT cost, workshop, and notes.
- Idempotent demo seeding adds only missing records; it is disabled by default and excluded from production.
- `ROLE_USER` and `ROLE_ADMIN` are represented in accounts and tokens for future authorization rules. Both currently have the same vehicle API access.

## Run locally

### Prerequisites

- JDK 21
- PostgreSQL and a database such as `stark_vms`

Set these values in your terminal or IDE run configuration. `VMS_JWT_SECRET` must be Base64-encoded random key material containing at least 32 bytes. Spring Boot does not load `.env` files automatically; `.env.example` lists the supported settings.

```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/stark_vms"
$env:SPRING_DATASOURCE_USERNAME = "your_postgres_user"
$env:SPRING_DATASOURCE_PASSWORD = "your_postgres_password"
$env:VMS_JWT_SECRET = "<base64-encoded-random-secret>"
$env:VMS_JWT_ACCESS_TOKEN_LIFETIME = "PT24H" # Set PT60H for a 60-hour token
$env:VMS_CORS_ALLOWED_ORIGINS = "http://localhost:*,http://127.0.0.1:*"
./mvnw.cmd spring-boot:run
```

The API starts at `http://localhost:8080`. Flyway applies migrations automatically; Hibernate validates the schema. Health check: `/actuator/health`. Run tests and package with `./mvnw.cmd verify`.

## Render and Supabase configuration

Create a Render PostgreSQL database and a Docker Web Service from this repository using the root `Dockerfile`. Use the database's internal connection details and set:

| Variable | Value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `production` |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<internal-host>:5432/<database-name>` |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | Render database credentials |
| `VMS_JWT_SECRET` | New Base64 random key of at least 32 bytes |
| `VMS_JWT_ACCESS_TOKEN_LIFETIME` | For example, `PT60H` |
| `VMS_CORS_ALLOWED_ORIGINS` | Exact frontend origin, e.g. `https://your-site.netlify.app` |
| `APP_SEED_DEMO_DATA` | `false` |
| `SUPABASE_S3_ENDPOINT` | Full endpoint from Storage → Configuration → S3, e.g. `https://<project-ref>.storage.supabase.co/storage/v1/s3` |
| `SUPABASE_S3_REGION` | Region shown in the Supabase S3 configuration |
| `SUPABASE_S3_ACCESS_KEY_ID` | S3 access key ID; backend only |
| `SUPABASE_S3_SECRET_ACCESS_KEY` | S3 secret access key; backend only |
| `SUPABASE_STORAGE_BUCKET` | Existing bucket name from the Supabase project; keep the bucket private |
| `SUPABASE_SIGNED_URL_TTL_SECONDS` | Optional, default `300`, maximum `3600` |
| `VMS_BUSINESS_TIME_ZONE` | Optional, default `Asia/Dhaka` |

Get the endpoint, region, S3 access key ID, and secret access key from Supabase Storage → Configuration → S3. Keep both keys on the backend in Render; S3 access keys have full Storage access and bypass RLS. Set the configured bucket to private in Supabase. The app does not start with storage credentials required, but document operations return `503` until the S3 endpoint, region, both keys, and bucket are supplied. Render supplies `PORT`; set the health check path to `/actuator/health`.

## API

Base path: `/api/v1`. Auth and vehicle routes retain the common `{ success, message, data }` response envelope. All vehicle, document, maintenance, and dashboard routes require `Authorization: Bearer <accessToken>`; register and login are public.

| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/auth/register` | Create a `ROLE_USER` account |
| `POST` | `/auth/login` | Sign in and receive an access token |
| `GET`, `POST` | `/vehicles` | Search/list or register a vehicle |
| `GET`, `PUT`, `DELETE` | `/vehicles/{vehicleNumber}` | Read, edit, or remove a vehicle |
| `GET` | `/dashboard/stats` | Fleet, latest-document expiry, and monthly maintenance summary |
| `POST` | `/vehicles/{vehicleNumber}/documents` | Multipart upload: `file`, `documentType`, optional `expiryDate` (`YYYY-MM-DD`) |
| `GET` | `/vehicles/{vehicleNumber}/documents` | Paged upload history; optional `documentType` and `expiryState` filters |
| `GET` | `/vehicles/{vehicleNumber}/documents/{documentId}/download` | Create a short-lived signed download URL |
| `POST` | `/vehicles/{vehicleNumber}/maintenance` | Add a maintenance record |
| `GET` | `/vehicles/{vehicleNumber}/maintenance` | Paged maintenance history |

Vehicle listing supports `search`, `status`, `page` (zero-based), `size` (maximum 100), `sortBy`, and `sortDirection` (`asc` or `desc`). Search covers vehicle number, company, type, brand, and model; without an explicit sort, vehicle-number matches rank first. Sort fields are `vehicleNumber`, `companyName`, `vehicleType`, `brand`, `model`, `status`, and `updatedAt`; the stable default is `updatedAt` descending then ID ascending.

Document types are `Registration`, `Tax Token`, `Insurance`, `Fitness Certificate`, and `Other`. Expiry states are derived using the configured business date: expired before today, expiring soon from today through day 30 inclusive, valid after day 30, or no expiry date. Renewals create new rows and objects; every history item includes a `latest` flag. Download URLs are short-lived and only issued after the authenticated backend confirms the document belongs to the requested vehicle.

Maintenance JSON fields are `maintenanceDate`, `maintenanceType`, `cost`, `workshop`, and optional `notes`. Dashboard expiry counts and alerts use only each vehicle/document type's latest version. Monthly maintenance count and cost use the current calendar month in the configured business time zone, and recent maintenance/expiry lists are bounded.

## Database and safety

Flyway migrations are forward-only. Assignment 2 migration converts existing `In Service` rows to `Maintenance` and adds document and maintenance tables. Vehicle document objects are removed through the Storage API before vehicle deletion; metadata uses foreign keys and indexed history queries. CORS is configured in Spring Security for local loopback origins in development and an exact frontend origin in production. CORS is a browser-origin policy; bearer-token authorization protects the API independently.

Drivers, driver assignment, role administration, refresh tokens, and maintenance scheduling are outside this backend assignment.
