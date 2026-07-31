# Velora Backend — Implementation Flow & Learning Guide

This document explains **everything implemented so far** in the Velora backend, in the
order a developer would naturally learn it: starting the app, understanding the folder
structure, then following one real request (register → login → call a protected
endpoint) all the way through the code.

It assumes you already know basic Java and Spring Boot syntax (annotations, classes,
interfaces). Every section explains **what** a piece is, **why** it exists, and **how**
it works here, with small examples.

---

## Table of Contents

1. [Project Purpose & Tech Stack](#1-project-purpose--tech-stack)
2. [Project Structure](#2-project-structure)
3. [Application Startup Flow](#3-application-startup-flow)
4. [Configuration Layer](#4-configuration-layer)
5. [Database Design](#5-database-design)
6. [Entities (JPA Layer)](#6-entities-jpa-layer)
7. [DTOs — Why We Never Return Entities Directly](#7-dtos--why-we-never-return-entities-directly)
8. [Repositories (Data Access Layer)](#8-repositories-data-access-layer)
9. [Security Architecture (Big Picture)](#9-security-architecture-big-picture)
10. [Authentication Flow — Register](#10-authentication-flow--register)
11. [Authentication Flow — Login](#11-authentication-flow--login)
12. [How a Protected Request Is Authenticated (JWT Filter)](#12-how-a-protected-request-is-authenticated-jwt-filter)
13. [Authorization — Roles and `@PreAuthorize`](#13-authorization--roles-and-preauthorize)
14. [Full Request Lifecycle (Controller → Service → Repository → DB)](#14-full-request-lifecycle-controller--service--repository--db)
15. [Feature Walkthrough: Design Catalog (Search/Filter/Pagination)](#15-feature-walkthrough-design-catalog-searchfilterpagination)
16. [Feature Walkthrough: Bookings (State Machine)](#16-feature-walkthrough-bookings-state-machine)
17. [Feature Walkthrough: Quotations (Post-Consultation Estimate)](#17-feature-walkthrough-quotations-post-consultation-estimate)
18. [Feature Walkthrough: User Profile (Role-Conditional Data)](#18-feature-walkthrough-user-profile-role-conditional-data)
19. [Validation Flow](#19-validation-flow)
20. [Exception Handling Flow](#20-exception-handling-flow)
21. [API Documentation (Swagger/OpenAPI)](#21-api-documentation-swaggeropenapi)
22. [Environment & Secrets Management](#22-environment--secrets-management)
23. [Quick Reference: All Endpoints](#23-quick-reference-all-endpoints)

---

## 1. Project Purpose & Tech Stack

Velora is the backend API for an interior-design booking app. It has two kinds of
users interacting with it:

- **Customers** — browse a catalog of interior designs, book consultations with designers,
  and receive/respond to line-item quotations after a consultation.
- **Designers** — publish designs (conceptually; upload is not yet built), manage the
  bookings customers make with them, and turn a booking into a scoped quotation (line
  items + total) once the consultation has happened.
- Both share one `users` table, distinguished by a `role` column.

A booking's purpose is to get a designer and customer talking; a **quotation** is what
turns that conversation into a priced, actionable scope of work the customer can accept
or reject. See [Feature Walkthrough: Quotations](#17-feature-walkthrough-quotations-post-consultation-estimate).

**Tech stack:**

| Concern              | Technology                                  |
|----------------------|----------------------------------------------|
| Language / runtime   | Java 21                                      |
| Framework            | Spring Boot 3.3.4                            |
| Web layer            | Spring MVC (`spring-boot-starter-web`)       |
| Data access           | Spring Data JPA + Hibernate                  |
| Database              | PostgreSQL (hosted on Neon, a serverless Postgres provider) |
| Migrations            | Flyway                                       |
| Security              | Spring Security 6 (stateless, JWT-based)     |
| Token format          | JWT (via `jjwt` library)                     |
| Validation            | Jakarta Bean Validation (`spring-boot-starter-validation`) |
| API docs              | springdoc-openapi (Swagger UI)               |
| Boilerplate reduction | Lombok                                       |
| Build tool            | Maven                                        |

---

## 2. Project Structure

```
src/main/java/com/velora/backend/
├── VeloraBackendApplication.java   # Entry point (main method)
├── config/                          # App-wide configuration & settings classes
│   ├── SecurityConfig.java          # Security rules, CORS, password encoder
│   ├── JwtProperties.java           # Binds app.jwt.* properties, validates on startup
│   ├── CorsProperties.java          # Binds app.cors.* properties
│   └── OpenApiConfig.java           # Swagger/OpenAPI setup
├── controller/                      # REST endpoints (the "front door")
│   ├── AuthController.java          # /api/auth/**
│   ├── UserController.java          # /api/users/**
│   ├── DesignController.java        # /api/designs/**
│   ├── CategoryController.java      # /api/categories/**
│   ├── BookingController.java       # /api/bookings/**
│   └── QuotationController.java     # /api/bookings/{bookingId}/quotation
├── service/                          # Business logic (the "brain")
│   ├── AuthService.java
│   ├── UserService.java
│   ├── DesignService.java
│   ├── CategoryService.java
│   ├── BookingService.java
│   └── QuotationService.java
├── repository/                       # Data access (talks to the database)
│   ├── UserRepository.java
│   ├── DesignerProfileRepository.java
│   ├── DesignRepository.java
│   ├── DesignSpecifications.java     # Dynamic query building for search/filter
│   ├── CategoryRepository.java
│   ├── BookingRepository.java
│   └── QuotationRepository.java
├── entity/                            # JPA entities = database tables as Java classes
│   ├── User.java, Role.java
│   ├── DesignerProfile.java
│   ├── Category.java
│   ├── Design.java
│   ├── Booking.java, BookingStatus.java
│   └── Quotation.java, QuotationLineItem.java, QuotationStatus.java
├── dto/                               # Data Transfer Objects = what the API sends/receives
│   ├── auth/   (RegisterRequest, LoginRequest, AuthResponse)
│   ├── user/   (UserProfileResponse, UpdateProfileRequest, DesignerProfileResponse)
│   ├── design/ (DesignResponse, DesignSummaryResponse, CategoryResponse)
│   ├── booking/(BookingRequest, BookingResponse, BookingStatusUpdateRequest)
│   └── quotation/(SaveQuotationRequest, QuotationLineItemRequest,
│                  QuotationResponse, QuotationLineItemResponse)
├── mapper/                            # Converts entities <-> DTOs
│   ├── UserMapper.java
│   ├── DesignMapper.java
│   ├── BookingMapper.java
│   └── QuotationMapper.java
├── security/                          # JWT + Spring Security plumbing
│   ├── JwtService.java               # Create/parse/validate tokens
│   ├── JwtAuthFilter.java            # Runs on every request, checks the token
│   ├── CustomUserDetailsService.java # Loads a User from the DB for Spring Security
│   ├── UserPrincipal.java            # Adapts our User entity to Spring Security's UserDetails
│   ├── RestAuthEntryPoint.java       # Returns JSON 401 (instead of a redirect/login page)
│   └── RestAccessDeniedHandler.java  # Returns JSON 403
├── exception/                         # Custom exceptions + centralized error handling
│   ├── GlobalExceptionHandler.java   # Catches exceptions app-wide, builds error JSON
│   ├── ApiErrorResponse.java         # Shape of every error response
│   ├── ResourceNotFoundException.java
│   ├── DuplicateResourceException.java
│   ├── InvalidStateTransitionException.java
│   └── UnauthorizedActionException.java
└── util/
    └── PageResponse.java              # Generic wrapper for paginated API responses

src/main/resources/
├── application.yml                   # Main config (reads from env vars)
├── application-local.yml             # Overrides for local dev (`local` profile)
└── db/migration/
    ├── V1__init_schema.sql           # Flyway migration: creates all tables
    ├── V2__seed_data.sql             # Seed categories, a demo designer, sample designs
    └── V3__quotations.sql            # Adds quotations + quotation_line_items tables
```

**Why this layered structure?**
Each layer has exactly one job, and only talks to the layer directly below it:

```
Controller  →  Service  →  Repository  →  Database
   (HTTP)      (business        (data
                 rules)          access)
```

This is the standard **layered architecture**. It keeps HTTP concerns (status codes,
JSON) out of business logic, and keeps business logic out of SQL/JPA details. It also
makes each layer independently testable.

---

## 3. Application Startup Flow

When you run `mvn spring-boot:run`, here's what happens, roughly in order:

1. **`VeloraBackendApplication.main()`** runs `SpringApplication.run(...)`.
   - `@SpringBootApplication` triggers component scanning (finds all `@Component`,
     `@Service`, `@Repository`, `@RestController`, `@Configuration` classes under
     `com.velora.backend`) and auto-configuration (Spring Boot guesses sensible
     defaults based on what's on the classpath — e.g. seeing `postgresql` + `flyway-core`
     on the classpath makes it auto-configure a Flyway migration runner).
   - `@EnableJpaAuditing` turns on automatic population of `@CreatedDate` /
     `@LastModifiedDate` fields on entities (see [Entities](#6-entities-jpa-layer)).

2. **Configuration properties are bound.** Spring reads `application.yml` (and
   `application-local.yml` if the `local` profile is active), resolving placeholders
   like `${DB_URL:jdbc:postgresql://localhost:5432/velora}` from environment variables
   (falling back to the default after the colon if the env var isn't set).
   - `JwtProperties` and `CorsProperties` are `@ConfigurationProperties` beans that
     capture `app.jwt.*` and `app.cors.*` values.
   - `JwtProperties.validate()` runs via `@PostConstruct` — if `JWT_SECRET` is missing
     or too short, **the app refuses to start**. This is a deliberate safety guard:
     it's better to fail loudly at boot than to silently sign tokens with a weak/empty key.

3. **Flyway runs database migrations** before JPA touches the database. It looks in
   `classpath:db/migration` for versioned SQL files (`V1__init_schema.sql`, etc.),
   checks a `flyway_schema_history` table to see which have already run, and applies
   any new ones in order. This is why the tables already exist by the time Hibernate
   starts validating the schema.

4. **Hibernate (JPA) validates the schema.** `spring.jpa.hibernate.ddl-auto: validate`
   means Hibernate does **not** create/alter tables itself — it only checks that the
   entity classes match what Flyway already created, and fails startup if they don't
   match. (This is the correct production-safe setting: Flyway owns the schema, JPA
   just uses it.)

5. **Spring Security's filter chain is built** (see `SecurityConfig`), wiring in the
   JWT filter, CORS rules, and which URLs are public vs. protected.

6. **Embedded Tomcat starts** on the configured port (`8080` by default), and the app
   is ready to accept HTTP requests.

If any step fails (bad DB credentials, missing JWT secret, schema mismatch), the whole
startup fails — Spring Boot's philosophy is "fail fast, fail loud" rather than starting
in a broken half-working state.

---

## 4. Configuration Layer

**What:** Plain Java classes annotated with `@ConfigurationProperties` that give
type-safe access to groups of settings from `application.yml`, instead of littering
`@Value("${...}")` everywhere.

**Why:** Centralizes related settings, validates them once at startup, and lets IDEs
autocomplete property names.

**How it works here:**

```yaml
app:
  jwt:
    secret: ${JWT_SECRET:}
    expiration-ms: ${JWT_EXPIRATION_MS:86400000}
    issuer: velora-backend
```

maps directly onto:

```java
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {
    private String secret;
    private long expirationMs;
    private String issuer;
    // getters/setters...
}
```

Similarly `CorsProperties` binds `app.cors.allowed-origins` (a comma-separated string)
and exposes it as a clean `List<String>` via `allowedOriginsList()`.

`OpenApiConfig` is a bit different — it's a `@Configuration` class that defines a
`@Bean` describing the Swagger UI's metadata (title, version) and tells it that every
endpoint can accept a `Bearer <token>` header (a security scheme named `bearerAuth`).

---

## 5. Database Design

**Why Postgres + Flyway:** Postgres is a solid relational database well-suited to the
relational nature of this domain (users have profiles, designs belong to designers,
bookings link customers to designers to designs). Flyway gives us **version-controlled,
repeatable schema changes** — every environment (your laptop, a teammate's laptop,
production) applies the exact same SQL in the exact same order, tracked in a
`flyway_schema_history` table.

**Schema (from `V1__init_schema.sql`):**

```
users                       designer_profiles
┌─────────────────┐         ┌─────────────────────┐
│ id (PK)         │◄───────┐│ id (PK)              │
│ email (unique)  │        └│ user_id (FK, unique) │ 1-to-1
│ password_hash   │         │ bio                  │
│ full_name       │         │ years_experience     │
│ phone           │         │ specialization       │
│ role            │         │ city                 │
│ created_at      │         └─────────────────────┘
│ updated_at      │
└─────────────────┘
        ▲   ▲
        │   │
        │   └───────────────────────┐
        │                           │
categories                 designs   │            bookings
┌───────────────┐         ┌──────────┴──────┐    ┌───────────────────┐
│ id (PK)       │◄────────│ category_id (FK) │    │ id (PK)            │
│ name (unique) │        1│ designer_id (FK) │───►│ customer_id (FK)───┼──► users
│ description   │  to    │ id (PK)           │    │ designer_id (FK)───┼──► users
└───────────────┘  many  │ title             │    │ design_id (FK, null)┼─► designs
                          │ description       │    │ scheduled_at        │
                          │ cover_image_url   │    │ status              │
                          │ price_estimate    │    │ notes               │
                          │ style_tag         │    │ created_at          │
                          └──────────────────┘    └───────────────────┘
                                                              ▲
                                                              │ 1-to-1 (booking_id UNIQUE)
                                                              │
                                                    quotations │
                                                    ┌─────────┴──────────┐
                                                    │ id (PK)             │
                                                    │ booking_id (FK, unique) │
                                                    │ status              │
                                                    │ total_amount        │
                                                    │ notes               │
                                                    │ created_at          │
                                                    │ updated_at          │
                                                    └──────────┬──────────┘
                                                               │ 1-to-many
                                                               ▼
                                                    quotation_line_items
                                                    ┌──────────────────────┐
                                                    │ id (PK)               │
                                                    │ quotation_id (FK)     │
                                                    │ description           │
                                                    │ quantity              │
                                                    │ unit                  │
                                                    │ unit_price            │
                                                    │ amount                │
                                                    │ sort_order            │
                                                    └──────────────────────┘
```

**Relationships:**

| Table                | Relationship                                                     |
|-----------------------|--------------------------------------------------------------------|
| `users` ↔ `designer_profiles` | One-to-one. Only users with `role = DESIGNER` get a row here. |
| `categories` ↔ `designs`      | One-to-many. Every design belongs to exactly one category.    |
| `users` ↔ `designs`            | One-to-many (as designer). A designer can publish many designs. |
| `users` ↔ `bookings`           | One-to-many, **twice** — once as `customer_id`, once as `designer_id`. |
| `designs` ↔ `bookings`         | Optional. A booking can reference a specific design, or be a general consultation (`design_id` is nullable). |
| `bookings` ↔ `quotations`      | One-to-one, at most one quotation per booking (`booking_id UNIQUE`). Added in `V3__quotations.sql` — this is the artifact a designer produces after a consultation: a priced, itemized scope the customer can accept or reject. |
| `quotations` ↔ `quotation_line_items` | One-to-many. Each line item is one priced row (e.g. "Modular kitchen — ₹50,000"); `total_amount` on the quotation is the sum of all its line items' `amount`. |

**Constraints worth noting:**
- `role` and `status` columns use SQL `CHECK` constraints (`CHECK (role IN ('CUSTOMER', 'DESIGNER'))`)
  as a database-level safety net, in addition to Java enum validation. `quotations.status`
  does the same (`CHECK (status IN ('DRAFT', 'SENT', 'ACCEPTED', 'REJECTED'))`).
- Indexes exist on frequently-filtered columns: `designs.category_id`, `designs.designer_id`,
  `designs.style_tag`, `bookings.customer_id`, `bookings.designer_id`, `bookings.status`,
  `quotation_line_items.quotation_id`.
  These make the catalog search and booking list queries fast as data grows.

---

## 6. Entities (JPA Layer)

**What:** An **entity** is a Java class annotated `@Entity` that JPA/Hibernate maps
onto a database table. Each field maps to a column; each instance maps to a row.

**Why:** Instead of writing raw SQL everywhere, we describe the shape of our data once
in Java, and Hibernate generates the SQL for us (via the repository layer — see next
section).

**Example — `User.java`:**

```java
@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    private Role role;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
```

Key annotations explained:
- `@Id` + `@GeneratedValue(strategy = IDENTITY)` — primary key, auto-incremented by
  Postgres (`BIGSERIAL`).
- `@Enumerated(EnumType.STRING)` — stores the enum's **name** (`"CUSTOMER"`) in the
  column, not its ordinal number. This is important: if you ever reorder the enum
  constants, ordinal storage would silently corrupt existing data. String storage is safe.
- `@CreatedDate` / `@LastModifiedDate` + `@EntityListeners(AuditingEntityListener.class)`
  — combined with `@EnableJpaAuditing` on the main application class, Hibernate
  automatically stamps these fields on insert/update. You never set them manually.
- Lombok's `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder` generate
  all the getters/setters/constructors so we don't hand-write boilerplate. `@Builder`
  in particular gives us a fluent way to construct entities: `User.builder().email(...).build()`.

**Relationships between entities:**

- `DesignerProfile.user` — `@OneToOne` pointing back to `User`, with `@JoinColumn(name = "user_id")`.
- `Design.category` and `Design.designer` — `@ManyToOne(fetch = FetchType.LAZY)`. Lazy
  fetch means Hibernate does **not** load the related `Category`/`User` from the
  database until you actually call `.getCategory()` — avoiding unnecessary joins when
  you don't need that data.
- `Booking.customer`, `Booking.designer`, `Booking.design` — all `@ManyToOne(LAZY)`.
  Note `Booking` has **two** `@ManyToOne` relationships to the *same* `User` entity
  (customer and designer) — this is why both are explicitly named via `@JoinColumn`.
- `Quotation.booking` — `@OneToOne`, mirroring `DesignerProfile.user`.
- `Quotation.lineItems` — `@OneToMany(mappedBy = "quotation", cascade = CascadeType.ALL, orphanRemoval = true)`.
  This is a new pattern not used elsewhere in the codebase yet: because a quotation's
  line items have no independent existence (they only make sense as part of one
  quotation), `cascade = ALL` means saving/deleting the `Quotation` automatically
  saves/deletes its `QuotationLineItem` rows too, and `orphanRemoval = true` means
  simply removing an item from the in-memory `lineItems` list (as `QuotationService`
  does when replacing a draft's items) deletes that row from the database — no manual
  `quotationLineItemRepository.delete(...)` call needed. This is why there's no separate
  repository for `QuotationLineItem` at all; it's managed entirely through its parent.

---

## 7. DTOs — Why We Never Return Entities Directly

**What:** DTOs (Data Transfer Objects) are simple, immutable records that define
**exactly** what data goes over the wire — for a request coming in, or a response
going out. In this project, every DTO is a Java `record` (all fields final,
auto-generated `equals`/`hashCode`/`toString`/accessors — perfect for immutable data
shapes).

**Why we never return `@Entity` objects directly from a controller:**

1. **Security** — entities have sensitive fields (e.g. `User.passwordHash`). If you
   serialize the entity straight to JSON, you risk leaking it.
2. **Lazy-loading crashes** — a `@ManyToOne(LAZY)` field like `Design.designer` is a
   Hibernate proxy. Serializing it outside a transaction throws
   `LazyInitializationException`. DTOs sidestep this by only exposing the flattened
   fields you actually fetched (e.g. `designerName: String` instead of the whole `User`).
3. **API stability** — you can freely rename/restructure entity fields without
   breaking the public API contract, as long as the DTO shape stays the same.

**Example — request DTO with validation, `RegisterRequest.java`:**

```java
public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 100) @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$")
        String password,
        @NotBlank @Size(max = 150) String fullName,
        @Size(max = 20) String phone,
        @NotNull Role role
) {}
```

**Example — response DTO, `AuthResponse.java`:**

```java
public record AuthResponse(
        String accessToken,
        String tokenType,
        UserSummary user
) {
    public record UserSummary(Long id, String email, String fullName, Role role) {}

    public static AuthResponse of(String token, Long userId, String email, String fullName, Role role) {
        return new AuthResponse(token, "Bearer", new UserSummary(userId, email, fullName, role));
    }
}
```

Notice the user-identifying fields are grouped under a nested `user` object rather than
sitting flat alongside `accessToken`/`tokenType`. This keeps the response shape
self-documenting — "token stuff" and "who this token belongs to" are visually and
structurally separate — and gives room to add more account fields later without
cluttering the top level. `UserSummary` is declared as a **nested record**, the same
technique used for `ApiErrorResponse.FieldViolation` (see
[Exception Handling Flow](#20-exception-handling-flow)) — a small DTO that only ever
makes sense in the context of its parent.

The `of(...)` static factory method is just a convenience constructor used by
`AuthService` so callers don't need to remember the literal string `"Bearer"`.

**Converting between entity and DTO — Mappers:**

A **mapper** (`UserMapper`, `DesignMapper`, `BookingMapper`) is a small `@Component`
whose only job is translating entity → DTO (and occasionally DTO → entity, though here
that's mostly done inline in the services via `.builder()`). Example from `DesignMapper`:

```java
public DesignResponse toResponse(Design design) {
    return new DesignResponse(
            design.getId(), design.getTitle(), design.getDescription(),
            toCategoryResponse(design.getCategory()),
            design.getDesigner().getId(), design.getDesigner().getFullName(),
            design.getCoverImageUrl(), design.getPriceEstimate(),
            design.getStyleTag(), design.getCreatedAt()
    );
}
```

Note it calls `design.getDesigner().getFullName()` — this triggers the lazy load
**while still inside the service's `@Transactional` method**, which is why it works
safely here (see [Transactions](#14-full-request-lifecycle-controller--service--repository--db)).

---

## 8. Repositories (Data Access Layer)

**What:** A repository is a Java **interface** extending Spring Data JPA's
`JpaRepository<EntityType, IdType>`. You don't write an implementation — Spring
generates one at runtime using dynamic proxies.

**Why:** Eliminates hand-written boilerplate CRUD code (`save`, `findById`, `findAll`,
`delete`, ...) entirely. You only write method *signatures* for anything beyond basic
CRUD, and Spring Data parses the method name to build the query.

**Example — `UserRepository.java`:**

```java
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

Spring Data reads `findByEmail` and generates:
`SELECT * FROM users WHERE email = ?` — no implementation needed. This is called a
**derived query method** — the method name itself *is* the query definition.

**Pagination — `BookingRepository.java`:**

```java
Page<Booking> findByCustomerId(Long customerId, Pageable pageable);
```

Passing a `Pageable` (page number + size + sort) makes Spring Data automatically
generate a `LIMIT ... OFFSET ...` query and also run a `COUNT(*)` query to know the
total number of matching rows — both wrapped up in the returned `Page<T>` object
(which has `.getContent()`, `.getTotalElements()`, `.getTotalPages()`, etc.).

**Lookup by a foreign key — `QuotationRepository.java`:**

```java
public interface QuotationRepository extends JpaRepository<Quotation, Long> {
    Optional<Quotation> findByBookingId(Long bookingId);
}
```

Since a quotation is one-to-one with its booking, every quotation lookup in
`QuotationService` goes through the booking id rather than the quotation's own id — the
booking id is the natural key a controller/client actually has on hand (see
[Feature Walkthrough: Quotations](#17-feature-walkthrough-quotations-post-consultation-estimate)).

**Dynamic queries — `DesignSpecifications.java` + `JpaSpecificationExecutor`:**

The design catalog needs *optional* filters (category, search text, style) that can
be combined in any combination. Derived query methods can't express "filter by these
three things, but only if they're provided." For that we use the **Specification**
pattern:

```java
public interface DesignRepository extends JpaRepository<Design, Long>,
        JpaSpecificationExecutor<Design> {
}
```

```java
public static Specification<Design> hasCategory(Long categoryId) {
    return (root, query, cb) -> categoryId == null
            ? null                                            // no filter applied
            : cb.equal(root.get("category").get("id"), categoryId);
}
```

A `Specification<T>` is just a lambda that builds a JPA Criteria predicate. Returning
`null` means "don't filter on this condition at all." `DesignService` combines three
of these with `.and(...)`:

```java
Specification<Design> spec = Specification
        .where(DesignSpecifications.hasCategory(categoryId))
        .and(DesignSpecifications.matchesSearch(search))
        .and(DesignSpecifications.hasStyle(style));

designRepository.findAll(spec, pageable);
```

This builds one SQL query with only the WHERE clauses that were actually requested —
much cleaner than writing a dozen overloaded repository methods for every filter
combination.

---

## 9. Security Architecture (Big Picture)

Before diving into the register/login flow, here's the overall shape of the security
system, since several pieces need to be understood together:

```
                     ┌─────────────────────────────────────────┐
Incoming HTTP        │            Spring Security Filter Chain  │
request  ──────────► │                                           │
                     │  1. CORS check                            │
                     │  2. JwtAuthFilter  ◄── reads Authorization │
                     │     (custom)          header, validates    │
                     │                        JWT, sets            │
                     │                        SecurityContext      │
                     │  3. Authorization check                     │
                     │     (public URL? role required?)            │
                     └───────────────┬───────────────────────────┘
                                     │ if authorized
                                     ▼
                              Your @RestController
```

**Key components and their roles:**

| Component | Role |
|---|---|
| `SecurityConfig` | Declares the rules: which URLs are public, which need auth, CORS policy, password hashing algorithm. |
| `JwtService` | Pure JWT logic: create a token, read claims out of a token, check if a token is still valid. Knows nothing about HTTP. |
| `JwtAuthFilter` | Runs once per request. Pulls the token out of the `Authorization` header, asks `JwtService` if it's valid, and if so tells Spring Security "this request is from this authenticated user." |
| `CustomUserDetailsService` | Given an email, loads the matching `User` from the database and wraps it as a `UserPrincipal`. Used both during login (password check) and by the JWT filter (to reload user details on every request). |
| `UserPrincipal` | Adapts our `User` entity to Spring Security's `UserDetails` interface, without polluting the entity itself with framework-specific code. |
| `RestAuthEntryPoint` | Runs when an **unauthenticated** user hits a protected endpoint. Returns a clean JSON 401 instead of Spring Security's default HTML login page. |
| `RestAccessDeniedHandler` | Runs when an **authenticated but unauthorized** user (wrong role) hits a restricted endpoint. Returns a clean JSON 403. |

**Why stateless (JWT) instead of sessions?**
`SecurityConfig` sets `sessionCreationPolicy(SessionCreationPolicy.STATELESS)`. This
means the server keeps **no session memory** between requests — every request must
prove who it is by presenting a valid JWT. This fits a mobile-app backend well: no
sticky sessions, easy to scale horizontally (any server instance can validate any
token, since the token itself carries the identity + signature).

---

## 10. Authentication Flow — Register

**Endpoint:** `POST /api/auth/register` (public — no token needed)

**Step by step:**

1. **Controller** (`AuthController.register`) receives the JSON body, and Spring
   automatically deserializes it into a `RegisterRequest` record.
   `@Valid` triggers Bean Validation on the fields (see [Validation](#19-validation-flow))
   *before* the method body even runs. If validation fails, the method is never called
   — Spring throws `MethodArgumentNotValidException` instead (caught globally, see
   [Exception Handling](#20-exception-handling-flow)).

2. **Controller delegates to `AuthService.register(request)`.** Controllers should
   never contain business logic — their only job is: deserialize input, call the
   service, wrap the result in an HTTP response.

3. **`AuthService.register`:**
   ```java
   String normalizedEmail = request.email().trim().toLowerCase();

   if (userRepository.existsByEmail(normalizedEmail)) {
       throw new DuplicateResourceException("An account with this email already exists");
   }
   ```
   Email is normalized (trimmed + lowercased) so `Test@Example.com` and
   `test@example.com` are treated as the same account. Duplicate check happens first —
   throwing a custom exception that the global handler turns into `409 Conflict`.

4. **Password hashing.**
   ```java
   .passwordHash(passwordEncoder.encode(request.password()))
   ```
   `passwordEncoder` is a `BCryptPasswordEncoder` bean (defined in `SecurityConfig`).
   **We never store the raw password** — BCrypt is a one-way hash algorithm
   specifically designed for passwords (slow by design, to resist brute-forcing, and
   includes a random salt automatically).

5. **Save the user.** `userRepository.save(user)` — since `id` is `null` on a new
   entity, JPA knows to `INSERT` rather than `UPDATE`, and Postgres assigns the next
   auto-increment id.

6. **If the role is `DESIGNER`, also create a `DesignerProfile` row** (empty, to be
   filled in later via the profile-update endpoint):
   ```java
   if (user.getRole() == Role.DESIGNER) {
       DesignerProfile profile = DesignerProfile.builder().user(user).build();
       designerProfileRepository.save(profile);
   }
   ```
   Customers never get a `DesignerProfile` row — this is why the `designerProfile`
   field in profile responses is `null`/absent for customers (see
   [User Profile Walkthrough](#18-feature-walkthrough-user-profile-role-conditional-data)).

7. **Generate a JWT immediately** so the new user is logged in right after
   registering (no separate login step required):
   ```java
   UserPrincipal principal = UserPrincipal.fromEntity(user);
   String token = jwtService.generateToken(principal);
   ```

8. **Return `201 Created`** with an `AuthResponse` containing the token + basic user info.

```
Client                Controller           AuthService              DB
  │  POST /register      │                     │                    │
  ├──────────────────────►                     │                    │
  │                       │  register(request)  │                   │
  │                       ├─────────────────────►                   │
  │                       │                     │ existsByEmail?    │
  │                       │                     ├───────────────────►
  │                       │                     │◄───────────────────
  │                       │                     │ encode password   │
  │                       │                     │ save(user)        │
  │                       │                     ├───────────────────►
  │                       │                     │◄───────────────────
  │                       │                     │ generateToken()   │
  │                       │◄─────────────────────                   │
  │◄──────────────────────  201 + AuthResponse                      │
```

---

## 11. Authentication Flow — Login

**Endpoint:** `POST /api/auth/login` (public)

```java
public AuthResponse login(LoginRequest request) {
    String normalizedEmail = request.email().trim().toLowerCase();

    authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(normalizedEmail, request.password()));

    User user = userRepository.findByEmail(normalizedEmail)
            .orElseThrow(() -> new IllegalStateException("User authenticated but not found: " + normalizedEmail));

    UserPrincipal principal = UserPrincipal.fromEntity(user);
    String token = jwtService.generateToken(principal);

    return AuthResponse.of(token, user.getId(), user.getEmail(), user.getFullName(), user.getRole());
}
```

**What's happening under the hood when `authenticationManager.authenticate(...)` is called:**

1. Spring Security's `AuthenticationManager` delegates to the `DaoAuthenticationProvider`
   bean (configured in `SecurityConfig`).
2. That provider calls `CustomUserDetailsService.loadUserByUsername(email)` to fetch
   the user from the DB and wrap it as a `UserPrincipal`.
3. It then compares the raw password from the request against `userPrincipal.getPassword()`
   (the BCrypt hash) using the same `PasswordEncoder` — `passwordEncoder.matches(raw, hash)`.
4. If they don't match, it throws `BadCredentialsException` — which propagates up out
   of `login()`, out of the controller, and is caught by `GlobalExceptionHandler`,
   which returns `401 Unauthorized` with the message *"Invalid email or password"*
   (deliberately vague — never reveal whether the email or the password was wrong,
   to avoid helping attackers enumerate valid accounts).
5. If they match, authentication succeeds silently (no exception), and `login()`
   continues to generate a fresh JWT.

**Why not just do the password check manually?** Delegating to
`AuthenticationManager`/`DaoAuthenticationProvider` reuses Spring Security's
well-tested, timing-attack-resistant comparison logic, and keeps the auth logic
consistent with the rest of the framework (rather than reinventing it).

---

## 12. How a Protected Request Is Authenticated (JWT Filter)

Once a client has a token from register/login, every subsequent request to a
protected endpoint must include it:

```
Authorization: Bearer eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJ0ZXN0...
```

**`JwtAuthFilter`** (extends `OncePerRequestFilter`, guaranteeing it runs exactly once
per request no matter how the servlet container dispatches it) does this on **every**
incoming HTTP request, before it reaches any controller:

```java
String authHeader = request.getHeader("Authorization");

if (authHeader == null || !authHeader.startsWith("Bearer ")) {
    filterChain.doFilter(request, response);   // no token -> just continue,
    return;                                     // let the authorization rules decide
}

String token = authHeader.substring("Bearer ".length());

try {
    String email = jwtService.extractEmail(token);

    if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);

        if (jwtService.isTokenValid(token, userDetails.getUsername())) {
            var authToken = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }
    }
} catch (JwtException | IllegalArgumentException ex) {
    // invalid/expired token -> leave request unauthenticated, let the entry point 401 it
}

filterChain.doFilter(request, response);
```

Step by step:

1. **No `Authorization` header, or it doesn't start with `Bearer `?** → skip straight
   to the next filter. This request will be treated as **anonymous**. If the endpoint
   requires auth, it'll get rejected later by the authorization rules (→ 401 via
   `RestAuthEntryPoint`). If the endpoint is public (e.g. `/api/designs`), it proceeds fine.

2. **Extract the email from the token's claims** (`JwtService.extractEmail` — reads
   the JWT's `subject` claim, which we set to the user's email at token creation time).

3. **Reload the user from the database** via `CustomUserDetailsService`. This is
   important: we don't just trust the token's claims blindly — we re-fetch the current
   user record so that, e.g., a role change or account deletion takes effect
   immediately rather than only once the old token expires.

4. **Validate the token**: `JwtService.isTokenValid` checks the signature already
   verified during parsing, that the subject matches the reloaded user's email, and
   that it hasn't expired.

5. **If valid, tell Spring Security "this request is authenticated"** by placing an
   `Authentication` object into `SecurityContextHolder`. Everything downstream
   (`@PreAuthorize`, `@AuthenticationPrincipal`) reads from this context for the
   remainder of the request.

6. **Any JWT parsing error** (malformed, expired, bad signature) is caught and
   swallowed — the request simply continues as unauthenticated. We don't leak parsing
   error details to the client; the standard 401 response takes over instead.

**`JwtService` — the actual token mechanics:**

```java
public String generateToken(UserPrincipal principal) {
    Instant now = Instant.now();
    Instant expiry = now.plusMillis(jwtProperties.getExpirationMs());

    return Jwts.builder()
            .subject(principal.getUsername())      // the user's email
            .issuer(jwtProperties.getIssuer())      // "velora-backend"
            .claim("userId", principal.getId())
            .claim("role", principal.getRole().name())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .signWith(signingKey())                 // HMAC-SHA signature
            .compact();
}
```

The token is a **signed**, not encrypted, JWT — anyone can decode and read its
contents (it's just base64), but nobody can forge or tamper with it without knowing
the secret key, because `signWith(signingKey())` produces a cryptographic signature
over the payload that `isTokenValid` re-checks on every request
(`Jwts.parser().verifyWith(signingKey())...`). If a single byte of the payload
changes, the signature check fails and parsing throws a `JwtException`.

**How `@AuthenticationPrincipal UserPrincipal principal` works in controllers:**

Once `JwtAuthFilter` has populated `SecurityContextHolder`, Spring MVC can inject the
currently authenticated user directly into any controller method parameter:

```java
@GetMapping("/profile")
public ResponseEntity<UserProfileResponse> getProfile(@AuthenticationPrincipal UserPrincipal principal) {
    return ResponseEntity.ok(userService.getProfile(principal.getId()));
}
```

No manual "get the current user" boilerplate needed — Spring resolves it from the
security context automatically.

---

## 13. Authorization — Roles and `@PreAuthorize`

**Authentication** answers "who are you?" **Authorization** answers "are you allowed
to do this?" This project uses two authorization mechanisms together:

**1. URL-pattern-based rules (`SecurityConfig`)** — coarse-grained, applied to entire
groups of endpoints:

```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()                 // /api/auth/**, swagger, actuator/health
        .requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll() // GET /api/designs/**, GET /api/categories/**
        .anyRequest().authenticated())                                  // everything else needs a valid token
```

Notice `/api/designs/**` is public **only for GET** — anyone can browse designs
without logging in, but (in future, when write endpoints are added) creating/editing a
design would require authentication.

**2. Method-level role checks (`@PreAuthorize`)** — fine-grained, applied to a single
controller method, checked *after* the URL-level rule already confirmed the user is
authenticated:

```java
@PostMapping
@PreAuthorize("hasRole('CUSTOMER')")
public ResponseEntity<BookingResponse> create(...) { ... }
```

```java
@PatchMapping("/{id}/status")
@PreAuthorize("hasRole('DESIGNER')")
public ResponseEntity<BookingResponse> updateStatus(...) { ... }
```

`@EnableMethodSecurity` (in `SecurityConfig`) turns this annotation on globally.
`hasRole('CUSTOMER')` actually checks for the authority `"ROLE_CUSTOMER"` — Spring
Security's convention is to prefix role names with `ROLE_`, which is exactly what
`UserPrincipal.getAuthorities()` produces:

```java
@Override
public List<GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
}
```

**What happens if the role check fails?** Spring Security throws
`AccessDeniedException`, which is caught by `RestAccessDeniedHandler` (registered in
`SecurityConfig` as the `accessDeniedHandler`) and turned into a clean JSON `403
Forbidden` response — never Spring's default HTML error page.

**A third layer of authorization — ownership checks in the service layer.** Role
checks alone aren't enough for some rules (e.g. "a customer can only cancel *their
own* booking, not anyone's booking"). This kind of check can't be expressed as a
simple role annotation because it depends on data, not just role — so it lives inside
`BookingService`:

```java
public BookingResponse cancel(Long customerId, Long bookingId) {
    Booking booking = findBooking(bookingId);

    if (!booking.getCustomer().getId().equals(customerId)) {
        throw new UnauthorizedActionException("Only the customer who made this booking can cancel it");
    }
    ...
}
```

So the full authorization picture for booking-cancellation is: *authenticated* (JWT
filter) → *has the CUSTOMER role* (`@PreAuthorize`) → *owns this specific booking*
(service-layer check). Three layers, each catching a different kind of mistake.

---

## 14. Full Request Lifecycle (Controller → Service → Repository → DB)

Let's trace one complete authenticated request end-to-end:
**`GET /api/users/profile`** with a valid `Authorization: Bearer <token>` header.

```
1. HTTP request arrives at embedded Tomcat
        │
        ▼
2. Spring Security filter chain
     - CORS filter checks Origin header
     - JwtAuthFilter reads token, validates it, populates SecurityContext
     - Authorization check: "/api/users/**" isn't in PUBLIC_ENDPOINTS -> requires authentication
       (SecurityContext has a valid Authentication -> passes)
        │
        ▼
3. Spring MVC dispatches to UserController.getProfile(...)
     - @AuthenticationPrincipal resolves the current UserPrincipal from SecurityContext
        │
        ▼
4. UserController calls userService.getProfile(principal.getId())
        │
        ▼
5. UserService.getProfile (annotated @Transactional(readOnly = true))
     - Opens a DB transaction
     - Calls userRepository.findById(userId) -> throws ResourceNotFoundException if absent
     - Role check: only fetch DesignerProfile if role == DESIGNER
     - Calls userMapper.toProfileResponse(user, designerProfile)
     - Commits (read-only) transaction
        │
        ▼
6. UserMapper builds a UserProfileResponse record from entity fields
        │
        ▼
7. Back in UserController: ResponseEntity.ok(response)
        │
        ▼
8. Spring MVC serializes the DTO record to JSON (via Jackson)
        │
        ▼
9. HTTP 200 response sent back to the client
```

**Why `@Transactional` matters here:** JPA lazy-loaded fields (like
`DesignerProfile.user`, or any `@ManyToOne`) can only be fetched *while a database
session/transaction is still open*. `@Transactional` on the service method keeps that
session open for the whole method body — so by the time the mapper calls something
like `design.getDesigner().getFullName()`, the lazy proxy can still reach the database
to fill itself in. Once the method returns and the transaction closes, trying to touch
an unloaded lazy field would throw `LazyInitializationException`. This is precisely
why entity-to-DTO mapping always happens **inside** the service layer, never in the
controller.

`readOnly = true` is a hint to Hibernate that no writes will happen in this
transaction, letting it apply some performance optimizations (e.g. skipping dirty
checking).

---

## 15. Feature Walkthrough: Design Catalog (Search/Filter/Pagination)

**Purpose:** Let customers (and anonymous visitors — this is a **public** endpoint)
browse, search, and filter interior designs.

**Endpoint:** `GET /api/designs?category=2&search=modern&style=minimalist&page=0&size=20&sortBy=priceEstimate&direction=asc`

**Flow:**

1. `DesignController.search` receives all query params (all optional except paging
   defaults). It clamps `size` to a maximum of 50 (`MAX_PAGE_SIZE`) so a client can't
   request an absurdly large page and hammer the database:
   ```java
   int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
   ```
2. It whitelists the sortable fields to prevent sorting by arbitrary/unsafe column
   names being injected via the `sortBy` param:
   ```java
   private String sanitizeSortField(String sortBy) {
       return switch (sortBy) {
           case "priceEstimate", "title", "createdAt" -> sortBy;
           default -> "createdAt";
       };
   }
   ```
3. Builds a `Pageable` (page number, size, sort direction+field) and calls
   `designService.search(category, search, style, pageable)`.
4. `DesignService` combines three `Specification<Design>` predicates (see
   [Repositories](#8-repositories-data-access-layer)) — category filter, free-text
   search (title/description `LIKE`), and style-tag filter — each of which is a no-op
   if its corresponding parameter is `null`/blank.
5. `designRepository.findAll(spec, pageable)` runs one SQL query combining whichever
   filters were actually provided, applies `LIMIT`/`OFFSET` for the page, and a
   separate `COUNT` query for the total.
6. Each `Design` entity is mapped to a lightweight `DesignSummaryResponse` (just the
   catalog-card fields — id, title, category name, cover image, price, style — not the
   full description) to keep list responses small.
7. The `Page<DesignSummaryResponse>` is wrapped into a `PageResponse<T>` — a generic
   record exposing `content`, `page`, `size`, `totalElements`, `totalPages`, `last` —
   so every paginated endpoint in the API (designs, bookings) returns the exact same
   envelope shape, regardless of what's inside `content`.

**Getting one design's full detail:** `GET /api/designs/{id}` uses the fuller
`DesignResponse` DTO (includes description, full category object, designer name),
via `designService.getById(id)`, throwing `ResourceNotFoundException` → `404` if the
id doesn't exist.

---

## 16. Feature Walkthrough: Bookings (State Machine)

**Purpose:** Let a customer book a consultation with a designer (optionally tied to a
specific design), and let both sides manage the booking's lifecycle.

**Booking states:** `PENDING → CONFIRMED → COMPLETED`, with `CANCELLED` reachable from
either `PENDING` or `CONFIRMED`. Once `CANCELLED` or `COMPLETED`, a booking is frozen
— no further transitions allowed.

```
        ┌─────────┐   designer confirms    ┌───────────┐   designer marks done   ┌───────────┐
        │ PENDING │────────────────────────►│ CONFIRMED │────────────────────────►│ COMPLETED │
        └────┬────┘                        └─────┬─────┘                        └───────────┘
             │  customer cancels                  │  customer cancels
             │  (or designer rejects)              │
             ▼                                    ▼
        ┌───────────┐                        ┌───────────┐
        │ CANCELLED │◄───────────────────────┤ CANCELLED │   (same terminal state)
        └───────────┘                        └───────────┘
```

**Creating a booking** — `POST /api/bookings`, restricted to `@PreAuthorize("hasRole('CUSTOMER')")`:

```java
public BookingResponse createBooking(Long customerId, BookingRequest request) {
    User customer = userRepository.findById(customerId)...
    User designer = userRepository.findById(request.designerId())...

    if (designer.getRole() != Role.DESIGNER) {
        throw new IllegalArgumentException("Selected user is not a designer");
    }

    Design design = null;
    if (request.designId() != null) {
        design = designRepository.findById(request.designId())...
        if (!design.getDesigner().getId().equals(designer.getId())) {
            throw new IllegalArgumentException("The selected design does not belong to the selected designer");
        }
    }

    Booking booking = Booking.builder()
            .customer(customer).designer(designer).design(design)
            .scheduledAt(request.scheduledAt())
            .status(BookingStatus.PENDING)   // always starts PENDING
            .notes(request.notes())
            .build();

    return bookingMapper.toResponse(bookingRepository.save(booking));
}
```

Notice the extra business-rule validation that can't be expressed via `@Valid`
annotations alone (they require cross-referencing the database): confirming the
target user is actually a designer, and if a specific design was chosen, confirming
it actually belongs to that designer.

**The state machine itself** — `validateTransition`:

```java
private void validateTransition(BookingStatus current, BookingStatus next) {
    boolean allowed = switch (current) {
        case PENDING -> next == BookingStatus.CONFIRMED || next == BookingStatus.CANCELLED;
        case CONFIRMED -> next == BookingStatus.COMPLETED || next == BookingStatus.CANCELLED;
        case CANCELLED, COMPLETED -> false;   // terminal states, no transitions out
    };

    if (!allowed) {
        throw new InvalidStateTransitionException("Cannot transition booking from " + current + " to " + next);
    }
}
```

This uses a Java **switch expression** (not statement) — each `case` *returns* a
value directly, and the compiler enforces all enum cases are handled (exhaustiveness
checking), so if `BookingStatus` ever gets a new value, this code won't compile until
someone updates the switch. `InvalidStateTransitionException` → mapped to `409
Conflict` by the global handler.

**Who can do what:**

| Action | Endpoint | Role required | Extra ownership check |
|---|---|---|---|
| Create booking | `POST /api/bookings` | CUSTOMER | — |
| List my bookings | `GET /api/bookings` | any authenticated | filtered by `principal.getId()` — customers see bookings where they're the customer, designers see ones where they're the designer (`BookingService.getBookingsForUser` branches on `role`) |
| Get one booking | `GET /api/bookings/{id}` | any authenticated | must be the customer *or* the designer on that booking (`assertParticipant`) |
| Cancel | `PATCH /api/bookings/{id}/cancel` | CUSTOMER | must be *the* customer on that booking, and it must still be cancellable |
| Update status | `PATCH /api/bookings/{id}/status` | DESIGNER | must be *the* designer on that booking, and the transition must be valid |

---

## 17. Feature Walkthrough: Quotations (Post-Consultation Estimate)

**Purpose:** A `Booking` only gets a designer and customer talking — it carries no
price. A **quotation** is the artifact that turns that consultation into a concrete,
itemized scope of work with a total cost, which the customer then explicitly accepts
or rejects. This was added specifically because nothing in the original booking flow
captured "what was actually agreed" — without it, a confirmed booking had no record of
what it would cost or what it covered.

**Data shape:** one `Quotation` per `Booking` (`booking_id UNIQUE` — no revision
history in this version), holding a list of `QuotationLineItem`s (`description`,
optional `quantity`/`unit`/`unitPrice` for a fully itemized row, and a required
`amount`). `totalAmount` on the quotation is always the sum of its line items'
`amount`s, recomputed server-side every time the line items change — never trusted
from client input.

**State machine:** `DRAFT → SENT → ACCEPTED` or `DRAFT → SENT → REJECTED`. Unlike
`BookingStatus`, there's no branch back to an editable state — once `SENT`, the
designer can no longer edit it, and once `ACCEPTED`/`REJECTED`, it's terminal (same
"no way out of a terminal state" philosophy as `BookingStatus.CANCELLED`/`COMPLETED`).

```
        ┌───────┐  designer sends   ┌──────┐  customer accepts  ┌──────────┐
        │ DRAFT │──────────────────►│ SENT │───────────────────►│ ACCEPTED │
        └───────┘                   └──┬───┘                    └──────────┘
     (designer can keep                 │  customer rejects
      editing line items                ▼
      while still DRAFT)           ┌──────────┐
                                    │ REJECTED │
                                    └──────────┘
```

**Endpoints** — all nested under the booking they belong to
(`/api/bookings/{bookingId}/quotation`), following the same nested-resource pattern
`BookingController` uses for `/{id}/cancel` and `/{id}/status`:

| Action | Endpoint | Role required | Extra ownership check |
|---|---|---|---|
| Save/replace draft | `PUT /api/bookings/{bookingId}/quotation` | DESIGNER | must be *the* designer assigned to that booking; quotation must currently be `DRAFT` (or not exist yet) |
| Send to customer | `POST /api/bookings/{bookingId}/quotation/send` | DESIGNER | same designer check; quotation must be `DRAFT` and have at least one line item |
| Get quotation | `GET /api/bookings/{bookingId}/quotation` | any authenticated | must be the customer *or* the designer on that booking |
| Accept | `PATCH /api/bookings/{bookingId}/quotation/accept` | CUSTOMER | must be *the* customer on that booking; quotation must be `SENT` |
| Reject | `PATCH /api/bookings/{bookingId}/quotation/reject` | CUSTOMER | must be *the* customer on that booking; quotation must be `SENT` |

**Saving a draft — `QuotationService.saveDraft`:**

```java
Quotation quotation = quotationRepository.findByBookingId(bookingId)
        .orElseGet(() -> Quotation.builder()
                .booking(booking).status(QuotationStatus.DRAFT).totalAmount(BigDecimal.ZERO).build());

if (quotation.getId() != null && quotation.getStatus() != QuotationStatus.DRAFT) {
    throw new InvalidStateTransitionException(
            "Cannot edit a quotation once it has been sent, accepted, or rejected");
}

quotation.setNotes(request.notes());
replaceLineItems(quotation, request.lineItems());   // clears + rebuilds the list, recomputes totalAmount
```

Two things worth noticing:
1. `PUT` here means **create-or-replace-the-whole-draft**, not a partial update like
   `UserService.updateProfile`. Every call sends the *complete* line-item list; the
   service clears the existing list and rebuilds it (`quotation.getLineItems().clear()`
   then re-adds), relying on `orphanRemoval = true` (see [Entities](#6-entities-jpa-layer))
   to actually delete the old rows. This is simpler to reason about than diffing
   old-vs-new line items, at the cost of the client always resending the full list.
2. The `if (quotation.getId() != null && status != DRAFT)` check is what makes a
   sent/accepted/rejected quotation immutable — same defensive pattern as
   `BookingService.validateTransition` refusing to move a booking out of a terminal state.

**Sending — `QuotationService.send`:**

```java
if (quotation.getStatus() != QuotationStatus.DRAFT) {
    throw new InvalidStateTransitionException("Only a draft quotation can be sent");
}
if (quotation.getLineItems().isEmpty()) {
    throw new IllegalArgumentException("Cannot send a quotation with no line items");
}
quotation.setStatus(QuotationStatus.SENT);
```

The empty-line-items check is a good example of a rule that can't live in Bean
Validation on the DTO (see [Validation Flow](#19-validation-flow)) — `SaveQuotationRequest`
deliberately allows an empty `lineItems` list so a designer can save an in-progress
draft with nothing in it yet, but *sending* an empty quotation to a customer would be
meaningless, so that check only fires here, at the point where it actually matters.

**Accepting/rejecting — `QuotationService.respond` (shared by both):**

```java
if (!quotation.getBooking().getCustomer().getId().equals(customerId)) {
    throw new UnauthorizedActionException("Only the customer on this booking can respond to its quotation");
}
if (quotation.getStatus() != QuotationStatus.SENT) {
    throw new InvalidStateTransitionException("Only a sent quotation can be accepted or rejected");
}
quotation.setStatus(newStatus);   // ACCEPTED or REJECTED
```

Both `accept` and `reject` funnel through this one private method with just the target
status swapped — since the ownership check and the "must currently be SENT" check are
identical for both actions, duplicating them across two methods would just be two
copies of the same bug waiting to diverge.

**No new exception types, no new `GlobalExceptionHandler` entries.** The whole feature
reuses `ResourceNotFoundException` (404 — booking or quotation doesn't exist),
`UnauthorizedActionException` (403 — wrong participant), `InvalidStateTransitionException`
(409 — illegal status move), and plain `IllegalArgumentException` (400 — empty line
items on send), exactly the same set `BookingService` already relies on (see
[Exception Handling Flow](#20-exception-handling-flow)). New features in this codebase
default to reusing the existing exception vocabulary rather than inventing new ones.

---

## 18. Feature Walkthrough: User Profile (Role-Conditional Data)

**Purpose:** Every user (customer or designer) can view/update their own profile via
one shared endpoint pair — `GET /api/users/profile` and `PUT /api/users/profile` —
rather than having separate customer-profile and designer-profile endpoints.

**Why one shared response shape works for both roles:**

```java
public record UserProfileResponse(
        Long id, String email, String fullName, String phone, Role role,
        Instant createdAt,
        DesignerProfileResponse designerProfile   // null for customers
) {}
```

`UserService.getProfile` only bothers looking up a `DesignerProfile` row **if** the
user's role is `DESIGNER`:

```java
DesignerProfile designerProfile = user.getRole() == Role.DESIGNER
        ? designerProfileRepository.findByUserId(userId).orElse(null)
        : null;
```

For a customer, `designerProfile` is always `null`. Because `application.yml` sets:

```yaml
spring.jackson.default-property-inclusion: non_null
```

Jackson (the JSON serializer) **omits any field whose value is `null`** entirely from
the JSON output — so a customer's profile response simply doesn't contain a
`designerProfile` key at all, rather than including a confusing `"designerProfile": null`.
This is why the same DTO shape is safe and clean to reuse for both roles, instead of
needing two parallel DTOs.

**Updating a profile — partial updates:**

```java
public record UpdateProfileRequest(
        @Size(max = 150) String fullName, @Size(max = 20) String phone,
        @Size(max = 2000) String bio, @Min(0) @Max(80) Integer yearsExperience,
        @Size(max = 150) String specialization, @Size(max = 100) String city
) {}
```

Every field is optional (no `@NotBlank`/`@NotNull`) — the client only sends the fields
it wants to change. `UserService.updateProfile` checks each one individually
before applying it:

```java
if (request.fullName() != null && !request.fullName().isBlank()) {
    user.setFullName(request.fullName().trim());
}
if (request.phone() != null) {
    user.setPhone(request.phone());
}
```

and only touches designer-specific fields (`bio`, `yearsExperience`, `specialization`,
`city`) if the user is actually a `DESIGNER` — creating their `DesignerProfile` row
lazily on first update if one doesn't already exist (`orElseGet(() -> DesignerProfile.builder()...)`).
This means a customer sending `bio` in the request body has it silently ignored — it's
simply never read, because the whole designer-fields block is skipped for their role.

---

## 19. Validation Flow

**What:** Jakarta Bean Validation (the `@NotBlank`, `@Email`, `@Size`, `@Pattern`,
`@Min`, `@Max`, `@Future`, `@NotNull` annotations you see on DTO fields) is a
standard Java spec for declaring data constraints directly on the data class, instead
of writing manual `if` checks in every controller.

**Why:** Keeps validation rules colocated with the field they constrain (self-documenting),
and lets Spring run them **automatically** before your code ever executes — you never
see invalid data inside a service method.

**How it's wired up:**

```java
@PostMapping("/register")
public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
```

- `@RequestBody` tells Spring MVC to deserialize the incoming JSON into a
  `RegisterRequest`.
- `@Valid` tells Spring to run Bean Validation on that object **immediately
  afterward**, before invoking the method body.
- If any constraint fails, Spring throws `MethodArgumentNotValidException` — the
  controller method body **never runs** — and the exception is caught globally (see
  next section).

**Example constraint from `RegisterRequest`:**

```java
@NotBlank @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
@Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
        message = "Password must contain at least one letter and one digit")
String password
```

Three stacked constraints on one field: not blank, length range (with a custom error
message), and a regex requiring at least one letter and one digit (a lookahead-based
pattern — reads as "the whole string must contain a letter somewhere *and* a digit
somewhere").

**Beyond field-level validation:** some rules can't be expressed as annotations
because they depend on other data (e.g. "does this designer id actually belong to a
DESIGNER role user?", "does this email already exist?"). Those live as explicit checks
inside the **service layer**, throwing custom exceptions like
`DuplicateResourceException` or plain `IllegalArgumentException` — see next section
for how those become HTTP responses too.

---

## 20. Exception Handling Flow

**What:** `GlobalExceptionHandler`, annotated `@RestControllerAdvice`, is a single
class that intercepts exceptions thrown **anywhere** in any controller/service call
chain and converts them into a consistent JSON error shape — so you never write
try/catch blocks in individual controllers.

**Why:** Without this, an unhandled exception would either leak a raw stack trace to
the client, or return Spring Boot's generic whitelabel error page — neither of which
is useful for an API consumer (a mobile app, in this case).

**The universal error shape — `ApiErrorResponse`:**

```java
public record ApiErrorResponse(
        Instant timestamp, int status, String error, String message,
        String path, List<FieldViolation> fieldErrors
) {
    public record FieldViolation(String field, String message) {}
}
```

Every error response — no matter what went wrong — has this exact shape. `fieldErrors`
is only populated for validation failures (see below); otherwise it's `null` (and, per
the Jackson config, omitted from the JSON entirely).

**The mapping table** (`GlobalExceptionHandler`'s `@ExceptionHandler` methods):

| Exception thrown | HTTP status | When it happens |
|---|---|---|
| `ResourceNotFoundException` | 404 Not Found | Looking up a user/design/booking by id that doesn't exist |
| `DuplicateResourceException` | 409 Conflict | Registering with an email that's already taken |
| `UnauthorizedActionException` | 403 Forbidden | Acting on a resource you don't own (e.g. cancelling someone else's booking) |
| `InvalidStateTransitionException` | 409 Conflict | Illegal booking status change (e.g. CONFIRMED → PENDING) |
| `BadCredentialsException` (Spring Security) | 401 Unauthorized | Wrong email/password at login |
| `AccessDeniedException` (Spring Security) | 403 Forbidden | `@PreAuthorize` role check failed |
| `DataIntegrityViolationException` (Spring/JPA) | 409 Conflict | A DB constraint was violated (e.g. race-condition duplicate) |
| `MethodArgumentNotValidException` | 400 Bad Request | `@Valid` bean validation failed — includes a `fieldErrors` list, one entry per invalid field |
| `IllegalArgumentException` | 400 Bad Request | Ad-hoc business-rule violations thrown directly in services (e.g. "Selected user is not a designer") |
| Anything else (`Exception`) | 500 Internal Server Error | Unexpected/unmapped errors — message is deliberately generic ("An unexpected error occurred") so internal details never leak to clients |

**Example — how a validation failure becomes a response:**

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
    List<ApiErrorResponse.FieldViolation> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> new ApiErrorResponse.FieldViolation(fe.getField(), messageOf(fe)))
            .toList();
    return build(HttpStatus.BAD_REQUEST, "Validation failed", req, fieldErrors);
}
```

If you register with a too-short password and a missing email, the client receives:

```json
{
  "timestamp": "2026-07-31T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/auth/register",
  "fieldErrors": [
    { "field": "email", "message": "must not be blank" },
    { "field": "password", "message": "Password must be between 8 and 100 characters" }
  ]
}
```

— one entry per failing field, exactly matching the `@Size(message = "...")` you wrote
on the DTO.

**Two layers of 401/403 handling, and why both exist:** You'll notice 401/403 are
produced in *two* different places — `GlobalExceptionHandler` (for exceptions thrown
during normal request processing, e.g. bad login credentials) **and**
`RestAuthEntryPoint`/`RestAccessDeniedHandler` (for failures that happen *inside the
security filter chain itself*, before the request ever reaches a controller — e.g. no
token at all, or role check failure). Spring's exception-handling advice
(`@RestControllerAdvice`) only intercepts exceptions from the MVC dispatch process; it
cannot catch something that Spring Security's filters already handled and short-circuited
before reaching MVC. That's why both exist, and why they're written to produce the
exact same `ApiErrorResponse` JSON shape — so from the client's point of view, there's
no visible difference in error format regardless of which layer caught the problem.

---

## 21. API Documentation (Swagger/OpenAPI)

**What:** `springdoc-openapi-starter-webmvc-ui` automatically scans all
`@RestController` classes/methods and generates a live, interactive API
specification — no manually-maintained documentation file to go stale.

**Why:** Lets any developer (or the mobile app team) explore and test every endpoint
directly from a browser, see exact request/response shapes, and try calls with a real
JWT — all generated from the actual code, so it can never drift out of sync with
reality.

**Where to find it (once the app is running):**
- Interactive UI: `http://localhost:8080/swagger-ui/index.html` (or `/swagger-ui.html`)
- Raw OpenAPI JSON spec: `http://localhost:8080/v3/api-docs`

**How endpoints get documented:** `@Tag` on the controller class groups related
endpoints together in the UI; `@Operation(summary = "...")` on each method gives it a
human-readable description. Example from `AuthController`:

```java
@Tag(name = "Auth", description = "Registration and login")
public class AuthController {
    @PostMapping("/register")
    @Operation(summary = "Register a new customer or designer account")
    public ResponseEntity<AuthResponse> register(...) { ... }
}
```

**Authenticating in Swagger UI:** `OpenApiConfig` registers a `bearerAuth` security
scheme, which adds an "Authorize" button in the Swagger UI. After logging in via
`/api/auth/login` (through the UI itself), paste the returned `accessToken` into that
button, and every subsequent "Try it out" call in the UI automatically includes the
`Authorization: Bearer <token>` header — letting you test protected endpoints directly
in the browser.

Both `/swagger-ui/**` and `/v3/api-docs/**` are listed in `SecurityConfig`'s
`PUBLIC_ENDPOINTS`, so anyone can view the documentation without a token (only
*calling* protected endpoints through it requires one).

---

## 22. Environment & Secrets Management

**Why environment variables instead of hardcoded config:** Database credentials, JWT
signing secrets, and CORS origins differ between local development, staging, and
production. Hardcoding any of them into `application.yml` would mean committing
secrets to source control (a serious security risk) and would make it impossible to
run the same build against different environments.

**How it works:**

```yaml
datasource:
  url: ${DB_URL:jdbc:postgresql://localhost:5432/velora}
  username: ${DB_USERNAME:velora}
  password: ${DB_PASSWORD:velora}
```

The `${ENV_VAR:default}` syntax means: *use the environment variable if it's set,
otherwise fall back to the value after the colon.* This lets local development "just
work" with sensible defaults while production can override every value via real
environment variables.

**`.env` file + `spring-dotenv`:** Rather than requiring every developer to manually
`export` a dozen environment variables before running the app, this project uses the
`spring-dotenv` library (added to `pom.xml`), which automatically loads a `.env` file
from the project root at startup and exposes its key-value pairs as Spring properties
— exactly as if they'd been exported as real OS environment variables. `.env` is
listed in `.gitignore`, so real secrets never get committed; `.env.example` documents
which variables are expected (with placeholder/dummy values) so a new developer knows
exactly what to fill in.

**Currently configured for this project:** the database points at a **Neon**-hosted
Postgres instance (`DB_URL=jdbc:postgresql://<neon-host>/neondb?sslmode=require`) —
Neon requires SSL for all connections, hence `sslmode=require` in the URL. This
replaced an earlier local Docker/Postgres setup that was removed once Neon was
adopted, to avoid local port conflicts with any locally-installed Postgres instance.

**The `local` Spring profile (`application-local.yml`):** activated by setting
`spring.profiles.active=local` (or `-Dspring-boot.run.profiles=local` when running via
Maven). It layers on top of `application.yml` and currently: provides a safe built-in
dev-only JWT secret (so you're not forced to generate one just to run the app
locally), and bumps logging to `DEBUG` (including raw SQL logging via
`org.hibernate.SQL: DEBUG`) for easier local troubleshooting.

---

## 23. Quick Reference: All Endpoints

| Method | Path | Auth required | Role restriction | Purpose |
|---|---|---|---|---|
| POST | `/api/auth/register` | No | — | Create a new account (customer or designer), returns a JWT |
| POST | `/api/auth/login` | No | — | Authenticate with email/password, returns a JWT |
| GET | `/api/users/profile` | Yes | — | Get the current user's own profile |
| PUT | `/api/users/profile` | Yes | — | Update the current user's own profile (partial update) |
| GET | `/api/designs` | No | — | Search/browse the design catalog (paginated, filterable) |
| GET | `/api/designs/{id}` | No | — | Get one design's full details |
| GET | `/api/categories` | No | — | List all design categories |
| POST | `/api/bookings` | Yes | CUSTOMER | Book a consultation with a designer |
| GET | `/api/bookings` | Yes | — | List the current user's bookings (customer or designer view) |
| GET | `/api/bookings/{id}` | Yes | — | Get one booking's details (must be a participant) |
| PATCH | `/api/bookings/{id}/cancel` | Yes | CUSTOMER | Cancel your own booking (only if PENDING/CONFIRMED) |
| PATCH | `/api/bookings/{id}/status` | Yes | DESIGNER | Advance a booking's status (only if you're the assigned designer) |
| PUT | `/api/bookings/{bookingId}/quotation` | Yes | DESIGNER | Create or replace a draft quotation for a booking (assigned designer only) |
| POST | `/api/bookings/{bookingId}/quotation/send` | Yes | DESIGNER | Send a draft quotation to the customer |
| GET | `/api/bookings/{bookingId}/quotation` | Yes | — | Get the quotation for a booking (must be a participant) |
| PATCH | `/api/bookings/{bookingId}/quotation/accept` | Yes | CUSTOMER | Accept a sent quotation |
| PATCH | `/api/bookings/{bookingId}/quotation/reject` | Yes | CUSTOMER | Reject a sent quotation |
| GET | `/actuator/health` | No | — | Health check (used to confirm the app is up) |
| GET | `/swagger-ui/index.html` | No | — | Interactive API documentation |
| GET | `/v3/api-docs` | No | — | Raw OpenAPI JSON spec |

---

*This document reflects the implementation as of the MVP 1 milestone. As new features
are added (design uploads, reviews, payments, etc.), extend this file section by
section rather than starting a new one, so it stays the single source of truth for
onboarding and self-review.*
