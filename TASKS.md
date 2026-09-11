# Working Notes

## Commit cadence
- Morning session ~9 AM: 2 commits
- Evening session ~7 PM: 3 commits
- Times will drift around other routines; this is a target, not a hard rule.
- Working directly on `main` only for small/safe changes. Anything that touches
  auth, ticket inventory, or the DB schema goes through a feature branch + PR,
  reviewed before it merges.

## Scope roadmap (in order)
1. **Tests** — no coverage exists yet. Start with auth (register/login),
   event CRUD, and the order/ticket purchase flow, since that's the one with
   concurrency risk (overselling tickets).
2. **Missing backend features** — refunds/cancellations, admin endpoints,
   email notification on purchase. (Event search & filtering done, 2026-08-28.)
3. **Deployment/DevOps** — Dockerize the app, add GitHub Actions CI (build +
   run the test suite from step 1), deploy somewhere reachable.
4. **Frontend** — optional/last. A thin client to demo the API end-to-end.
   Lower priority than hardening the backend itself.

Rationale: features 2-4 all build on code that currently has zero test
coverage, including the transactional logic that prevents overselling
tickets. Locking down tests first means later changes have something to
break against instead of failing silently in production.

## Current state (as of 2026-08-26)
- Auth: register/login with JWT, roles ATTENDEE/ORGANIZER/ADMIN.
- Events: organizer CRUD, public browse of published events.
- Orders/Tickets: purchase flow with transactional inventory checks.
- Tests: unit coverage for JwtTokenProvider, JwtAuthenticationFilter,
  AuthService, EventService, OrderService (19 tests, merged in PR #1).
- GraphQL: a query layer over the events read path (events/event(id)),
  merged in PR #2.
- Tests: unit coverage for GlobalExceptionHandler (6 tests), and an event
  search endpoint (keyword/category/location/date filters) with both a
  mocked-repository unit test and a real H2-backed query test.
- No CI, no frontend, no deployment yet.
- 2026-08-28: repo moved from a fork of yashaswini-tdr/event_management-platform
  into praneethnaidu1910-cmd's own account, so the automated sessions have
  admin-level push access (the prior setup hit a 403 - the Claude GitHub App
  wasn't authorized on someone else's repo). Two automated runs that day got
  stuck in ephemeral sandboxes and never reached GitHub before this move -
  that work (GlobalExceptionHandler tests, and the event search endpoint)
  was redone from scratch afterward, not recovered from the stranded
  sessions, since only truncated summaries of what they did were available.

## Automated sessions (9 AM / 7 PM daily)
Unattended cloud sessions work on a shared branch per day: `daily/YYYY-MM-DD`,
created off `main` by the morning run, continued by the evening run. They
push that branch to origin but never touch `main` and never open a PR -
praneethnaidu1910-cmd reviews the diff and opens the PR by hand.

Each run appends an entry below before pushing, so the next run (and the
human reviewing later) knows what happened and what's next. Newest first.

### Log
- 2026-09-11, morning, `daily/2026-09-11`: `main` is still stuck at
  2026-08-28 - none of the daily branches from 2026-08-29 through
  2026-09-10 have been merged yet (twelve branches now), so this
  session branched off the same stale base as all the others. Checked
  each of those branches before picking work, to avoid re-solving
  something already sitting in the backlog: controller-layer MockMvc
  tests for Auth/Event/Order, the `@PreAuthorize`/`@Valid` 500-instead-
  of-403/400 exception-handler fixes, ticket-purchase concurrency
  tests, refunds/admin stats/notifications, Dockerfile/compose, and a
  CI workflow all already exist on one branch or another - grepped for
  the two specific gaps the 2026-09-10 evening log flagged as untouched
  (`pageable`/`pagination`, ticket-type price/quantity validation) and
  confirmed neither appears on any of the twelve branches.
  Picked pagination on `GET /api/events`, since it's small, self-
  contained, and doesn't touch auth/security or the ticket-inventory
  path. Added a `findByStatus(status, Pageable)` overload on
  `EventRepository` alongside the existing unpaginated one (which
  `EventGraphQlController` still uses unchanged), a new
  `EventService.getAllEvents(page, size)` that validates page >= 0 and
  1 <= size <= 100 (throwing `BadRequestException` otherwise) and
  returns a `Page<EventResponse>` sorted by `startDate` ascending, and
  updated the controller to take `page`/`size` query params (defaults
  0/20). This changes the response shape of `GET /api/events` from a
  bare JSON array to a `Page` envelope (`content`, `totalElements`,
  `totalPages`, etc.) - a deliberate, visible break, not an oversight,
  since that's what pagination requires; `GET /api/events/search`
  was left as a plain list, unpaginated, to keep this change scoped to
  the one endpoint.
  Also found `mvnw` is still committed without its executable bit on
  `main` (every session hitting this has had to `chmod +x` it locally;
  the 2026-09-10 morning branch fixed it but that fix is stuck in the
  same unmerged pile) - `chmod +x`'d it locally to run the suite but
  deliberately did not commit that fix here, to keep this branch's diff
  to the one roadmap item.
  Test status: full suite green, 35 tests (`./mvnw test`, JDK 17 -
  installed via `apt-get install openjdk-17-jdk`, which took nearly 30
  minutes on this sandbox's network; only JDK 21 was preinstalled).
  Next session should pick up: (a) the merge backlog is now thirteen
  unmerged branches deep - this is the single biggest thing slowing
  these sessions down, since every morning re-derives the same "what's
  already been done" analysis from scratch by grepping branches instead
  of reading it off `main`; a human reviewing and merging even one
  branch (this one is a reasonable candidate: small, tested, no
  auth/security touched) would help going forward. (b) Ticket-type
  price/quantity validation (e.g. `TicketTypeRequest.price` accepting
  negative values, `quantity` accepting zero or negative) is still the
  other gap nothing has touched. (c) If continuing pagination further:
  the search endpoint (`GET /api/events/search`) and organizer-scoped
  listing don't have it either, though neither was flagged as urgent.
  (d) The `mvnw` executable-bit fix is one line and safe to land
  whenever a branch touches the build tooling next.
