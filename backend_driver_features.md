---
title: Stark VMS Backend — Driver Management and Assignment
tags:
  - stark-vms
  - spring-boot
  - drivers
  - assignment-2
---

# Stark VMS — Backend Driver Features

> Backend implementation brief for the coding agent working in `C:\stark_vms\vms_backend`. Implement Driver CRUD and current Driver–Vehicle assignment so they fit the existing API and database. Audit first, preserve the completed document and maintenance features, and do not duplicate existing work.

## 1. Current project state and integration contract

The backend is a Spring Boot REST API using PostgreSQL, Flyway, Spring Security, JWT access tokens, and the existing layered structure. Its current base path is `/api/v1`; JSON endpoints use the standard `{ "success", "message", "data" }` response envelope. Protected API routes require `Authorization: Bearer <accessToken>`.

The backend already has working vehicle CRUD, search, status filtering, allow-listed sorting, pagination, Supabase-backed vehicle document upload/history/download, expiry tracking, maintenance create/history, the `Active` / `Inactive` / `Maintenance` vehicle statuses, and dashboard summaries. **Do not rebuild or alter these features.** Add only driver management and driver assignment, plus the smallest compatible vehicle response additions needed to expose the assignment.

The current backend README says that drivers and assignments are out of scope. That statement is superseded by this brief; update the README/API table and remove the outdated sentence when the implementation is complete. Keep the valid architecture, security, configuration, migration, and API conventions from the existing project.

The Flutter project currently has only the Assignment 1 vehicle/auth API client: `Vehicle` sends `vehicleNumber`, `companyName`, `vehicleType`, `brand`, `model`, and title-cased status labels. It has no driver API/models yet. Keep those vehicle request fields and existing CRUD routes unchanged. Add assignment data only as an optional response field; do not make normal vehicle create/update implicitly assign or clear a driver. This task changes the backend only; document the exact endpoints and DTOs so a later Flutter task can wire them without guessing.

The accepted Assignment 2 design requires:

- Driver fields: name, phone, license number, license expiry, and Active/Inactive status.
- A driver list showing the currently assigned vehicle, if any.
- At most one current driver per vehicle and at most one current vehicle per driver.
- Assignment selection limited to Active drivers who are not assigned to another vehicle.
- A searchable assign/change flow from Vehicle Details.
- No assignment history or multi-vehicle driver behavior.

Do not add payroll, attendance, trip/route management, dispatch, driver roles, user administration, settings, or unrelated fleet features. The current assignment has no driver-specific roles or permissions: all authenticated users follow the existing shared-fleet security model.

## 2. Database design and migration

Add one new forward-only Flyway migration (expected next version `V4`). Do not edit V1, V2, or V3. Use the project's existing generated numeric IDs, timestamp style, naming, and PostgreSQL conventions.

Create a `drivers` table with:

- `id BIGINT` generated primary key.
- `name VARCHAR(120) NOT NULL`.
- `phone VARCHAR(32) NOT NULL` (preserve user-entered display punctuation; trim outer whitespace).
- `license_number VARCHAR(64) NOT NULL`, trimmed and normalized consistently (uppercase is compatible with vehicle-number normalization).
- A unique constraint/index on normalized license number.
- `license_expiry DATE NOT NULL`.
- `status VARCHAR(16) NOT NULL`, constrained to `Active` or `Inactive` to match existing API display values.
- `created_at` and `updated_at` timestamps matching the existing backend conventions.

Model only the current assignment; do not create an assignment-history table. Add nullable `current_driver_id BIGINT` to `vehicles`, referencing `drivers.id`, with a **unique constraint** on `current_driver_id`. This column gives each vehicle zero or one current driver; the unique constraint prevents one driver from being attached to multiple vehicles. PostgreSQL permits multiple null values in a unique constraint, so unassigned vehicles remain valid. Use `ON DELETE RESTRICT` for the FK and enforce the clearer application rule that an assigned driver must first be unassigned before deletion.

Add appropriate indexes for driver search/listing and assignment lookups. Use database constraints as the final integrity guard, alongside service validation. Verify migration from a populated V3 database preserves vehicles, document metadata/storage relationships, maintenance history, and vehicle statuses.

## 3. Driver domain, validation, and DTOs

Follow the existing package/layer style. Add a `driver` feature package containing the entity, `DriverStatus`, repository, request/response DTOs, paged response, service, and controller. Keep JPA entities out of JSON responses.

Driver create/update payload:

```json
{
  "name": "Rafiqul Islam",
  "phone": "+880 1712-345678",
  "licenseNumber": "DHAKA-M-123456",
  "licenseExpiry": "2028-07-31",
  "status": "Active"
}
```

Use title-cased wire values `Active` and `Inactive`, consistent with the existing vehicle contract. Internally use enum constants such as `ACTIVE` and `INACTIVE`. Validate required fields, trim text, reject unreasonable lengths and invalid dates, and return the standard error envelope. Do not impose country-specific phone formatting beyond safe non-empty/length validation unless existing project validation already specifies it.

The license number must be unique after normalization. Translate a duplicate license number into HTTP `409 Conflict` with a clear domain message; enforce it in both the service and a database constraint. Phone numbers need not be unique. Keep `licenseExpiry` as a date-only value (`YYYY-MM-DD`). The license expiry is stored and returned; do not invent extra assignment rules based on the expiry date unless the current frontend/backend contract explicitly adds them.

Driver responses should include the driver's fields, generated ID, timestamps if the existing API convention exposes them, and the current `assignedVehicle` summary or `null`. Keep that summary small and non-recursive, for example:

```json
"assignedVehicle": {
  "vehicleNumber": "DHAKA METRO GA-12-3456",
  "brand": "Toyota",
  "model": "Corolla"
}
```

Add a `DriverPageResponse` matching the existing vehicle page envelope fields: `content`, `page`, `size`, `totalElements`, and `totalPages`.

## 4. Driver REST API

Add authenticated endpoints under `/api/v1/drivers`, using `ApiResponse` and `ApiExceptionHandler` conventions:

| Method | Endpoint | Behavior |
|---|---|---|
| `POST` | `/drivers` | Create driver; return 201 with the new driver (unassigned). |
| `GET` | `/drivers/{driverId}` | Get one driver, including its current vehicle summary or `null`. |
| `PUT` | `/drivers/{driverId}` | Update name, phone, license number, license expiry, or status. Do not change assignment through this route. |
| `DELETE` | `/drivers/{driverId}` | Delete only when unassigned; otherwise return 409 and require explicit unassignment first. |
| `GET` | `/drivers` | Search/list with optional `search`, `status`, `page`, `size`, and safe allow-listed sorting. |

The list search should be case-insensitive across name, phone, license number, and assigned vehicle number. Status filter accepts only `Active` / `Inactive`. Reuse the existing page validation limits and response conventions (zero-based page and current maximum page size). Use stable deterministic ordering. Allow-list sort fields such as `name`, `licenseExpiry`, `status`, and `createdAt`; reject invalid values instead of interpolating untrusted sort input into a query.

The driver list response must provide `assignedVehicle` (or an equivalent documented nullable summary) so the Drivers screen can show the current vehicle without making one request per row. Avoid N+1 queries by using a fetch join, entity graph, projection, or equivalent bounded query.

## 5. Driver–Vehicle assignment API and rules

Use the existing vehicle-number route identity and normalized lookup in `VehicleService`. Expose assignment separately from vehicle CRUD:

### Assign or change

`PUT /api/v1/vehicles/{vehicleNumber}/driver`

Request:

```json
{
  "driverId": 12
}
```

Behavior:

- Require an existing vehicle and existing driver.
- Allow assignment only to a driver whose status is `Active` and who is not assigned to a different vehicle.
- A driver already assigned to the same vehicle may be submitted again idempotently.
- If the vehicle already has a different driver, this operation changes the single current assignment atomically. The previous driver becomes unassigned.
- Reject an inactive driver or a driver assigned to another vehicle with HTTP 409 and a clear message. Return 404 for missing vehicle/driver.
- Return the updated vehicle representation, including its `assignedDriver` summary.

### Unassign

`DELETE /api/v1/vehicles/{vehicleNumber}/driver`

Clear the vehicle's current assignment and return the standard success envelope (HTTP 200 to match the existing delete API style). Make clearing an already-unassigned vehicle safe/idempotent. The driver becomes available for another vehicle. The UI can expose this as an explicit **Unassign Driver** action when needed.

No assignment history is stored. Do not add assignment audit/history tables, multi-driver relationships, or user/role rules.

For concurrent requests, lock the relevant vehicle/driver rows inside a transaction where appropriate and keep the unique database constraint as the final guard. Translate assignment uniqueness conflicts into 409 rather than an unexpected 500.

An assigned driver cannot be changed to `Inactive` or deleted until unassigned. Return a clear 409 and preserve the existing assignment; do not silently detach them. Vehicle deletion naturally removes its current assignment because the relationship lives on the vehicle row. Preserve the current vehicle delete flow that removes its Supabase document objects and metadata before deleting the vehicle.

## 6. Vehicle response compatibility

Extend the existing `VehicleResponse` additively with a nullable `assignedDriver` summary so the Vehicle List and Vehicle Details can display the current assignment:

```json
"assignedDriver": {
  "id": 12,
  "name": "Rafiqul Islam",
  "phone": "+880 1712-345678"
}
```

When no driver is assigned, return `"assignedDriver": null`. Include this field consistently from `GET /vehicles`, `GET /vehicles/{vehicleNumber}`, and the assignment response. Do not include an assignment field in `VehicleRequest`; `POST`/`PUT /vehicles` must not assign, unassign, or clear a driver. Do not rename existing vehicle fields or status labels.

The Flutter Part 1 model currently ignores unknown JSON fields, so the new response field is additive for existing vehicle screens. Document the new field for the later Flutter Driver/Assignment work. Keep the response DTO non-recursive and avoid serializing `Driver` or `Vehicle` JPA entities directly. Ensure vehicle pagination still avoids N+1 driver queries.

## 7. Error handling and security

- All new driver and assignment routes require the existing JWT bearer access token. Keep sign-up/login as the only public routes and do not add refresh-token behavior.
- All authenticated users have the same current access; do not introduce role-based authorization for drivers in this assignment.
- Reuse the standard response envelope for success and error responses.
- Use 400 for invalid fields/status/page/sort values, 401 for missing or invalid token, 404 for unknown driver/vehicle, and 409 for duplicate license numbers, ineligible assignments, assigned-driver deactivation/deletion, or assignment conflicts.
- Never return JPA entities, password/auth data, database implementation details, or a bidirectional object graph.
- Keep CORS and Supabase storage credential behavior unchanged.

## 8. Do not disturb completed document and maintenance work

The existing backend already includes:

- Private Supabase vehicle document storage and authenticated short-lived download access.
- Registration, Tax Token, Insurance, Fitness Certificate, and Other document types.
- PDF/JPEG/PNG validation up to 10 MB, expiry state/history, and retained renewal versions.
- Maintenance record creation and paginated vehicle history.
- Vehicle status migration to Active, Inactive, and Maintenance and dashboard status/expiry/maintenance summaries.

Do not edit the existing V1–V3 migrations, storage adapter, Supabase configuration, document routes/DTOs, maintenance routes/DTOs, or expiry calculations as part of driver work. Add only the new V4 driver/assignment migration and required additive `VehicleResponse` change. Keep existing document deletion on vehicle deletion intact. Existing document and maintenance API tests must continue to pass.

## 9. Implementation sequence and checks

1. Inspect the current backend packages, V1–V3 migrations, entities, security configuration, response/error types, README/API table, and Flutter's current vehicle JSON contract. Confirm that no driver domain or assignment route already exists before adding files.
2. Add V4 with the `drivers` table, unique normalized license number, nullable unique `vehicles.current_driver_id`, foreign key, and indexes. Do not change prior migrations.
3. Implement driver status, entity, request/response DTOs, repository queries, service validation, duplicate-license exception handling, and CRUD endpoints.
4. Implement transactional assign/change/unassign behavior and database conflict mapping.
5. Add nullable `assignedDriver` to vehicle responses and `assignedVehicle` to driver responses; keep vehicle request payloads untouched and avoid N+1 queries.
6. Update README/API documentation to remove its obsolete “drivers out of scope” statement and document routes, payloads, statuses, constraints, and error cases.
7. Add focused unit/integration/migration tests, then run the backend test suite and build. Do not require live Supabase credentials for driver tests.

Tests should cover driver CRUD and validation, duplicate license conflict, nullable assignment summaries, assign/change/unassign, no double assignment, inactive-driver rejection, blocking deactivation/deletion while assigned, concurrent uniqueness protection where practical, vehicle deletion releasing assignment while preserving document cleanup, authorization on every new route, and unchanged document/maintenance APIs.

## 10. Acceptance checklist

- Driver CRUD works with only name, phone, license number, license expiry, and Active/Inactive status.
- Duplicate normalized license numbers cannot be stored.
- Driver list is searchable, paginated, filterable, stably sorted, and includes its assigned-vehicle summary without N+1 queries.
- A vehicle can have zero or one current driver; a driver can have zero or one current vehicle.
- Only Active, currently unassigned drivers can be newly assigned; an assignment can be changed or explicitly removed.
- Assignment is independent from normal vehicle create/update and from vehicle status, documents, maintenance records, and dashboard calculations.
- Vehicle list/detail responses include an optional driver summary; old vehicle request/response fields and CRUD behavior remain compatible.
- Assigned drivers cannot be deactivated or deleted silently; clients receive actionable conflict errors.
- All new routes use JWT and the established API response/error conventions; no role-based behavior is added.
- V4 migration is forward-only; README no longer contradicts the implemented driver scope.
- Existing document upload/history/download and maintenance behavior remains unchanged and their tests pass.
