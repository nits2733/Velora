# Velora Backend

Spring Boot 3 / Java 21 REST API for Velora, a home-services app.

Velora supports two customer journeys: **Full Home Services** (a designer-led,
end-to-end interior-design project) and **Individual Services** (a standalone trade
job — painting, plumbing, electrical, carpentry, false ceiling, modular kitchen). Both
share the same account type (a `PROFESSIONAL`), the same portfolio model, and the same
booking lifecycle. A customer can either pick a specific professional directly
(browsing their public profile and portfolio first, Full Home Services only) or ask
Velora to assign the best-fit one — an admin then gets a ranked, scored list of
candidate professionals (specialization, portfolio, experience, location, rating,
budget fit) and assigns the pick. Either path converges into the same journey:
booking → quotation → (future) project execution → completion → review.

Full scope beyond what's built so far — project execution/scheduling, milestones,
payments, material procurement — is tracked as later phases; see
[`IMPLEMENTATION_FLOW.md`](./IMPLEMENTATION_FLOW.md) and [`FLOW.md`](./FLOW.md) for
exactly what's built vs. not.

## Tech Stack

- Java 21, Spring Boot 3.3
- Spring Security + JWT (jjwt) — stateless auth, three roles (CUSTOMER, PROFESSIONAL, ADMIN)
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
| Demo professional | `designer@velora.com` | `Designer@123` | PROFESSIONAL |
| Bootstrap admin | `admin@velora.com` | `Admin@123` | ADMIN |

(The demo professional account's email/password were seeded before the `DESIGNER`→
`PROFESSIONAL` rename — only its `role` column changed, not its login credentials.)

There's no self-registration path for admin accounts (`AuthService.register` blocks
it unconditionally) — the seeded account above is currently the only way to reach any
`/api/bookings/{id}/assign` or `/recommendations` endpoint.

## Running Tests

```bash
mvn test
```

Unit tests (JUnit 5 + Mockito + AssertJ) cover: JWT issuance/validation, auth
registration rules (duplicate email, admin self-registration blocked, professional
profile auto-creation), booking creation/state-transition/ownership rules (including
the Full Home Services vs. Individual Service split and the assignment-request path),
quotation draft/send/accept/reject rules, professional-matching scoring/ranking/
tie-breaking, and review creation/duplicate-prevention/rating recomputation. There is
no integration test against a real Postgres instance in this environment — verify
manually against your Neon instance with `mvn spring-boot:run` and the endpoints below
before deploying.

## Module Overview

| Module | Responsibility |
|---|---|
| Auth | Register (customer/professional only), login, JWT issuance, password hashing (BCrypt) |
| User | Get/update own profile — customer, or professional (bio/specialization/city/availability/rating) |
| Professional (public) | Single-professional public profile lookup by id (bio, experience, availability, rating) |
| Portfolio | Categories, professional work-sample catalog with search/filter/pagination/upload, item detail |
| Booking | Book a Full Home Services project (direct professional pick, or request Velora to assign one) or an Individual Service request (always Velora-assigned) |
| Professional Matching | Admin-only: rank candidate professionals for an assignment-requested booking |
| Quotation | Post-consultation itemized estimate: draft → send → customer accepts/rejects |
| Review | One rating (1-5) + comment per completed booking; recomputes the professional's aggregate rating |

Explicitly **out of scope** so far: project execution/scheduling, milestones,
payments (Razorpay), notifications (Firebase), Cloudinary image upload (portfolio
items accept an image *URL* today, not a file), Google Maps, refresh tokens, OTP,
password reset, a real professional-availability calendar (today it's a simple
self-toggled flag), and a searchable professional directory (today it's
single-profile lookup by id only).

## API Reference

All endpoints are prefixed `/api`. Protected endpoints require
`Authorization: Bearer <token>`.

### Auth (public)
| Method | Path | Description |
|---|---|---|
| POST | `/auth/register` | Register as CUSTOMER or PROFESSIONAL (ADMIN is rejected) |
| POST | `/auth/login` | Login, returns a JWT |

### User (authenticated)
| Method | Path | Description |
|---|---|---|
| GET | `/users/profile` | Get own profile (includes professional profile section if PROFESSIONAL) |
| PUT | `/users/profile` | Update own profile (partial update, incl. availability toggle) |

### Professionals (public)
| Method | Path | Description |
|---|---|---|
| GET | `/professionals/{id}` | Get one professional's public profile |

### Portfolio Catalog
| Method | Path | Role | Description |
|---|---|---|---|
| GET | `/categories` | public | List all categories (each tagged HOME_PROJECT or INDIVIDUAL_SERVICE) |
| GET | `/portfolio?category=&search=&style=&page=&size=&sortBy=&direction=` | public | Paginated/filterable portfolio catalog |
| GET | `/portfolio/{id}` | public | Portfolio item detail |
| POST | `/portfolio` | PROFESSIONAL | Upload a new portfolio item under the caller's own account; `styleTag`/`priceEstimate` are optional — supplying either creates the item's `InteriorDesignDetails` satellite, omitting both leaves it a plain generic item |

### Bookings (authenticated)
| Method | Path | Role | Description |
|---|---|---|---|
| POST | `/bookings` | CUSTOMER | Create a booking — `requestType` FULL_HOME_PROJECT (with or without a chosen `professionalId`) or INDIVIDUAL_SERVICE (always Velora-assigned) |
| GET | `/bookings?page=&size=` | any | Own bookings (customer: booked; professional: assigned) |
| GET | `/bookings/{id}` | participant | Booking detail |
| PATCH | `/bookings/{id}/cancel` | CUSTOMER (owner) | Cancel a PENDING_ASSIGNMENT/PENDING/CONFIRMED booking |
| PATCH | `/bookings/{id}/status` | PROFESSIONAL (assigned) | PENDING→CONFIRMED/CANCELLED, CONFIRMED→COMPLETED/CANCELLED |
| GET | `/bookings/{id}/recommendations` | ADMIN | Ranked candidate professionals for a booking awaiting assignment |
| PATCH | `/bookings/{id}/assign` | ADMIN | Assign a professional to a booking awaiting assignment |

### Quotations (authenticated, nested under a booking)
| Method | Path | Role | Description |
|---|---|---|---|
| PUT | `/bookings/{bookingId}/quotation` | PROFESSIONAL (assigned) | Create/replace a draft quotation |
| POST | `/bookings/{bookingId}/quotation/send` | PROFESSIONAL (assigned) | Send the draft to the customer |
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
| `V1__init_schema.sql` | Creates `users`, `designer_profiles`, `categories`, `designs`, `bookings` (original names, since renamed — see `V7`/`V8`) |
| `V2__seed_data.sql` | Seeds categories, a demo designer account + profile, sample designs |
| `V3__quotations.sql` | Adds `quotations` + `quotation_line_items` |
| `V4__admin_and_designer_assignment.sql` | Adds `ADMIN` role, makes `bookings.designer_id` nullable, adds `PENDING_ASSIGNMENT` status, seeds a bootstrap admin account |
| `V5__ratings_and_availability.sql` | Adds `reviews`; adds availability/rating columns to `designer_profiles`; adds category/style/budget/location columns to `bookings` for matching |
| `V6__widen_review_rating_column.sql` | Fixes a `SMALLINT`/`INTEGER` column-type mismatch found in `V5` at first boot |
| `V7__rename_designer_to_professional_and_add_request_types.sql` | Renames role `DESIGNER`→`PROFESSIONAL`, `designer_profiles`→`professional_profiles`, every `designer_id` column→`professional_id`; adds `bookings.request_type` (`FULL_HOME_PROJECT`/`INDIVIDUAL_SERVICE`) and `categories.service_group` (`HOME_PROJECT`/`INDIVIDUAL_SERVICE`); seeds the six Individual Service trade categories |
| `V8__generalize_design_to_portfolio_item.sql` | Renames `designs`→`portfolio_items`, `bookings.design_id`→`portfolio_item_id`; splits `style_tag`/`price_estimate` out into a new optional `interior_design_details` satellite table |

## Design Notes / Extensibility

- **`PortfolioItem` + optional `InteriorDesignDetails`, not one flat table.** A work
  sample is generic across every trade (`title`, `description`, `category`,
  `coverImageUrl`) — a painter's before/after photo and an interior designer's concept
  board are the same shape at that level. Only interior design work has a structured
  `styleTag`/`priceEstimate` worth filtering and sorting on, so those two fields live
  in a separate, optional 1-to-1 `InteriorDesignDetails` row instead of on
  `PortfolioItem` itself — a plumber's or electrician's portfolio item simply has no
  row there. This mirrors the same shape already used for `User` + optional
  `ProfessionalProfile` (one satellite row only if relevant), rather than introducing
  a new pattern, a generic JSON metadata blob (which would break this codebase's
  zero-`@Query`, fully-typed-and-filterable convention), or JPA inheritance (more
  complexity than the domain currently needs).
- A professional can upload their own portfolio items (`POST /portfolio`) — but there's
  still no update/delete endpoint yet, so a mis-entered item can't be corrected or
  removed via the API today.
- Portfolio images are plain URL strings (`cover_image_url`) for now — swap for a
  Cloudinary-backed upload flow later without changing `PortfolioItem`'s public shape.
- Portfolio items are never directly purchasable — a booking only optionally
  *references* one for context; the priced artifact is always the `Quotation` created
  after a consultation, never the catalog entry itself.
- Professional "availability" is a self-managed on/off flag today, not a real
  scheduling calendar — `ProfessionalMatchingService` just filters on it. A future
  calendar can replace the stored flag with a computed one without changing how
  matching consumes it.
- Professional matching is a transparent, deterministic weighted formula — out of 100
  points: specialization/category match 30, portfolio evidence 20 (category match 10 +
  style match 10), experience 15 (capped at 10 years), location match 15, rating 15
  (unrated professionals get a neutral 3.0/5 midpoint, not zero), budget fit 5 — no ML,
  no external scoring service — and always produces a **ranked list an admin
  confirms**, never a silent auto-assignment.
- JWT is access-token-only (no refresh token) — expiry is deliberately short-lived-friendly
  via `JWT_EXPIRATION_MS`; refresh-token rotation is a later phase.
- All list endpoints are paginated (`page`, `size`, capped at 50) from day one to avoid
  an unpaginated endpoint becoming a breaking change later.
- See [`IMPLEMENTATION_FLOW.md`](./IMPLEMENTATION_FLOW.md) for a full code-level
  walkthrough of every layer, and [`FLOW.md`](./FLOW.md) for a no-code, plain-language
  walkthrough of every request flow end to end.
