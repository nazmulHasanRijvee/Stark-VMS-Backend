# Stark VMS Backend

Separate Spring Boot backend for the Stark VMS Flutter Web application.

## Foundation versions and scope

- Java 21
- Spring Boot 4.1.2 (the version already selected by this backend project)
- Maven Wrapper 3.3.4, configured to use Maven 3.10.0
- Base package: `com.stark.vms`

This setup stage provides PostgreSQL connectivity and the Actuator health endpoint. It intentionally adds no VMS entities, repositories, feature controllers, authentication, migrations, or seed data.

## Local PostgreSQL configuration

The local development settings belong in the ignored external file `config/application-local.properties`, outside `src/main/resources` so credentials are not packaged into the application JAR. It should contain the following keys with your local values:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/stark_vms
spring.datasource.username=your_local_postgres_user
spring.datasource.password=your_local_password
```

The file is already ignored by `.gitignore`. Activate the `local` profile in your IntelliJ run configuration, or from PowerShell in the backend project directory:

```powershell
$env:SPRING_PROFILES_ACTIVE = "local"
./mvnw.cmd spring-boot:run
```

Do not activate `local` in the shared `application.properties`; select it only for local development. Spring Boot loads `config/application-local.properties` when the `local` profile is active.

For other environments, configure the datasource through these process environment variables:

```text
SPRING_DATASOURCE_URL       (defaults to jdbc:postgresql://localhost:5432/stark_vms)
SPRING_DATASOURCE_USERNAME (required)
SPRING_DATASOURCE_PASSWORD (required)
```

`.env.example` lists these names with placeholder values. Spring Boot does not load `.env` files by itself; use environment variables or a tool that explicitly loads `.env`. Never commit real credentials.

## Schema and health endpoint

Hibernate schema changes are disabled with `spring.jpa.hibernate.ddl-auto=none`; SQL logging is off and `spring.jpa.open-in-view` is disabled. The only exposed Actuator endpoint is `/actuator/health`, with health details hidden.

When the application and configured database are available, check `http://localhost:8080/actuator/health`. The expected response is:

```json
{"status":"UP"}
```

## Build and run

Run the tests with the Maven Wrapper:

```powershell
./mvnw.cmd test
```

The test profile uses an in-memory H2 database and does not need local PostgreSQL credentials. Build the executable JAR with:

```powershell
./mvnw.cmd package
```

## Part 1 verification

- The Maven package build succeeded on Java 21; the test profile passed 2 tests using H2.
- A read-only query using the local profile connected to PostgreSQL 18.6 and returned `stark_vms` as the current database.
- The local application started with the `local` profile. `/actuator/health` returns `{"status":"UP"}` while the application and database are available.
- No domain entities, schema migrations, seed data, authentication behavior, or VMS feature endpoints were added.

## Package layout

- `com.stark.vms.config` - future configuration classes
- `com.stark.vms.auth` - reserved for the later auth stage
- `com.stark.vms.vehicle` - reserved for the later vehicle stage
- `com.stark.vms.common.error` - reserved for shared error handling

These are placeholders only; no domain schema or feature endpoints have been implemented.

## Handoff notes for Part 2

Before implementing auth and vehicle logic, reconcile the current Flutter code with `C:\stark_vms\stark_vms\feature_overview.md`:

- **Roles:** the overview specifies `ROLE_ADMIN` for its demo account and `ROLE_USER` for registration, while the current Flutter auth flow stores an access token and does not model or enforce roles. Agree on the role claim and how the client will use it.
- **Vehicle JSON keys:** the overview's API examples use `companyName` and `vehicleType`; the Flutter `Vehicle` model serializes and reads `company` and `type`. Choose a shared wire format or add explicit mapping on one side.
- **Success envelope:** the overview wraps payloads in `{success, message, data}`, while Flutter's success handler passes the response object through unchanged and login reads `accessToken` at the top level. Either unwrap `data` in the client or specify a compatible response shape.

These contract decisions remain open for the domain and API stages.
