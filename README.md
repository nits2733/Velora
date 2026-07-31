# Velora Backend

Spring Boot 3 / Java 21 REST API for the Velora interior-design and home-services app.

Velora connects customers with designers for interior-design projects. A customer can
either pick a specific designer directly (browsing their public profile and portfolio
first) or ask Velora to assign the best-fit one — an admin then gets a ranked,
scored list of candidate designers (specialization, portfolio, experience, location,
rating, budget fit) and assigns the pick. Either path converges into the same journey:
booking → quotation → (future) project execution → completion → review.

Full scope beyond what's built so far — project execution/scheduling, milestones,
payments, material procurement — is tracked as later phases; see
[`IMPLEMENTATION_FLOW.md`](./IMPLEMENTATION_FLOW.md) and [`FLOW.md`](./FLOW.md) for
exactly what's built vs. not.

## Tech Stack

- Java 21, Spring Boot 3.3
- Spring Security + JWT (jjwt) — stateless auth, three roles (CUSTOMER, DESIGNER, ADMIN)
- Spring Data JPA + PostgreSQL (hosted on [Neon](https://neon.tech), no local DB server needed)
- Flyway for schema migrations
- springdoc-openapi (Swagger UI)
- spring-dotenv (auto-loads `.env` at startup)
- Maven

## Getting Started

### 1. Get a Postgres connection

This project runs against a cloud-hosted **Neon** Postgres instance — no local
database server or Docker required. Create a free project at
[neon.tech](https://neon.tech) (or point at any Postgres 14+ instance you already
have) and grab its connection string.

### 2. Configure environment

Copy `.env.example` to `.env` and fill in your real values:

```bash
cp .env.example .env
```

At minimum set:
- `DB_URL` — e.g. `jdbc:postgresql://<your-neon-host>/<dbname>?sslmode=require`
- `DB_USERNAME` / `DB_PASSWORD` — from your Postgres provider
- `JWT_SECRET` — 32+ bytes; generate one with `openssl rand -base64 48`

The app **fails fast at startup** if `JWT_SECRET` is missing or too short — this is
intentional, it must never be allowed to default silently in a real deployment.
`.env` is gitignored and loaded automatically at startup via `spring-dotenv`; you
never need to manually `export` anything.

### 3. Run

```bash
mvn spring-boot:run
```

Or, for local development conveniences (a safe placeholder JWT secret so you don't
have to generate one immediately, plus verbose SQL logging):

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Flyway applies every migration in `src/main/resources/db/migration/` automatically
on startup, in order (`V1` through the latest — see
[Database Migrations](#database-migrations) below).

### 4. Explore the API

- Swagger UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- Health check: http://localhost:8080/actuator/health

### 5. Log in as the seeded accounts

Two accounts are seeded by migrations so you can exercise the API immediately without
registering everything from scratch:

| Account | Email | Password | Role |
|---|---|---|---|
| Demo designer | `designer@velora.com` | `Designer@123` | DESIGNER |
| Bootstrap admin | `admin@velora.com` | `Admin@123` | ADMIN |

There's no self-registration path for admin accounts (`AuthService.register` blocks
it unconditionally) — the seeded account above is currently the only way to reach any
`/api/bookings/{id}/assign` or `/recommendations` endpoint.

## Running Tests

```bash
mvn test
```

Unit tests (JUnit 5 + Mockito + AssertJ) cover: JWT issuance/validation, auth
registration rules (duplicate email, admin self-registration blocked, designer profile
auto-creation), booking creation/state-transition/ownership rules (including the
assignment-request path), quotation draft/send/accept/reject rules, designer-matching
scoring/ranking/tie-breaking, and review creation/duplicate-prevention/rating
recomputation. There is no integration test against a real Postgres instance in this
environment — verify manually against your Neon instance with `mvn spring-boot:run`
and the endpoints below before deploying.

## Module Overview

| Module | Responsibility |
|---|---|
| Auth | Register (customer/designer only), login, JWT issuance, password hashing (BCrypt) |
| User | Get/update own profile — customer, or designer (bio/specialization/city/availability/rating) |
| Designer (public) | Single-designer public profile lookup by id (bio, experience, availability, rating) |
| Design Catalog | Categories, design portfolio listing with search/filter/pagination, design detail |
| Booking | Book a consultation — direct designer pick, or request Velora to assign one |
| Designer Matching | Admin-only: rank candidate designers for an assignment-requested booking |
| Quotation | Post-consultation itemized estimate: draft → send → customer accepts/rejects |
| Review | One rating (1-5) + comment per completed booking; recomputes the designer's aggregate rating |

Explicitly **out of scope** so far: project execution/scheduling, milestones,
payments (Razorpay), notifications (Firebase), Cloudinary image upload, Google Maps,
refresh tokens, OTP, password reset, a real designer-availability calendar (today it's
a simple self-toggled flag), and a searchable designer directory (today it's
single-profile lookup by id only).

## API Reference

All endpoints are prefixed `/api`. Protected endpoints require
`Authorization: Bearer <token>`.

### Auth (public)
| Method | Path | Description |
|---|---|---|
| POST | `/auth/register` | Register as CUSTOMER or DESIGNER (ADMIN is rejected) |
| POST | `/auth/login` | Login, returns a JWT |

### User (authenticated)
| Method | Path | Description |
|---|---|---|
| GET | `/users/profile` | Get own profile (includes availability/rating if DESIGNER) |
| PUT | `/users/profile` | Update own profile (partial update, incl. availability toggle) |

### Designers (public)
| Method | Path | Description |
|---|---|---|
| GET | `/designers/{id}` | Get one designer's public profile |

### Design Catalog (public read)
| Method | Path | Description |
|---|---|---|
| GET | `/categories` | List all categories |
| GET | `/designs?category=&search=&style=&page=&size=&sortBy=&direction=` | Paginated/filterable catalog |
| GET | `/designs/{id}` | Design detail |

### Bookings (authenticated)
| Method | Path | Role | Description |
|---|---|---|---|
| POST | `/bookings` | CUSTOMER | Book with a chosen `designerId`, or omit it to request assignment |
| GET | `/bookings?page=&size=` | any | Own bookings (customer: booked; designer: assigned) |
| GET | `/bookings/{id}` | participant | Booking detail |
| PATCH | `/bookings/{id}/cancel` | CUSTOMER (owner) | Cancel a PENDING_ASSIGNMENT/PENDING/CONFIRMED booking |
| PATCH | `/bookings/{id}/status` | DESIGNER (assigned) | PENDING→CONFIRMED/CANCELLED, CONFIRMED→COMPLETED/CANCELLED |
| GET | `/bookings/{id}/recommendations` | ADMIN | Ranked candidate designers for a booking awaiting assignment |
| PATCH | `/bookings/{id}/assign` | ADMIN | Assign a designer to a booking awaiting assignment |

### Quotations (authenticated, nested under a booking)
| Method | Path | Role | Description |
|---|---|---|---|
| PUT | `/bookings/{bookingId}/quotation` | DESIGNER (assigned) | Create/replace a draft quotation |
| POST | `/bookings/{bookingId}/quotation/send` | DESIGNER (assigned) | Send the draft to the customer |
| GET | `/bookings/{bookingId}/quotation` | participant | Get the quotation |
| PATCH | `/bookings/{bookingId}/quotation/accept` | CUSTOMER (owner) | Accept a sent quotation |
| PATCH | `/bookings/{bookingId}/quotation/reject` | CUSTOMER (owner) | Reject a sent quotation |

### Reviews (authenticated, nested under a booking)
| Method | Path | Role | Description |
|---|---|---|---|
| POST | `/bookings/{bookingId}/review` | CUSTOMER (owner) | Leave a 1-5 rating + comment (only once, only after COMPLETED) |

## Environment Variables

See `.env.example` for the full list: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`,
`DB_POOL_SIZE`, `JWT_SECRET` (required, 32+ bytes), `JWT_EXPIRATION_MS`,
`CORS_ALLOWED_ORIGINS`, `PORT`.

## Database Migrations

`ddl-auto: validate` — the schema is owned entirely by Flyway migrations in
`src/main/resources/db/migration/`, never by Hibernate auto-DDL. Migrations already
applied are **never edited** — every change, including fixing a mistake in a previous
migration, is a new `V{n}__description.sql` file.

| Migration | What it does |
|---|---|
| `V1__init_schema.sql` | Creates `users`, `designer_profiles`, `categories`, `designs`, `bookings` |
| `V2__seed_data.sql` | Seeds categories, a demo designer account + profile, sample designs |
| `V3__quotations.sql` | Adds `quotations` + `quotation_line_items` |
| `V4__admin_and_designer_assignment.sql` | Adds `ADMIN` role, makes `bookings.designer_id` nullable, adds `PENDING_ASSIGNMENT` status, seeds a bootstrap admin account |
| `V5__ratings_and_availability.sql` | Adds `reviews`; adds availability/rating columns to `designer_profiles`; adds category/style/budget/location columns to `bookings` for matching |
| `V6__widen_review_rating_column.sql` | Fixes a `SMALLINT`/`INTEGER` column-type mismatch found in `V5` at first boot |

## Design Notes / Extensibility

- Design portfolio images are plain URL strings (`cover_image_url`) for now — swap for
  a Cloudinary-backed upload flow later without changing `Design`'s public shape.
- Portfolio designs are never directly purchasable — a booking only optionally
  *references* one for context; the priced artifact is always the `Quotation` created
  after a consultation, never the catalog entry itself.
- Designer "availability" is a self-managed on/off flag today, not a real scheduling
  calendar — `DesignerMatchingService` just filters on it. A future calendar can
  replace the stored flag with a computed one without changing how matching consumes it.
- Designer matching is a transparent, deterministic weighted formula (specialization
  30 / portfolio 20 / experience 15 / location 15 / rating 15 / budget 5, out of 100) —
  no ML, no external scoring service — and always produces a **ranked list an admin
  confirms**, never a silent auto-assignment.
- JWT is access-token-only (no refresh token) — expiry is deliberately short-lived-friendly
  via `JWT_EXPIRATION_MS`; refresh-token rotation is a later phase.
- All list endpoints are paginated (`page`, `size`, capped at 50) from day one to avoid
  an unpaginated endpoint becoming a breaking change later.
- See [`IMPLEMENTATION_FLOW.md`](./IMPLEMENTATION_FLOW.md) for a full code-level
  walkthrough of every layer, and [`FLOW.md`](./FLOW.md) for a no-code, plain-language
  walkthrough of every request flow end to end.
