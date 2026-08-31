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
- 2026-08-31, evening, `daily/2026-08-31`: Picked up where the morning
  session left off - controller-layer tests. Added `@WebMvcTest`/MockMvc
  coverage for `AuthController`, `EventController`, and `OrderController`,
  importing the real `SecurityConfig` and using `spring-security-test`'s
  `@WithMockUser` so `@PreAuthorize` role checks (organizer-only event
  create/update/delete, attendee-only purchase) are actually exercised
  instead of mocked away. Along the way found and fixed a real bug: a
  wrong-role request (an authenticated ATTENDEE hitting an ORGANIZER-only
  endpoint) and a failed `@Valid` request were both falling through
  `GlobalExceptionHandler`'s catch-all and coming back as 500 "Unexpected
  error" instead of 403/400 - the handler only matched the app's own
  `AccessDeniedException`, not Spring Security's class of the same simple
  name, and had no handler for `MethodArgumentNotValidException` at all.
  Added explicit handlers for both, plus unit tests for them. Full suite
  green (56 tests, `./mvnw test`), 3 commits: the exception-handler fix,
  the new controller tests + `spring-security-test` dependency, and this
  log entry. Combined with this morning: auth-support services
  (`CurrentUserService`, `UserDetailsServiceImpl`) and now all three
  controllers have real coverage, so step 1 (tests) is essentially done -
  the transactional purchase flow, event CRUD, and auth are all tested at
  both the service and controller layer. Next session: start step 2
  (missing backend features) - refunds/cancellations is the natural first
  pick since it's adjacent to the order/ticket code that's already
  well-tested; email notification on purchase needs a decision on what
  mail provider/config to use, so probably leave that for a session with
  more room to make that call deliberately.
- 2026-08-31, morning, `daily/2026-08-31`: Added unit tests for
  `CurrentUserService` and `UserDetailsServiceImpl`, the two auth-support
  services that had no coverage yet (everything else in that path -
  `JwtTokenProvider`, `JwtAuthenticationFilter`, `AuthService` - already
  had tests). No production code changed. 10 new tests, full suite green
  (38 tests, `./mvnw test`). Next session: controller-layer tests
  (`EventController`, `OrderController`, `AuthController` via
  `@WebMvcTest`/MockMvc) are still missing and would need
  `spring-security-test` added as a test dependency to exercise the
  `@PreAuthorize` role checks properly - worth doing deliberately rather
  than rushed. After that, step 1 (tests) is essentially done and step 2
  (refunds/cancellations, admin endpoints, email on purchase) can start.
