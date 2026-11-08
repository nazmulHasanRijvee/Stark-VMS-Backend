# Stark VMS API: local Postman testing

This guide uses the importable collection [`Stark-VMS-Local.postman_collection.json`](./Stark-VMS-Local.postman_collection.json). The collection covers public health/auth routes, bearer-protected vehicle and dashboard routes, expected error cases, and CORS preflight responses.

## 1. Start the backend locally

Start PostgreSQL and confirm the local `stark_vms` database is available. Start the backend with the `local` profile using IntelliJ or PowerShell. The local configuration file should be `config/application-local.properties` in the backend project and contain your local datasource URL, username, and password.

The application also requires `VMS_JWT_SECRET`. It must be Base64 text that decodes to at least 32 bytes. Keep the value private. `VMS_JWT_ACCESS_TOKEN_LIFETIME` is a Spring duration; the default is `PT24H`, and `PT60H` gives a 60-hour token lifetime. Spring Boot does not load `.env` files by itself, so define these as process/IDE environment variables or use a tool that loads `.env`.

For example, the backend should be reachable at `http://localhost:8080`; first confirm `GET http://localhost:8080/actuator/health` returns HTTP 200 and `{ "status": "UP" }`. The collection includes this health request.

You do not need demo data to use the collection. It registers a fresh test account and then logs in with it. If you prefer to test the seeded demo account, enable the local `demo` profile and provide `VMS_DEMO_PASSWORD`; do not assume a demo password is configured.

## 2. Import and configure the collection

1. In Postman, choose **Import** and select `Stark-VMS-Local.postman_collection.json`.
2. The collection variables default to `serverOrigin = http://localhost:8080` and `baseUrl = http://localhost:8080/api/v1`.
3. If your backend uses another host or port, edit both collection variables. Keep `/api/v1` at the end of `baseUrl`.
4. The collection has a default test password (`secret123`) and a generated email prefix (`postman-test`). You can change `password`, `fullName`, or `emailPrefix` under the collection's **Variables** tab before running it.

## 3. Run the requests

Run the collection in the displayed order, or send individual requests in this sequence:

1. **Health > Health check** confirms the process is up.
2. **Authentication > Register test user** creates a new user and generates a unique email address.
3. **Authentication > Duplicate registration** reuses that email and expects HTTP 409.
4. **Authentication > Login test user** logs in and stores `data.accessToken` in the collection variable `accessToken`.
5. **Vehicles > List vehicles** confirms protected reads work.
6. **Vehicles > Create test vehicle** creates a unique vehicle number and stores it as `vehicleNumber`.
7. **Vehicles > Duplicate vehicle create**, **Read test vehicle**, and **Update test vehicle** exercise conflict, read, and update behavior.
8. **Vehicles > Delete test vehicle** removes the test vehicle.
9. **Dashboard > Dashboard statistics** returns counts for the current database after that create/update/delete flow.

To observe the dashboard count change while the test vehicle exists, manually send **Dashboard statistics** before creating the vehicle, then again after **Update test vehicle** and before **Delete test vehicle**. Registration leaves its newly created test user in the local database; it uses a different email on each run. The vehicle is removed if the delete request succeeds.

The `Authentication` folder also has invalid-registration and invalid-login checks. The `Vehicles` folder includes expected 400, 401, and 404 checks. The `CORS` folder sends preflight `OPTIONS` requests; the configured local default is `http://localhost:3000`. If you changed `VMS_CORS_ALLOWED_ORIGINS`, update the **Origin** header in **Allowed local preflight** to match.

You can run requests individually as well. Run **Register test user** before **Login test user**, and run **Login test user** before any request that requires authorization. Protected collection requests inherit `Authorization: Bearer {{accessToken}}`; the login scripts save the token automatically. Registration does not issue a token.

## 4. API request reference

API requests use the `/api/v1` base path. Except for `/actuator/health`, success responses have this envelope:

```json
{
  "success": true,
  "message": "...",
  "data": {}
}
```

Errors use `{ "success": false, "message": "...", "timestamp": "..." }`; validation errors also include `fieldErrors`. Error responses do not contain SQL details, stack traces, passwords, or tokens.

| Method | Path | Authentication | Successful status |
|---|---|---|---:|
| GET | `/actuator/health` | Public | 200 |
| POST | `/api/v1/auth/register` | Public | 201 |
| POST | `/api/v1/auth/login` | Public | 200 |
| GET | `/api/v1/vehicles` | Bearer token | 200 |
| GET | `/api/v1/vehicles/{vehicleNumber}` | Bearer token | 200 |
| POST | `/api/v1/vehicles` | Bearer token | 201 |
| PUT | `/api/v1/vehicles/{vehicleNumber}` | Bearer token | 200 |
| DELETE | `/api/v1/vehicles/{vehicleNumber}` | Bearer token | 200 |
| GET | `/api/v1/dashboard/stats` | Bearer token | 200 |

### Registration and login

Registration request:

```json
{
  "fullName": "Postman Local Tester",
  "email": "unique-user@example.com",
  "password": "secret123"
}
```

The password must be at least 6 characters (maximum 72 UTF-8 bytes). Registration returns the safe user in `data`; it does not return an access token.

Login request uses `username` for the email address:

```json
{
  "username": "unique-user@example.com",
  "password": "secret123"
}
```

Login returns `data.accessToken`, `data.tokenType`, `data.expiresIn` (seconds), and `data.user`. The collection saves only the access token and sends it as `Authorization: Bearer <accessToken>` for protected requests. There is no refresh token or refresh endpoint. An absent, invalid, or expired token returns HTTP 401.

### Vehicles

Create/update requests accept these six business fields only:

```json
{
  "vehicleNumber": "POSTMAN-123456789",
  "companyName": "Postman Test Fleet",
  "vehicleType": "Van",
  "brand": "TestBrand",
  "model": "Local Tester",
  "status": "Active"
}
```

Valid status values are exactly `Active`, `In Service`, and `Inactive`. Vehicle responses add read-only `id`, `createdAt`, and `updatedAt` fields. Vehicle numbers are normalized to uppercase; duplicates return 409 and missing vehicles return 404. For numbers containing spaces or punctuation, URL-encode the entire number as one path segment; for example, use `DHAKA%20METRO%20GA-12-3456` in the URL path.

The list endpoint supports `search`, `status`, `page`, and `size`. `search` is a case-insensitive substring match over vehicle number, company name, brand, and model. `page` is zero-based (default 0); `size` defaults to 10 and is capped at 100. The response page fields are `content`, `page`, `size`, `totalElements`, and `totalPages`.

### Dashboard

`GET /api/v1/dashboard/stats` returns database-backed `totalVehicles`, `activeVehicles`, `inServiceVehicles`, and `inactiveVehicles`. Counts depend on the rows in your local database; they are not fixed demo values.

## 5. Interpreting common responses

- **400 Bad Request:** invalid request data, invalid status, invalid page, or a page size outside 1–100. Validation details appear in `fieldErrors` when available.
- **401 Unauthorized:** missing, malformed, invalid, or expired bearer token; invalid login also returns a generic 401 message.
- **404 Not Found:** vehicle number does not exist.
- **409 Conflict:** duplicate email or normalized vehicle number.
- **CORS preflight 403:** the `Origin` is not in `VMS_CORS_ALLOWED_ORIGINS`. Postman does not enforce browser CORS itself; inspect the preflight status and response headers here, then test from the browser frontend too.

