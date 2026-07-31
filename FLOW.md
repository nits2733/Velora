# Velora Backend — End-to-End Flow

A plain-language walkthrough of how the whole system works, start to finish, with no code.

---

## 1. Startup Flow

1. The application process starts.
2. Configuration values load from environment variables — normally supplied automatically from a local `.env` file, so nobody has to set them by hand. This includes the database connection details, the JWT signing secret, the token expiry time, which frontend origins are allowed to call the API, and which network port to listen on.
3. A safety check runs on the JWT secret before anything else happens: if it's missing entirely, or too short to be cryptographically safe, startup is aborted right there with a clear error. The reasoning is deliberate — it's far safer to refuse to start than to start up and silently issue tokens signed with a weak or empty key.
4. Once configuration is confirmed valid, the app connects to the database and hands control to the migration tool (Flyway). Flyway keeps a running history of every schema change ("migration") that has ever been applied to this database. It compares that history against the migration files bundled with the app and applies any that haven't run yet, in strict order. This is what actually creates the tables the first time, and would apply any future schema changes the same way in every environment — laptop, staging, production — so they never drift apart.
5. After migrations are applied, the data-access layer double-checks that the Java-side definitions of each table match what's actually in the database (right columns, right types, right constraints). It does not create or fix anything itself at this point — that's Flyway's job only. If there's a mismatch, startup fails rather than running against a schema it doesn't fully understand.
6. The security rules are assembled next: which web addresses are open to the public (like registration, login, browsing the catalog) and which require a logged-in user; which frontend origins are allowed to make requests at all (CORS policy); and how passwords will be hashed.
7. Finally, the web server starts listening for incoming HTTP requests, and the application is considered "up." From this point on, any of the flows below can happen.

If any step along the way fails, the whole application refuses to start — nothing runs in a half-broken state.

**Flow diagram:**

```
App process starts
        │
        ▼
Load config from .env / environment variables
        │
        ▼
Validate JWT secret
        │
        ├── Missing or too short
        │         │
        │         ▼
        │   Abort startup (fail fast, clear error)
        │
        ▼
Connect to database
        │
        ▼
Flyway: compare migration history vs. migration files
        │
        ▼
Apply any pending migrations, in order
        │
        ▼
JPA validates entity classes match actual DB schema
        │
        ├── Mismatch
        │      │
        │      ▼
        │   Abort startup
        │
        ▼
Wire up security rules
  • Public vs. protected URLs
  • CORS policy
  • Password hashing strategy
        │
        ▼
Start web server, begin listening
        │
        ▼
Application ready to accept requests
```

---

## 2. Registration Flow

1. A new user submits their email, a password, their full name, an optional phone number, and which kind of account they want — customer or designer. A third account kind, admin, exists in the system but can never be requested through registration — that's a deliberate, hard block (see step 2).
2. Before anything is saved, the submitted data is checked: is the requested account kind actually one a stranger is allowed to create (admin is rejected outright, no matter what), is the email actually shaped like an email, is the password long enough and complex enough, are the required fields actually present. Anything that fails this check stops the request immediately — nothing touches the database yet.
3. The email is normalized (extra spaces trimmed, letters lowercased) so that two different-looking versions of the same address are always treated as the same account.
4. The system checks whether an account with that email already exists. If it does, the registration is rejected as a conflict — no duplicate accounts are allowed, and the email field is the uniqueness key.
5. The password is never stored as-is. It's run through a one-way hashing process before saving, so even if the database were ever exposed, the original passwords could not be recovered from it.
6. A new user record is created holding the email, the hashed password, the name, the phone number, the chosen role, and automatic timestamps for when the account was created and last updated.
7. If — and only if — the chosen role is designer, a second, currently-empty profile record is created and linked to that user, meant to be filled in later with a bio, experience, specialization, city, and an availability toggle (defaulting to available). Customers never get this extra record at all.
8. Immediately after the account is created, the system generates a signed access token for the brand-new user, so they're logged in right away without needing a separate login step straight after signing up.
9. The response sent back contains that access token plus the basic account details (id, email, name, role) — everything the client-side app needs to consider the user "logged in" from this point forward.

The one and only admin account in the system today was created directly, not through this flow — registration can never produce one, by design.

**Flow diagram:**

```
Client sends email, password, fullName, phone, role
        │
        ▼
Is the requested role "admin"?
        │
        ├── Yes
        │      │
        │      ▼
        │   400 Bad Request (admin accounts can't self-register)
        │
        ▼
Validate input shape (format, length, required fields)
        │
        ├── Invalid
        │      │
        │      ▼
        │   400 Bad Request (field-by-field errors)
        │
        ▼
Normalize email (trim + lowercase)
        │
        ▼
Does an account with this email already exist?
        │
        ├── Yes
        │      │
        │      ▼
        │   409 Conflict
        │
        ▼
Hash the password
        │
        ▼
Save new user record
        │
        ├── role == DESIGNER
        │         │
        │         ▼
        │   Create empty designer profile, linked to user
        │   (availability defaults to "available")
        │
        ▼
Generate signed JWT access token
        │
        ▼
201 Created + access token + basic user info
```

---

## 3. Login Flow

1. An existing user submits their email and password.
2. The email is normalized the same way as during registration, so login works regardless of capitalization or stray spaces.
3. The system looks up the account by that email.
4. If no account exists with that email, or if one exists but the submitted password doesn't match the stored hash, the login attempt is rejected. Critically, the error message given back is intentionally vague — it never says which part was wrong. This is a deliberate security choice: telling someone "that email doesn't exist" versus "wrong password" would let an attacker quietly figure out which email addresses have accounts, one guess at a time.
5. If the password does match, a brand-new signed access token is generated for this login session. Each login produces its own fresh token with its own expiry countdown — logging in again later doesn't reuse an old one.
6. The response returned is identical in shape to the registration response: access token plus basic account details.
7. From here, the client is expected to store this token and attach it to every future request that needs to prove who's making it.

**Flow diagram:**

```
Client sends email + password
        │
        ▼
Normalize email
        │
        ▼
Look up account by email
        │
        ▼
Compare submitted password against stored hash
        │
        ├── No account found, or password doesn't match
        │         │
        │         ▼
        │   401 "Invalid email or password"
        │   (deliberately vague — never says which part was wrong)
        │
        ▼
Password matches
        │
        ▼
Generate a fresh signed JWT access token
        │
        ▼
200 OK + access token + basic user info
        │
        ▼
Client stores token, attaches it to future requests
```

---

## 4. Authenticated Request Flow (Every Protected Call)

This flow happens on every single request, whether it ends up being allowed or not — it's the gatekeeper step that runs before any business feature.

1. The client sends a request and, if it wants to be recognized as a logged-in user, includes the access token in a specific request header, prefixed with the word "Bearer".
2. A dedicated checkpoint intercepts every incoming request before it reaches any actual feature code, and looks for that header.
3. If the header is missing, or doesn't have the expected "Bearer" prefix, the request is simply treated as coming from an anonymous, logged-out visitor. It's allowed to continue — but only as far as public endpoints go (browsing designs, categories, registering, logging in, viewing API docs). Anything else will be blocked a little further down this same flow.
4. If a token is present, the system reads the identity information encoded inside it — primarily, which email address it was issued to.
5. Rather than trusting that identity blindly, the system goes back to the database and reloads the actual, current user record for that email. This matters: if that person's account had since been deleted, or their role had changed, this reload makes sure the request reflects reality right now — not whatever was true at the moment the token was originally issued.
6. The token itself is then checked for validity: its cryptographic signature must check out (proving it wasn't tampered with and really was issued by this system), the email inside it must match the account just reloaded, and it must not have expired yet.
7. If any of that fails — bad signature, expired, mismatched — the request is left completely unauthenticated, as if no token had been sent at all, and no error is thrown at this stage. The actual rejection (if one is needed) happens in the next step.
8. If the token passes every check, the request is now marked, for its entire remaining lifetime, as belonging to a specific, verified, logged-in user.
9. With that identity now attached (or still missing, if none was ever valid), the system checks whether this particular endpoint is allowed to be reached anonymously. If it requires login and no valid identity was attached, the request is stopped here and rejected with an "unauthorized" response.
10. Some endpoints go one step further and require not just "any logged-in user" but a specific kind of user — customer-only or designer-only actions. If the logged-in user's role doesn't match what's required, the request is stopped and rejected with a "forbidden" response, distinct from the plain "unauthorized" one.
11. Only once all of the above passes does the request actually reach the feature-specific logic described in the flows below.

**Flow diagram:**

```
Client sends request
        │
        ▼
Read Authorization header
        │
        ├── Missing or invalid format
        │         │
        │         ▼
        │   Treat as Anonymous
        │
        ▼
Extract Bearer token
        │
        ▼
Read email from JWT
        │
        ▼
Load latest user from database
        │
        ▼
Validate:
  • Signature
  • Expiry
  • User exists
  • Email matches
        │
   ┌────┴────┐
   │         │
 Invalid   Valid
   │         │
   ▼         ▼
Anonymous  Authenticated User
             │
             ▼
Check endpoint security
             │
     ┌───────┴────────┐
     │                │
 Public          Login Required
     │                │
     ▼                ▼
Allow         Authenticated?
                    │
            ┌───────┴────────┐
            │                │
           No               Yes
            │                │
            ▼                ▼
     401 Unauthorized   Check Role
                              │
                     ┌────────┴────────┐
                     │                 │
                Wrong Role       Correct Role
                     │                 │
                     ▼                 ▼
             403 Forbidden      Controller Executes
```

---

## 5. General Request Lifecycle (Any Endpoint)

This is the shape every single feature request follows, regardless of which specific feature it is.

1. The request arrives and, if the endpoint requires it, passes through the authentication/authorization checkpoint described above.
2. The incoming data is read — this might come from the URL path (like an id), from query parameters (like filters or page numbers), or from a JSON body (like a form submission).
3. That data is checked against validation rules specific to this request type — required fields, correct formats, sensible length and value limits. Anything that fails is rejected immediately with a detailed explanation of exactly what was wrong, without ever reaching the actual business logic.
4. Once the data is confirmed well-formed, it's handed off, together with the identity of whoever is making the request (if logged in), to the layer responsible for the actual business rules of that feature.
5. That business logic layer does the real work:
   - It fetches whatever existing records it needs from the database to make its decision (e.g. "does this booking exist," "does this user already exist," "is this the right owner").
   - It applies whatever rules matter for this specific action — ownership checks ("is this really your booking"), status checks ("can this booking actually move to this new status right now"), role checks ("is the other party actually a designer"), uniqueness checks ("is this email free"), and so on.
   - If everything checks out, it performs whatever change is needed — creating a new record, updating an existing one, or simply reading and returning data unchanged.
6. Before anything goes back to the client, the internal database records are reshaped into a clean, deliberately limited response format. Sensitive or irrelevant internal details — like password hashes, or raw database relationship structures — are never included in what goes out.
7. Finally, a response is sent back with an appropriate status: a success code with the requested/created data, or one of a small set of well-defined error responses (covered in the error-handling flow below), so the caller always knows exactly what happened.

**Flow diagram:**

```
Request arrives
        │
        ▼
Security checkpoint (if endpoint is protected — see Section 4)
        │
        ▼
Read input: path variables / query params / JSON body
        │
        ▼
Validate input shape
        │
        ├── Invalid
        │      │
        │      ▼
        │   400 Bad Request (detailed field errors)
        │
        ▼
Hand off to business logic layer, with caller identity
        │
        ▼
Business logic:
  • Fetch needed records
  • Apply ownership / status / role / uniqueness rules
        │
        ├── Rule violated
        │      │
        │      ▼
        │   Specific error (404 / 403 / 409 / ...)
        │
        ▼
Perform the change, or read the data
        │
        ▼
Map entity/entities → response DTO
  (internal-only fields like password hashes never included)
        │
        ▼
Return response with appropriate status code
```

---

## 6. Design Catalog Browsing Flow

1. Anyone can browse the design catalog — logged in or not — since this is one of the intentionally public parts of the API.
2. A client can ask for the full list, or narrow it down using any combination of: a specific category, a free-text search term (matched against the design's title and description), and a specific style tag. Any of these can be left out entirely, and only the ones actually supplied get applied — there's no requirement to filter by everything at once.
3. Results can also be sorted (by newest, by price, or by title) and paged through, rather than returned all at once.
4. To keep the system responsive and prevent abuse, the page size a client can request is capped at a fixed maximum — no one can ask for an enormous single page of results.
5. The sortable fields are limited to a small, safe, known list — a request trying to sort by some arbitrary or unexpected field quietly falls back to the default sort instead of causing an error.
6. What comes back is a "page" of results: the actual matching items for this page, plus metadata describing where you are — current page number, how many items per page, how many total matching items exist, and how many total pages there are. Every paginated feature in this API (catalog browsing, bookings list) uses this exact same page-shaped response, so a client only has to learn this pattern once.
7. Catalog list results are intentionally lightweight — just enough to render a card (title, category name, cover image, price, style) — not the full description, to keep list responses fast and small.
8. Viewing a single design by its id returns the fuller picture instead: full description, full category details, and who the designer is. If no design exists with that id, the request is rejected as "not found."
9. A related, equally public lookup lets anyone view a single designer's own profile directly — their bio, experience, specialization, city, current availability, and aggregate rating (see the review flow below for where that rating comes from). This is deliberately just a single-profile lookup by id, not a searchable directory of every designer — that's a natural next step, but isn't built yet. Together with the catalog, this is what lets a customer evaluate a specific designer's work and reputation before deciding to book them directly.

**Flow diagram:**

```
Client requests GET /api/designs
 (optional: category, search, style, page, size, sortBy, direction)
        │
        ▼
Clamp page size to the allowed maximum
        │
        ▼
Whitelist the sort field (unknown field → falls back to default)
        │
        ▼
Build filters — only for parameters actually supplied
        │
        ├── category given → filter by category
        ├── search given   → filter by title/description match
        ├── style given    → filter by style tag
        │      (any combination, or none at all)
        │
        ▼
Run one combined query, plus a total-count query
        │
        ▼
Map each result to a lightweight summary (card-sized fields)
        │
        ▼
Wrap results in a page envelope (content + paging metadata)
        │
        ▼
200 OK


Client requests GET /api/designs/{id}
        │
        ▼
Look up design by id
        │
        ├── Not found
        │      │
        │      ▼
        │   404 Not Found
        │
        ▼
Map to full detail response (description, category, designer)
        │
        ▼
200 OK


Client requests GET /api/designers/{id}  (public, no token needed)
        │
        ▼
Look up user by id
        │
        ├── Not found, or not actually a designer account
        │      │
        │      ▼
        │   404 Not Found
        │
        ▼
Load that designer's profile (bio, experience, specialization,
city, availability, aggregate rating)
        │
        ▼
200 OK
```

---

## 7. Booking Flow

1. Only a logged-in user with the customer role can request a new booking — this is enforced before the request even reaches the booking logic.
2. A customer has two ways to start a booking. Either they specify exactly which designer they want to consult with (a scheduled date/time in the future, optional notes, and optionally a specific design they're interested in) — or they leave the designer unspecified entirely and instead describe what they need: a rough category of work, a preferred style, a budget, and where the project is located. The second path is "let Velora choose," and it's what feeds the designer-matching flow described next.
3. If a specific designer was named, the system verifies that user really does have the designer role — you can't accidentally (or deliberately) book a "consultation" with another customer.
4. If a specific design was included, the system also verifies that design actually belongs to the chosen designer — you can't reference someone else's design while booking a different designer. Referencing a specific design without also naming its designer isn't allowed either — it doesn't make sense to point at one specific designer's work while asking to be matched with someone else.
5. A booking that named a designer up front starts its life already in the "awaiting designer confirmation" stage. A booking that didn't starts one stage earlier — "awaiting assignment" — since there's genuinely no designer attached yet for anyone to confirm anything with.
6. When listing bookings, the system automatically shows different things depending on who's asking: a customer sees only the bookings they personally made; a designer sees only the bookings assigned to them. There's no shared "see everything" view — it's always scoped to whoever is currently logged in.
7. Viewing the details of one specific booking is restricted to the people actually involved in it — the customer who made it, or the designer it's currently assigned to (if any). Anyone else attempting to view it, even if logged in, is rejected.
8. A booking can be cancelled only by the customer who originally created it, and only while it's still in an early enough stage (awaiting assignment, awaiting confirmation, or confirmed) — once it's been completed, cancelling no longer makes sense and is blocked.
9. Moving a booking forward through its lifecycle (confirming it, marking it completed) can only be done by the specific designer it's assigned to — not by the customer, and not by any other designer, and not at all while it's still awaiting assignment (there's no designer yet to do the confirming).
10. The lifecycle itself follows a strict, one-directional set of allowed moves:
    - A booking awaiting assignment moves forward only by being assigned a designer (see the matching flow) — it cannot skip straight to confirmed or completed.
    - Once a designer is attached, a pending booking can become confirmed, or be cancelled outright.
    - A confirmed booking can become completed, or still be cancelled.
    - Once a booking is cancelled or completed, it's considered final — no further status changes are permitted from either side, ever.
11. Any attempt to skip a stage, move backward, change a finished booking, or act on a booking that isn't yours is rejected with a clear explanation of exactly why.

**Flow diagram:**

```
Customer requests a new booking
 (scheduledAt, optional notes, and either:
   designerId (+ optional designId)          — direct pick
   or category/style/budget/location         — let Velora choose)
        │
        ▼
Was a specific design given without naming its designer?
        │
        ├── Yes
        │      │
        │      ▼
        │   400 Bad Request
        │
        ▼
Was a designerId given?
        │
        ├── Yes → is that user actually a DESIGNER?
        │              │
        │              ├── No
        │              │      │
        │              │      ▼
        │              │   400 Bad Request
        │              ▼
        │         Does the optional design belong to that designer?
        │              │
        │              ├── No
        │              │      │
        │              │      ▼
        │              │   400 Bad Request
        │              ▼
        │         Create booking, status PENDING (designer attached)
        │
        ├── No →  Create booking, status PENDING_ASSIGNMENT (no designer yet)
        │
        ▼
201 Created


Booking action requested (view / cancel / update status)
        │
        ▼
Load booking by id
        │
        ├── Not found
        │      │
        │      ▼
        │   404 Not Found
        │
        ▼
Is the requester an allowed participant?
 • view      → must be the customer OR the assigned designer (if any)
 • cancel    → must be THE customer on it
 • status    → must be THE assigned designer (booking must have one)
        │
        ├── No
        │      │
        │      ▼
        │   403 Forbidden
        │
        ▼
(For cancel/status) Is this transition allowed from the current status?
  PENDING_ASSIGNMENT → nothing directly (must be assigned first)
  PENDING            → CONFIRMED or CANCELLED
  CONFIRMED          → COMPLETED or CANCELLED
  CANCELLED / COMPLETED → nothing (final)
        │
        ├── Not allowed
        │      │
        │      ▼
        │   409 Conflict
        │
        ▼
Apply the change
        │
        ▼
200 OK
```

---

## 8. Designer Assignment & Matching Flow

1. This flow only ever applies to a booking sitting in the "awaiting assignment" stage — one where the customer asked Velora to choose rather than naming a designer themselves.
2. Only an admin can trigger this. An admin asks the system to rank candidate designers for one specific awaiting-assignment booking.
3. The system looks at every designer who has currently marked themselves as available — a designer who's toggled themselves unavailable is left out of consideration entirely, before any scoring even happens.
4. Each remaining candidate is scored against the booking's stated preferences, across several independent factors, each worth a fixed share of a 100-point total: whether their specialization matches the requested category or style; whether their own portfolio actually contains work in that category or style; how many years of experience they have (more counts, up to a cap); whether their location matches where the project is; their aggregate rating from past completed work (a designer with no reviews yet gets a fair, neutral middle score rather than being penalized for being new); and whether the customer's stated budget is realistically in line with that designer's typical pricing based on their past portfolio.
5. Candidates are sorted highest-score first. If two candidates end up tied, the one currently juggling fewer active bookings is ranked higher — an even, fair way to avoid overloading the same few designers.
6. The ranked list — each candidate's relevant details plus their score — is handed back to the admin. Nothing is assigned automatically at this point; this is a recommendation, not a decision.
7. The admin picks one candidate from that list (or in principle any eligible designer at all) and assigns them the same way a booking is normally handed to a designer — at which point the booking leaves "awaiting assignment" and rejoins the exact same lifecycle a directly-booked designer would already be in.
8. Trying to rank candidates for a booking that isn't actually awaiting assignment (already has a designer, or was cancelled) is rejected — there's nothing to recommend for.

**Flow diagram:**

```
Admin requests rankings for a booking
        │
        ▼
Is this booking currently PENDING_ASSIGNMENT?
        │
        ├── No
        │      │
        │      ▼
        │   409 Conflict
        │
        ▼
Load every designer currently marked AVAILABLE
        │
        ▼
Score each one against the booking's stated preferences:
  • specialization / category / style match
  • their own portfolio has relevant work
  • years of experience (capped)
  • project location matches their city
  • aggregate rating (unrated designers get a neutral score)
  • budget realistically fits their typical pricing
        │
        ▼
Sort highest score first
 (tie → prefer whoever has fewer active bookings right now)
        │
        ▼
Return ranked list to the admin — nothing assigned yet
        │
        ▼
Admin picks one candidate
        │
        ▼
Assign that designer to the booking
 (booking rejoins the normal lifecycle — same as a direct pick)
        │
        ▼
200 OK
```

---

## 9. Quotation Flow

1. A booking on its own never carries a price — it just gets a designer and customer talking. A quotation is the separate thing that turns that consultation into an actual, priced scope of work: a list of line items and a total cost, which the customer then has to explicitly accept or reject.
2. Only the designer assigned to a booking can build or edit its quotation. They add line items — each one a description and an amount, optionally broken down further into quantity, unit, and unit price for a fully itemized breakdown — and the system automatically adds them all up into a running total. Nobody ever types in a total by hand; it's always recalculated from the line items themselves, so it can never drift out of sync with what's actually listed.
3. While a quotation is still being put together, it sits in a draft stage. The designer can keep changing it freely — every save simply replaces the whole line-item list with whatever was just submitted, so there's no need to track individual additions or removals.
4. Once a quotation has been sent to the customer, it can no longer be edited. If it needs to change after that point, that specific quotation's story is over — there's no "unsend" or "revise" step in this version. This is a deliberate simplification: better to keep the lifecycle simple and predictable for now than to support editing history.
5. A quotation can't be sent with nothing in it — at least one line item is required before it's allowed to leave the draft stage. This isn't checked at the same time as the basic shape of the request; it's checked specifically at the moment of sending, because an empty quotation is perfectly fine to exist temporarily while it's still being drafted.
6. Once sent, only the customer on that specific booking can respond to it — and only while it's still in the "sent" stage. They can either accept it, moving it to its final approved state, or reject it, moving it to its final declined state. Once either of those happens, the quotation is done; there's no going back to sent or draft.
7. Anyone not involved in the booking — not the customer, not the designer — has no access to view or act on its quotation at all. And within the two people who are involved, each side is restricted to only the actions that make sense for their role: the designer can build and send, but never accept or reject; the customer can only respond to what's been sent, never edit it themselves.

**Flow diagram:**

```
Designer builds a quotation for a booking
 (description + amount per line item, optionally quantity/unit/unit price too)
        │
        ▼
Is the requester the designer assigned to this booking?
        │
        ├── No
        │      │
        │      ▼
        │   403 Forbidden
        │
        ▼
Has this quotation already been sent, accepted, or rejected?
        │
        ├── Yes
        │      │
        │      ▼
        │   409 Conflict (can no longer be edited)
        │
        ▼
Replace the entire line-item list with what was just submitted
        │
        ▼
Recalculate the total from the line items (never trusted from the client)
        │
        ▼
200 OK — saved as DRAFT


Designer sends the quotation to the customer
        │
        ▼
Is it currently a draft?
        │
        ├── No
        │      │
        │      ▼
        │   409 Conflict
        │
        ▼
Does it have at least one line item?
        │
        ├── No
        │      │
        │      ▼
        │   400 Bad Request
        │
        ▼
Mark as SENT
        │
        ▼
200 OK


Customer accepts or rejects the quotation
        │
        ▼
Is the requester the customer on this booking?
        │
        ├── No
        │      │
        │      ▼
        │   403 Forbidden
        │
        ▼
Is the quotation currently SENT?
        │
        ├── No
        │      │
        │      ▼
        │   409 Conflict
        │
        ▼
Mark as ACCEPTED or REJECTED (final either way)
        │
        ▼
200 OK
```

---

## 10. Review & Rating Flow

1. Once a booking has actually been marked completed, the customer on it can leave a review — a 1-to-5 rating plus an optional written comment.
2. Only the customer who was actually on that booking can review it — nobody else, including the designer themselves, can leave one, and it can't be done at all until the booking has genuinely reached its completed stage. Trying to review anything earlier in the lifecycle is rejected.
3. Exactly one review is allowed per booking, ever. A second attempt on the same booking is rejected outright as a duplicate — there's no editing or replacing a review once it exists in this version.
4. The moment a review is saved, the designer's overall rating is immediately recalculated from every review they've ever received — not just this newest one — so it always reflects their full track record, not a single data point.
5. That recalculated aggregate rating is exactly the same number surfaced on the designer's public profile and fed into the designer-matching scoring formula described earlier — a completed project's outcome flows directly back into how future customers evaluate that designer and how future bookings might get matched to them.
6. Individual written comments aren't listed anywhere yet in this version — only the aggregate score and count are exposed. The comments themselves are stored, so making them viewable later doesn't require changing how reviews are created.

**Flow diagram:**

```
Customer submits a review for a booking
 (1-5 rating, optional comment)
        │
        ▼
Is the requester the customer who made this booking?
        │
        ├── No
        │      │
        │      ▼
        │   403 Forbidden
        │
        ▼
Is the booking's status COMPLETED?
        │
        ├── No
        │      │
        │      ▼
        │   409 Conflict
        │
        ▼
Has this booking already been reviewed?
        │
        ├── Yes
        │      │
        │      ▼
        │   409 Conflict
        │
        ▼
Save the review
        │
        ▼
Reload every review this designer has ever received
        │
        ▼
Recompute their average rating and review count
        │
        ▼
Save the updated aggregate onto the designer's profile
 (this is what the public profile shows, and what
  designer-matching scores against going forward)
        │
        ▼
201 Created
```

---

## 11. Profile Flow

1. A logged-in user can view their own profile — always their own; there is no way to look up someone else's profile through this endpoint.
2. The response always includes the shared basic details every account has: id, email, name, phone, role, and when the account was created.
3. On top of that, if the logged-in user is a designer, their designer-specific details are also included — bio, years of experience, specialization, city, their current availability toggle, and their aggregate rating and review count. If the user is a customer, that whole section is simply absent from the response rather than showing up empty — customers don't have that data at all, so there's nothing to include.
4. Updating a profile works as a partial update: the client only sends the fields it actually wants to change, and anything left out is simply untouched — there's no need to resend the entire profile just to change one field.
5. The basic fields (name, phone) can be updated by anyone, regardless of role.
6. The designer-specific fields (bio, experience, specialization, city, and the availability toggle) only ever take effect if the logged-in user is actually a designer. If a customer's update request happens to include any of those fields anyway, they're quietly ignored rather than causing an error — there's simply no designer profile record for them to apply to.
7. The availability toggle is entirely self-managed — a designer flips themselves to unavailable or back to available whenever they choose, and that's the only signal the matching flow uses to decide whether to consider them at all. There's no calendar or scheduled time slots behind it yet, just this one on/off flag.
8. The aggregate rating and review count are read-only from this endpoint — they can never be set directly by anyone. The only way that number ever changes is through the review flow above, recalculated from actual completed-booking reviews.
9. If a designer updates their profile before ever having filled anything in, the missing designer-profile record is created automatically at that moment, rather than requiring a separate "set up your designer profile" step first.

**Flow diagram:**

```
GET /api/users/profile
        │
        ▼
Load current user (identity taken from the token, never from input)
        │
        ▼
Is role == DESIGNER?
        │
        ├── Yes → also load designer profile record
        │
        ▼
Build response
 (designerProfile section included only for designers)
        │
        ▼
200 OK


PUT /api/users/profile (only the fields the client wants to change)
        │
        ▼
Apply basic fields if present (fullName, phone)
        │
        ▼
Is role == DESIGNER?
        │
        ├── No  → any designer-specific fields sent are silently ignored
        │
        ├── Yes → load designer profile, or create one if it doesn't exist yet
        │             │
        │             ▼
        │        Apply any supplied designer fields
        │        (bio, yearsExperience, specialization, city)
        │
        ▼
Save changes
        │
        ▼
200 OK + updated profile
```

---

## 12. Validation Flow

1. Every request that includes a body (registration, login, profile updates, booking creation, status changes, quotation line items, designer assignment, reviews) has that body checked against a fixed set of rules before any feature logic runs at all.
2. These rules cover things like: is a required field actually present and non-empty; does a value look like a properly formatted email; is a piece of text within its allowed minimum/maximum length; does a number fall within a sensible range; does a password meet its complexity requirement (a minimum length, plus at least one letter and one digit); is a scheduled time actually set in the future rather than the past.
3. If even one rule fails, the entire request is rejected immediately, before touching the database, with a response listing every single field that failed and a human-readable reason for each one — not just the first problem found, but all of them at once, so a client can fix everything in one pass instead of discovering issues one at a time.
4. This layer only ever looks at the shape of the data itself — it has no awareness of what else exists in the database. Anything that depends on existing data (is this email already taken, does this designer actually exist, do you actually own this booking) is deliberately handled one layer deeper, inside the business logic, after this basic shape-check has already passed.

**Flow diagram:**

```
Request body received
        │
        ▼
Run field-level checks
  • required / non-empty
  • correct format (e.g. email)
  • length within allowed range
  • value within allowed range
  • pattern match (e.g. password complexity)
        │
        ├── One or more checks fail
        │         │
        │         ▼
        │   Collect EVERY failing field (not just the first)
        │         │
        │         ▼
        │   400 Bad Request + full list of field errors
        │
        ▼
All checks pass
        │
        ▼
Continue to business logic
 (data-dependent rules — uniqueness, ownership, existence —
  are checked there, not at this stage)
```

---

## 13. Error Handling Flow

1. No matter where something goes wrong in the system — a missing record, a broken rule, bad input, an unexpected bug — it's all funneled through one single, central point before anything is sent back to the client. Individual features never have to build their own custom error responses.
2. Every error response, regardless of cause, has exactly the same overall shape: when it happened, a numeric status code, a short label for that status, a human-readable message explaining what went wrong, which endpoint was being called, and — only for validation failures — a detailed list of which specific fields were the problem.
3. Different situations map to different, predictable outcomes:
   - Asking for something that doesn't exist (a user, a design, a booking) results in a "not found" response.
   - Trying to create something that would duplicate existing data (like registering an email that's already taken, assigning a designer to a booking that already has one, or reviewing a booking a second time) results in a "conflict" response.
   - Trying to act on something you don't have permission over (someone else's booking, someone else's quotation, reviewing a booking that isn't yours) results in a "forbidden" response.
   - Trying to move something into an invalid state (an illegal booking status change, editing/responding to a quotation that's already past the stage where that's allowed, or reviewing a booking that isn't completed yet) also results in a "conflict" response.
   - Asking Velora to rank designers for a booking that isn't actually awaiting assignment also results in a "conflict" response.
   - Submitting the wrong login credentials results in an "unauthorized" response.
   - Missing or invalid login on a protected endpoint results in "unauthorized"; being logged in but lacking the right role results in "forbidden."
   - Submitting badly-shaped input results in a "bad request" response with the field-by-field breakdown.
   - Anything else entirely unexpected falls back to a generic "internal error" response — deliberately vague, so no internal system detail is ever accidentally exposed to a client.
4. Two different parts of the system are capable of producing "unauthorized"/"forbidden" outcomes — the very first gatekeeper check (missing/invalid token, wrong role) and the deeper business-logic checks (you don't own this specific record). Both are deliberately made to produce the exact same response shape, so from the outside, a client can't tell which part of the system caught the problem — it always looks and behaves the same way.

**Flow diagram:**

```
Something goes wrong, anywhere in the system
        │
        ▼
Caught by one central error handler
        │
        ▼
Match against known situations
        │
        ├── Record doesn't exist          → 404 Not Found
        ├── Duplicate data (e.g. email)   → 409 Conflict
        ├── Not your resource             → 403 Forbidden
        ├── Illegal state change          → 409 Conflict
        ├── Wrong login credentials       → 401 Unauthorized
        ├── Missing/invalid token         → 401 Unauthorized
        ├── Logged in, wrong role         → 403 Forbidden
        ├── Malformed input               → 400 Bad Request + field list
        ├── Anything unmapped/unexpected  → 500 Internal Error (generic message)
        │
        ▼
Build the one standard error shape
 (timestamp, status, error label, message, path, fieldErrors)
        │
        ▼
Send to client
 (looks identical whether caught at the security checkpoint
  or deep inside business logic)
```

---

## 14. Documentation Flow

1. A live, interactive description of every available endpoint is generated automatically, straight from the real code — there's no separate document to keep updated by hand, so it can never fall out of sync with what the API actually does.
2. Anyone can open this documentation in a browser and see, for every endpoint: what it expects as input, what it returns, and what error cases look like.
3. It's also usable directly, without any external tool: a user can log in right there in the documentation page, take the token they receive, plug it into the page's own "authorize" step, and from then on every protected endpoint they try directly from that page automatically includes their login — letting someone explore and test the whole API from nothing but a browser.
4. The documentation pages themselves are public and viewable without logging in — only actually calling the protected endpoints through them requires a real logged-in token, same as calling them any other way.

**Flow diagram:**

```
Developer opens the API documentation page
        │
        ▼
Browse the auto-generated list of endpoints
 (input shape, response shape, error cases — for each one)
        │
        ▼
Log in through the documentation page itself
        │
        ▼
Copy the returned access token
        │
        ▼
Paste it into the page's "Authorize" step
        │
        ▼
Every subsequent "try it out" call
 automatically includes that token
        │
        ▼
Protected endpoints can be tested directly from the browser
```

---

## 15. Environment & Configuration Flow

1. Anything sensitive or environment-specific — database connection details, the JWT signing secret, which frontend addresses are allowed to call the API — is never written directly into the codebase. It's always supplied from outside, through environment variables, so the same code can run against different databases and settings in different places without being changed itself.
2. During local development, these values are supplied automatically from a local configuration file that lives only on the developer's machine and is deliberately excluded from version control — so real secrets are never accidentally shared or committed anywhere.
3. There's a separate, optional local-development mode that layers a few extra conveniences on top of the normal configuration: a safe placeholder signing key so a new developer can run the app immediately without generating one themselves, and much more detailed logging (including the raw database queries being run) to make local troubleshooting easier. None of this affects how the system behaves outside of local development.
4. The database itself lives in the cloud rather than requiring anything installed locally — anyone working on this project only needs the connection details, not a locally running database server, to get the whole system working end to end.

**Flow diagram:**

```
Application needs a configuration value
        │
        ▼
Look for a matching environment variable
        │
        ├── Set (via real env var, or a local .env file)
        │         │
        │         ▼
        │   Use that value
        │
        ├── Not set
        │         │
        │         ▼
        │   Fall back to a built-in default, if one exists
        │
        ▼
Is the "local" development profile active?
        │
        ├── Yes
        │      │
        │      ▼
        │   Layer on dev-only defaults (e.g. placeholder JWT secret)
        │   and much more detailed / verbose logging
        │
        ▼
Final configuration is ready before startup continues
```

---

*High-level flow only. For the technical "why" behind each step — code structure, class names, Spring/Java concepts — see `IMPLEMENTATION_FLOW.md`.*
