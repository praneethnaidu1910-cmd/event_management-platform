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
- 2026-09-03, evening, `daily/2026-09-03`: Added the first controller-level
  tests in the project - EventControllerTest, OrderControllerTest,
  AuthControllerTest (23 new tests), using `@WebMvcTest` with the real
  `SecurityConfig` imported so `@PreAuthorize` and the security filter chain
  actually run, rather than mocking security away. JwtAuthenticationFilter's
  own dependencies (JwtTokenProvider/UserRepository/UserDetailsServiceImpl)
  are mocked since every test authenticates via `@WithMockUser` instead of a
  real token, so the filter is present in the chain but never does anything.
  Needed the `spring-security-test` dependency (test scope only) for
  `@WithMockUser`; also fixed `mvnw`'s missing executable bit, which was
  blocking `./mvnw test` from running at all in a fresh checkout.
  Writing these against the real DispatcherServlet + exception-resolution
  pipeline (rather than calling controller/handler methods directly, which
  is all the existing tests did) surfaced two real bugs, fixed in a separate
  commit before the controller tests were added: (1) `@PreAuthorize` denying
  a request throws Spring Security's own `AccessDeniedException`, which is a
  different class from this project's `com.eventmanagement.exception.
  AccessDeniedException` - GlobalExceptionHandler only had a handler for the
  latter, so a role-mismatch was coming back as a 500 instead of 403; (2)
  `MethodArgumentNotValidException` (thrown by `@Valid` on any request body)
  had no handler at all, so every validation failure on register/login/
  create-event/purchase was also a 500 instead of 400. Both now have
  dedicated handlers, covered by new unit tests in GlobalExceptionHandlerTest
  in addition to being exercised through the controller tests. Full suite
  (58 tests) passes via `./mvnw test`. This closes out the "no
  controller-level tests" item flagged by this morning's session. Next
  session: refunds/cancellations and admin endpoints are still unstarted on
  the roadmap (item 2); no CI/Docker yet (item 3). Worth a look before
  picking up new features: GlobalExceptionHandler's generic `Exception ->
  500` catch-all is now known to be a real fallback path (not just
  defensive) - worth double-checking there isn't a third exception type in
  the same boat before it bites someone in production the way these two did.
- 2026-09-03, morning, `daily/2026-09-03`: Added a real-database concurrency
  test for the ticket purchase flow (OrderConcurrencyTest - 20 threads racing
  for 5 tickets against an H2-backed OrderService, not a mocked repository).
  At full concurrency it reliably reproduced dropped/failed purchases caused
  by stacking `@Transactional(isolation = SERIALIZABLE)` on top of the
  existing pessimistic row lock (`findByIdForUpdate`) in OrderService -
  the lock alone already serializes access to a ticket type's availability,
  and the extra SERIALIZABLE isolation just gave the DB a second way to
  abort a legitimate concurrent buyer's transaction (visible to a real user
  as an unhandled 500 instead of a normal sold-out response), with no retry
  logic anywhere to recover from it. Removed the SERIALIZABLE isolation;
  re-ran the new test 3x back to back with no failures. Full suite (33
  tests) passes via `./mvnw test`. Next session: still open on the roadmap -
  no controller-level tests exist yet (AuthController/EventController/
  OrderController), and refunds/cancellations + admin endpoints are still
  unstarted.
