# Velora Backend — MVP 1

Spring Boot 3 / Java 21 REST API for the Velora interior design mobile app. This is **MVP 1** of the
full [Velora architecture](../Interior%20Design%20Mobile%20App%20-%20Complete%20Tech%20Stack%20and%20Architecture-2(1).pdf):
Auth, User, Design Catalog, and Booking only. Quotation, Payment, Notification, Admin, Cloudinary, Firebase,
Razorpay, and Google Maps are **not** implemented yet — they are later phases.

## Tech Stack

- Java 21, Spring Boot 3.3
- Spring Security + JWT (jjwt) — stateless auth
- Spring Data JPA + PostgreSQL
- Flyway for schema migrations
- springdoc-openapi (Swagger UI)
- Maven

## Getting Started

### 1. Start PostgreSQL locally

```bash
docker compose up -d
```

This starts Postgres on `localhost:5432` with db/user/password `velora` (see `docker-compose.yml`).

### 2. Configure environment

Copy `.env.example` and set a real `JWT_SECRET` (32+ bytes):

```bash
cp .env.example .env
# generate a secret:
openssl rand -base64 48
```

The app **fails fast at startup** if `JWT_SECRET` is missing or too short — this is intentional, it must
never be allowed to default silently in a real deployment.

### 3. Run

With the `local` profile (loads a dev-only fallback JWT secret and DEBUG logging if you don't want to set env vars yourself):

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Or export the env vars from `.env` and run the default profile:

```bash
mvn spring-boot:run
```

Flyway runs the migration in `src/main/resources/db/migration/V1__init_schema.sql` automatically on startup.

### 4. Explore the API

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- Health check: http://localhost:8080/actuator/health

## Running Tests

```bash
mvn test
```

Unit tests cover JWT issuance/validation, auth registration rules (duplicate email, designer profile
auto-creation), and booking state-transition/ownership rules. There is no integration test against a real
Postgres instance in this environment (no Docker daemon available at build time) — verify manually with
`docker compose up -d` + `mvn spring-boot:run` before deploying, and hit the endpoints below.

## Module Overview (MVP 1 scope)

| Module | Responsibility |
|---|---|
| Auth | Register, login, JWT issuance, password hashing (BCrypt) |
| User | Get/update profile (customer or designer, incl. designer bio/specialization/city) |
| Design Catalog | Categories, design listing with search/filter/pagination, design detail |
| Booking | Book a consultation, view own bookings, cancel, designer status updates |

Explicitly **out of scope** for MVP 1 (see architecture doc phases 2+): Quotation, Payment (Razorpay),
Notification (Firebase), Admin, Cloudinary image upload, Google Maps, refresh tokens, OTP, password reset.

## API Reference

All endpoints are prefixed `/api`. Protected endpoints require `Authorization: Bearer <token>`.

### Auth (public)
| Method | Path | Description |
|---|---|---|
| POST | `/auth/register` | Register as CUSTOMER or DESIGNER |
| POST | `/auth/login` | Login, returns JWT |

### User (authenticated)
| Method | Path | Description |
|---|---|---|
| GET | `/users/profile` | Get own profile |
| PUT | `/users/profile` | Update own profile |

### Design Catalog (public read)
| Method | Path | Description |
|---|---|---|
| GET | `/categories` | List all categories |
| GET | `/designs?category=&search=&style=&page=&size=&sortBy=&direction=` | Paginated/filterable catalog |
| GET | `/designs/{id}` | Design detail |

### Booking (authenticated)
| Method | Path | Role | Description |
|---|---|---|---|
| POST | `/bookings` | CUSTOMER | Book a consultation |
| GET | `/bookings?page=&size=` | any | Own bookings (customer: booked; designer: assigned) |
| GET | `/bookings/{id}` | participant | Booking detail |
| PATCH | `/bookings/{id}/cancel` | CUSTOMER (owner) | Cancel a PENDING/CONFIRMED booking |
| PATCH | `/bookings/{id}/status` | DESIGNER (assigned) | Transition PENDING→CONFIRMED/CANCELLED, CONFIRMED→COMPLETED/CANCELLED |

## Environment Variables

See `.env.example` for the full list: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DB_POOL_SIZE`, `JWT_SECRET`
(required, 32+ bytes), `JWT_EXPIRATION_MS`, `CORS_ALLOWED_ORIGINS`, `PORT`.

## Design Notes / Extensibility

- `ddl-auto: validate` — schema is owned by Flyway migrations, never by Hibernate auto-DDL. Add
  `V2__...sql` etc. for future changes.
- Design images are plain URL strings (`cover_image_url`) for MVP 1 — swap for a Cloudinary-backed upload
  flow in a later phase without changing the `Design` entity's public shape.
- `Booking` has no `Quotation`/`Payment` reference yet; those tables/relations get added in MVP 2 without
  touching existing booking endpoints.
- JWT is access-token-only (no refresh token) — expiry is deliberately short-lived-friendly via
  `JWT_EXPIRATION_MS`; refresh-token rotation is a later phase per the architecture doc.
- All list endpoints are paginated (`page`, `size`, capped at 50) from day one to avoid an unpaginated
  endpoint becoming a breaking change later.
