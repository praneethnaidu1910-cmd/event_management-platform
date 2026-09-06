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
- 2026-09-06, evening, `daily/2026-09-06`: added MockMvc controller
  tests for AuthController, EventController, and OrderController (the
  gap called out by this morning's entry) - request validation, role
  enforcement (organizer/attendee/anonymous) via the real SecurityConfig
  and method security, and the exception-to-status-code mapping for
  each controller's failure cases. Needed `spring-security-test` for
  `@WithMockUser`, added as a test dependency. Writing the authorization
  tests turned up a real bug along the way: `GlobalExceptionHandler`'s
  catch-all handler was intercepting Spring Security's own
  `AccessDeniedException` (thrown by `@PreAuthorize` when an
  authenticated user lacks the right role) before the security filter
  chain could translate it, so those requests got a 500 "Unexpected
  error" instead of a 403 - anonymous requests were fine since those
  get denied earlier, at the URL-level filter. Added a dedicated
  handler mapping it to 403 plus a unit test. Full suite green (52
  tests, up from 33). Next session: roadmap item 2 (refunds/
  cancellations, admin endpoints, email notification on purchase) -
  refunds/cancellations is probably the best starting point since it's
  the most natural extension of the existing order/ticket flow.
- 2026-09-06, morning, `daily/2026-09-06`: found that `GlobalExceptionHandler`'s
  catch-all `Exception` handler was also swallowing `MethodArgumentNotValidException`,
  so any `@Valid` failure (e.g. registering with a malformed email or a short
  password) came back as a 500 "Unexpected error" instead of a 400 with the
  actual field errors. Added a dedicated handler for it plus a `fieldErrors`
  map on `ErrorResponse`, and a unit test covering it. Also fixed `mvnw`'s
  missing executable bit (a fresh checkout couldn't run it). Full suite green
  (33 tests). Next session: still no controller-level (MockMvc) tests for
  AuthController/EventController/OrderController - only service-layer unit
  tests exist there; that's the natural next step before moving on to roadmap
  item 2 (refunds/cancellations, admin endpoints, email notifications).
