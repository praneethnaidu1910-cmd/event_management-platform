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
- 2026-09-08 evening (`daily/2026-09-08`): Added `@WebMvcTest` coverage for
  AuthController, EventController, and OrderController (20 new tests) -
  the controller layer had zero coverage before this, per this morning's
  "next up" note. Each test class imports the real `SecurityConfig` and
  `JwtAuthenticationFilter` (with their dependencies mocked) rather than
  mocking the filter itself, so `@PreAuthorize` role checks are genuinely
  exercised via `@WithMockUser` instead of silently no-op'd, which is a
  common trap with this test slice. Added `spring-security-test` as a test
  dependency for `@WithMockUser`.
  Writing these tests surfaced two real bugs in `GlobalExceptionHandler`,
  both fixed and covered: (1) `@Valid` failures on request bodies had no
  matching handler and fell through to the catch-all, returning 500
  instead of 400; (2) Spring Security's `AccessDeniedException` (thrown by
  `@PreAuthorize` on a role mismatch) collided with the same catch-all for
  the same reason - the existing handler only matched the app's own
  `AccessDeniedException` class, not Spring Security's, so every wrong-role
  request was returning 500 instead of 403.
  Also found, but deliberately left alone: an anonymous request to a
  protected endpoint returns 403, not 401, because `SecurityConfig` never
  registers an `AuthenticationEntryPoint` (Spring Security falls back to
  `Http403ForbiddenEntryPoint`). Test expectations were adjusted to match
  this actual behavior rather than "fixing" it, since that's a SecurityConfig
  change and the instructions for this session call for extra caution
  there - it's a reasonable small follow-up for a session with more room
  to test it properly, not an urgent bug.
  Full suite: 55/55 passing, run three times with no flakes.
  Next up: still no test coverage for `CurrentUserService`,
  `UserDetailsServiceImpl`, or `SecurityConfig` itself. Also on the roadmap:
  decide whether to add a proper `AuthenticationEntryPoint` (401 instead of
  403 for missing/invalid auth) noted above, then move to roadmap item 2
  (refunds/cancellations, admin endpoints, email on purchase) now that the
  auth/event/order flows all have real test coverage underneath them.
- 2026-09-08 morning (`daily/2026-09-08`): Added `OrderServiceConcurrencyTest`,
  a real-database test for the ticket purchase flow. The existing
  `OrderServiceTest` only calls `purchaseTickets()` against a mocked
  repository one at a time, so it never exercised the pessimistic row lock
  (`findByIdForUpdate`) that's supposed to stop overselling under real
  concurrent load - which is the specific risk this roadmap item calls out.
  The new test spins up 30 threads buying against a 10-ticket allotment on a
  real (in-memory H2) Spring context and asserts exactly 10 succeed, the
  rest get `InsufficientTicketsException`, and available/ticket counts never
  exceed what was in stock. Purchases retry on `CannotAcquireLockException`
  since H2's lock manager sometimes reports plain single-row contention at
  this thread count as a deadlock rather than queuing waiters like Postgres
  would; this mirrors the retry a client would need under real SERIALIZABLE
  contention in production. Ran 4 times locally with no flakes. Full suite:
  33/33 passing.
  Next up: still no test coverage for the controller layer (AuthController/
  EventController/OrderController), or for CurrentUserService,
  UserDetailsServiceImpl, SecurityConfig. Also worth a look: OrderService
  uses `@Transactional(isolation = SERIALIZABLE)` *and* an explicit
  pessimistic write lock together, which under heavy contention against
  H2 shows up as spurious `CannotAcquireLockException`/deadlock errors
  even for single-row contention (see the retry logic added in this
  session) - worth checking whether SERIALIZABLE is pulling its weight
  on top of the row lock, or whether dropping to READ_COMMITTED would be
  simpler and just as safe, before building more features on top of this
  flow.
