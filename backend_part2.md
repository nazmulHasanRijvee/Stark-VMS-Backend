---
title: Stark VMS Backend — Assignment 2
tags:
  - stark-vms
  - spring-boot
  - backend
  - assignment-2
---

# Stark VMS — Backend Assignment 2 Implementation Brief

> Give this brief to the coding agent working in `C:\stark_vms\vms_backend`. It extends the existing Spring Boot/PostgreSQL API for the listed Assignment 2 features. Audit the current code first and implement only missing or incomplete behavior; preserve existing working features.

## 1. Assignment and architecture context

Stark VMS is a Flutter Web client backed by Java Spring Boot, PostgreSQL, and REST APIs. The backend owns validation, persistence, business rules, authentication, and authorization. Keep the current layered architecture (controller, service, repository, entity, DTO, validation, exception handling), API response envelope, `/api/v1` base path, Flyway migrations, and JWT access-token authentication.

Use the current backend implementation and current Assignment 2 requirements as sources of truth. Older Assignment 1 documents such as `README.md`, `feature_overview.md`, `build_logic.md`, and `expose_api.md` may describe `In Service` and say that documents or maintenance are out of scope. Their old scope is superseded here. Keep their still-valid setup and architecture conventions. Inspect the current Flutter project/API use before changing a wire format or field name. Preserve existing `companyName`, `vehicleType`, and other request fields unless the current client and backend contract have already changed together.

This brief covers the features listed below. Driver CRUD, driver–vehicle assignment, user/role administration, and unrelated fleet functions are outside this backend task; do not add them here.

## 2. Existing backend functionality: preserve and extend

The code audit found these working foundations:

- Authentication and stateless JWT access tokens; no refresh-token flow.
- Vehicle CRUD and unique vehicle-number enforcement.
- Server-side search over vehicle number, company, brand, and model.
- Server-side status filtering and page/size pagination, with deterministic default ordering by `updatedAt` descending and `id` ascending.
- A dashboard endpoint with total, active, `In Service`, and inactive vehicle counts.
- Flyway migrations and integration tests.

Do not rebuild these features. Extend them as specified below:

- Replace `In Service` with `Maintenance` throughout the vehicle domain, API, database constraint/data, dashboard, seed data, and tests.
- Expand vehicle search to relevant existing vehicle fields and make vehicle-number matches rank first by default.
- Add client-selectable server-side sorting while retaining safe defaults and current pagination semantics.
- Extend dashboard statistics with maintenance and document-expiry metrics; retain correct totals over the full database, not the current vehicle page.
- Add document upload/metadata/history and maintenance-record creation/history.

The current `VehicleStatus` Java enum uses the constants `ACTIVE`, `IN_SERVICE`, and `INACTIVE`, while API and database values are title-cased display labels (`Active`, `In Service`, `Inactive`). Keep API values compatible with the current Flutter client: `Active`, `Inactive`, and `Maintenance`. Use clear uppercase Java enum constants internally, and do not accept or return `In Service` after migration. If the Flutter code has already migrated its wire format, coordinate a single consistent contract rather than supporting an undocumented mix.

## 3. Vehicle status migration

Use exactly three domain states: `ACTIVE`, `INACTIVE`, and `MAINTENANCE` (API labels `Active`, `Inactive`, and `Maintenance`). Keep vehicle status independently editable; recording maintenance history must not change it.

Add a new forward-only Flyway migration. Do not edit or rewrite existing migrations:

1. Drop the current `ck_vehicles_status` constraint.
2. Migrate every existing `In Service` row to `Maintenance` without changing its vehicle data.
3. Recreate the constraint to permit only `Active`, `Inactive`, and `Maintenance`.
4. Update enum conversion, request validation, seed data, fixtures, and dashboard count names to the new states.

Reject unsupported status values with the existing consistent validation error response. Verify that existing rows survive the migration and that create/update/filter/dashboard paths all use the same three states.

## 4. Vehicle documents and Supabase Storage

### Supported document types and metadata

Support these document types: `Registration`, `Tax Token`, `Insurance`, `Fitness Certificate`, and `Other`.

Persist document metadata in PostgreSQL, linked to a vehicle. Include at minimum:

- Generated document ID and vehicle foreign key.
- Document type.
- Original display filename (sanitized; never use it as a storage path).
- Unique storage object key.
- Verified content type and file size in bytes.
- Optional expiry date (`LocalDate`).
- Upload timestamp (`Instant`).

Every renewal is a new version. Keep older metadata and files in history; never overwrite an existing object or delete the prior version during renewal. The latest version for a vehicle/document type is the greatest upload timestamp (with a deterministic ID tie-breaker). Return the full history in newest-first order and make the latest version easy for the frontend to identify.

### Upload and access security

The Supabase project and bucket have already been configured. Use the configured bucket and credentials supplied through environment-based Spring configuration; inspect the deployment configuration rather than guessing the bucket name. If a required setting is missing, add its environment-variable name to `.env.example` and document it in `README.md`, but never commit credential values.

- Treat the bucket as private unless the existing Supabase configuration explicitly establishes a safer documented alternative.
- The Supabase service-role key, or any privileged storage credential, must exist only in the backend environment. Never return it, log it, put it in source control, or expose it to Flutter.
- Generate object keys server-side, for example under a vehicle/document UUID hierarchy. Do not accept arbitrary bucket names or object keys from the client.
- Require the existing bearer JWT for upload, listing, and download routes. Verify the requested document belongs to the vehicle in the route to prevent cross-vehicle access.
- Provide downloads through an authenticated backend route that either streams the object or returns a short-lived signed URL. Never expose a permanent public object URL.
- If storage upload succeeds but database persistence fails, attempt to delete the just-uploaded object and report a consistent service error. If storage fails, do not create document metadata. Log operational detail safely without leaking credentials or file contents.
- When deleting a vehicle, preserve the existing vehicle-delete contract while ensuring related document metadata and cloud objects are handled consistently; do not silently orphan stored files.

Use the Assignment 2 UI's file limits: PDF, JPEG, or PNG, maximum 10 MB. Enforce the limit on the server, validate allowed content type and file signature where practical, sanitize the displayed filename, and reject unsupported/empty files with a clear 400 response. Do not trust client-supplied MIME type or size alone.

### Expiry rules

Expiry date is optional. Derive the expiry state from the date at read time; do not store a state that can become stale:

- `EXPIRED`: expiry date is before today.
- `EXPIRING_SOON`: expiry date is today through today plus 30 days, inclusive.
- `VALID`: expiry date is after today plus 30 days.
- `NO_EXPIRY_DATE`: expiry date is null.

Use an injectable `Clock` and the configured business time zone (default `Asia/Dhaka`) so boundary behavior is consistent and testable. On list/search endpoints, allow filters only where useful to the existing client, such as document type and expiry state. Validate enum/filter inputs and keep queries paginated.

### Suggested REST contract

Use authenticated routes under the current `/api/v1` prefix and keep the existing response envelope. Add equivalent routes only if they better match established project conventions; document the final contract in `README.md`:

- `POST /vehicles/{vehicleNumber}/documents` — `multipart/form-data` with `file`, `documentType`, and optional `expiryDate` (`YYYY-MM-DD`); return created metadata with 201.
- `GET /vehicles/{vehicleNumber}/documents` — list document versions, newest first; support page/size and optional type/expiry filters if needed.
- `GET /vehicles/{vehicleNumber}/documents/{documentId}/download` — authenticated short-lived download access.

Retain the project’s response envelope for JSON metadata responses. Multipart errors must use the same standard error shape/status mapping. Do not return storage keys as clickable public URLs.

## 5. Maintenance records and history

Add a maintenance entity linked to a vehicle, plus repository, request/response DTOs, service, controller, validation, and Flyway migration. Persist at minimum:

- Maintenance date (`LocalDate`, required).
- Maintenance type (required text; do not invent a fixed type catalogue unless the current contract already defines one).
- Cost in Bangladeshi Taka (`BigDecimal`, non-negative; use a fixed precision such as `NUMERIC(12,2)`).
- Workshop/vendor (required text).
- Notes (optional text).
- Created timestamp and generated record ID.

Expose authenticated endpoints to add a record and view a vehicle's history, for example:

- `POST /vehicles/{vehicleNumber}/maintenance` — create one record, return 201.
- `GET /vehicles/{vehicleNumber}/maintenance` — paginated history, newest maintenance date first, stable ID tie-breaker.

Validate that the vehicle exists and map missing vehicles to the existing 404 format. Keep all business rules in the service layer. Do not automatically change `Vehicle.status` when a maintenance record is added. Do not add maintenance scheduling, work orders, parts, invoices, fuel, or unrelated finance features. Edit/delete endpoints are not required unless the current frontend/API contract already needs them.

## 6. Vehicle search, filters, sorting, and pagination

Preserve the existing server-side endpoint and its current zero-based `page`, `size`, status filter, maximum page size, and response metadata. Do not load every vehicle into memory to perform filtering or sorting.

Extend the existing query behavior:

- Search case-insensitively across vehicle number, company name, vehicle type, brand, and model. Escape SQL wildcard characters as the current implementation does.
- When search is present and no explicit sort is requested, rank exact vehicle-number matches first, then vehicle-number prefix matches, then other vehicle-number matches, then matches in the other fields. Keep results deterministic with a stable tie-breaker.
- Keep vehicle status filtering for `Active`, `Inactive`, and `Maintenance`; reject `In Service` and unsupported values.
- Add an optional allow-listed sort field and direction (such as `vehicleNumber`, `companyName`, `vehicleType`, `brand`, `model`, `status`, or `updatedAt`). Reject unsupported sort values; never concatenate untrusted sort input into SQL.
- Keep a stable secondary sort (for example, ID) for all sort orders. Retain the current `updatedAt` descending, ID ascending default when no search/sort changes the order.
- If the current Assignment 2 client requires a document-health filter, implement it as a query against current/latest document versions and document the exact semantics. Do not add driver-based search until driver management is separately in scope.

Keep the existing `VehiclePageResponse` shape unless a coordinated client change requires a documented addition. Preserve total element and page counts for filtered results.

## 7. Dashboard and summary statistics

Extend the existing authenticated `GET /api/v1/dashboard/stats` endpoint. Do not replace it with a second dashboard endpoint unless the existing API design requires a separate summary route. Compute aggregates from database queries/projections, never from one page of vehicle results.

Return, at minimum:

- Vehicle counts: total, active, inactive, and in maintenance. Remove the old `inServiceVehicles` response field.
- Document counts: expired and expiring soon, based on the expiry rules above.
- A bounded list of documents expiring soon (and optionally expired items if the UI needs an attention list), with vehicle number, document type, expiry date, and document ID.
- Current calendar-month maintenance record count and total cost.
- A short recent-maintenance list with record date, vehicle number, type, vendor/workshop, and cost.

Define “this month” in the configured business time zone, default `Asia/Dhaka`; use the first day of the current month inclusive and the first day of the next month exclusive. Do not use hard-coded demonstration dates. Keep dashboard queries bounded and avoid N+1 loading. Continue returning the common success/error envelope.

## 8. Persistence, API errors, and compatibility

- Add new Flyway migrations for document and maintenance tables and status changes. Never edit already-applied migrations.
- Use foreign keys and indexes for vehicle joins, document type/upload time/expiry, and maintenance date. Match the project’s existing generated ID and timestamp conventions.
- Keep JPA entities internal; expose DTOs only.
- Reuse `ApiResponse`, `ApiErrorResponse`, and `ApiExceptionHandler`. Use 400 for validation, 401 for missing/invalid authentication, 404 for missing vehicle/document, 409 for conflicts, and an appropriate 5xx status for Supabase outages.
- Preserve vehicle CRUD and duplicate vehicle-number behavior. Update the demo seeder, all fixtures, README examples, and tests so no `In Service` status remains in live API behavior.
- Do not log credentials, JWTs, signed URLs, document bytes, or sensitive metadata.

## 9. Implementation sequence

1. Inspect current entities, migrations, controllers, DTOs, security, dashboard queries, tests, deployed configuration, and the Flutter API contract. Record which requirements are already complete and any contract mismatches.
2. Implement the forward-only vehicle-status migration and replace `In Service` with `Maintenance` consistently.
3. Implement document metadata, Supabase storage adapter/configuration, secure upload/list/download APIs, history, and expiry calculations.
4. Implement maintenance persistence, create/history APIs, and validations.
5. Extend vehicle search ranking, filters, safe sorting, and pagination only where missing.
6. Extend the existing dashboard summary with vehicle, document, and maintenance statistics.
7. Update API documentation/README and add focused service, migration, storage-mocked, and API integration tests. Run the backend test suite and build; fix failures. Do not require real Supabase credentials for tests.

If Supabase configuration, bucket access, a required storage detail, or the current frontend contract is genuinely missing, finish independent backend work and report the precise missing value/decision rather than inventing it. Keep all secrets out of source control.

## 10. Completion checklist

- Existing authentication, vehicle CRUD, duplicate validation, and pagination remain intact.
- Only Active, Inactive, and Maintenance are valid vehicle states, and existing `In Service` records migrate to Maintenance.
- Search covers the agreed vehicle fields, prioritizes vehicle-number matches by default, and sorting is server-side and allow-listed.
- Vehicle documents upload to the configured Supabase bucket; metadata and expiry are in PostgreSQL; renewals keep previous versions; downloads require authenticated short-lived access.
- Invalid file types and files above 10 MB are rejected server-side; storage failures do not leave inconsistent database metadata.
- Expiry states follow the 30-day rule and boundary behavior is covered.
- Maintenance records can be added and listed by vehicle; adding one never changes the vehicle status.
- Dashboard metrics use whole-database aggregates and correctly report status counts, expiry attention, and this-month maintenance.
- New schema is introduced only through forward Flyway migrations; no secrets are committed.
- API documentation and tests reflect the final contract; backend tests/build pass.

## 11. Explicitly out of scope

Do not implement driver CRUD or driver–vehicle assignments in this task, even though they are part of the broader Assignment 2. Also exclude role administration, refresh tokens, email verification, settings/user-management screens, dispatch/trips, GPS/telematics, fuel, maintenance scheduling, notifications, and unrelated fleet features.
