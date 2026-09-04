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
- 2026-09-04, evening, `daily/2026-09-04`: Added MockMvc controller tests for
  OrderController (purchase endpoint: attendee success, role rejection,
  validation failure, ticket-type-not-found and insufficient-tickets error
  mapping) and AuthController (register and login, success and failure
  paths). Both had zero test coverage above the service layer, per this
  morning's note. Writing the validation-failure cases surfaced another
  gap in the same handler the morning session touched: `@Valid` failures
  (`MethodArgumentNotValidException`) had no handler in
  `GlobalExceptionHandler` either, so a malformed register/purchase request
  was returning 500 instead of 400. Fixed with a dedicated handler that
  reports the failing field(s), plus a unit test. Tests: 56/56 passing
  (`./mvnw test`), up from 42 this morning. Every controller now has at
  least baseline coverage. Next session: no controller is left uncovered,
  so move to roadmap item 2 (missing backend features) - refunds/cancellations
  is the natural next piece since it extends the order/ticket flow that's
  now reasonably well tested; email notification on purchase and admin
  endpoints are the other open items there.
- 2026-09-04, morning, `daily/2026-09-04`: Added MockMvc-based controller tests
  for EventController (public GET endpoints, organizer-only create/update/delete,
  role rejection for ATTENDEE and anonymous callers, search param forwarding) -
  no controller had test coverage before this. Writing the role-rejection tests
  surfaced a real bug: `GlobalExceptionHandler` only handled our own
  `com.eventmanagement.exception.AccessDeniedException`, not Spring Security's
  `org.springframework.security.access.AccessDeniedException` that `@PreAuthorize`
  actually throws, so every role-gated endpoint returned 500 instead of 403 for
  an unauthorized caller. Fixed with a dedicated handler and a regression test.
  Also fixed `mvnw` being committed without the execute bit (fresh clone couldn't
  run it). Tests: 42/42 passing (`./mvnw test`), up from 32. Next session should
  add controller tests for OrderController (the purchase endpoint) and
  AuthController - both still have zero test coverage at any layer above the
  service.
