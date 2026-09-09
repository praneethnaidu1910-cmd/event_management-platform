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
- Tests: unit coverage for GlobalExceptionHandler (7 tests, including
  validation error mapping), and an event search endpoint
  (keyword/category/location/date filters) with both a mocked-repository
  unit test and a real H2-backed query test.
- Tests: MockMvc coverage for AuthController (register/login, including
  the 400-on-invalid-payload path) - the first controller-level tests
  in the project.
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
- 2026-09-09, evening, `daily/2026-09-09`: Picked up item (b) from this
  morning's notes: controller-level tests for `EventController` and
  `OrderController`, using the same `@WebMvcTest` + MockMvc pattern as
  `AuthControllerTest`, with `spring-security-test`'s `@WithMockUser` added
  to `pom.xml` for the authenticated cases. While wiring these up, tried
  importing a nested `@EnableMethodSecurity` config into the slice so the
  `@PreAuthorize` role checks could be verified end-to-end - that broke
  `RequestMappingHandlerMapping`'s handler detection entirely (every
  request fell through to the static-resource handler and came back as a
  500), a real Spring Security/WebMvcTest interaction quirk, not something
  worth fighting for a small session. Backed that out; these tests cover
  request mapping, validation, and exception translation, not the
  `@PreAuthorize` enforcement itself (that lives in `SecurityConfig` and
  would need a full `@SpringBootTest` context to exercise honestly).
  Reading through the exception-handling path for this did turn up a real
  bug worth fixing on its own merits: `@PreAuthorize` denials throw
  Spring Security's `AccessDeniedException`, a different class from our
  own `exception.AccessDeniedException`, and only the latter had a
  handler - the security one fell through to the generic handler and
  came back as a 500 instead of a 403 (Spring MVC resolves it inside
  `DispatcherServlet` before `ExceptionTranslationFilter` ever sees it).
  Added a dedicated handler for it, with a direct unit test (not
  depending on the WebMvcTest slice, so it doesn't share that quirk).
  Tests: full suite green, 51 tests (`./mvnw test`). Commits: the
  `AccessDeniedException` fix, `EventControllerTest`, `OrderControllerTest`.
  Next session should pick up: (a) the `OrderService` concurrency question
  is still open from yesterday morning - needs verification against real
  Postgres or a retry-on-serialization-failure path before a concurrency
  test can be written; this touches the purchase flow, so treat it
  carefully and small. (b) If controller-level authorization coverage is
  wanted, it needs a `@SpringBootTest`-based test (full context, real
  `SecurityConfig`) rather than a `@WebMvcTest` slice, given the
  `@EnableMethodSecurity` incompatibility found today. (c) Missing backend
  features (refunds/cancellations, admin endpoints, email notifications)
  and CI/deployment are both still untouched.
- 2026-09-09, morning, `daily/2026-09-09`: Tried to add a real (H2-backed)
  concurrency test for OrderService.purchaseTickets - spinning up 6-20
  threads buying the same ticket type at once to prove the pessimistic
  lock stops overselling. It's reproducibly broken on H2:
  `Isolation.SERIALIZABLE` combined with the `PESSIMISTIC_WRITE` lock
  means only the first transaction to touch the row ever succeeds: every
  other concurrent transaction aborts with a `40001` serialization
  failure ("Deadlock detected") instead of blocking and retrying with
  the fresh row - this held regardless of thread count or how many
  tickets were available. Whether real Postgres (what the app actually
  runs on) behaves the same way for a plain single-row `SELECT ... FOR
  UPDATE` is unclear without testing against it directly, so I backed
  the test out rather than commit something flaky or misleading, and
  picked a smaller, safer piece of work instead: `GlobalExceptionHandler`
  had no handler for `MethodArgumentNotValidException`, so any `@Valid`
  failure (blank email, short password, etc.) fell through to the
  generic handler and came back as a 500 instead of a 400 - fixed that,
  and added MockMvc tests for `AuthController` (register/login) that
  exercise validation and the exception handler together over real HTTP,
  not just the service layer. Tests: full suite green, 37 tests
  (`./mvnw test`).
  Next session should pick up: (a) actually resolve the OrderService
  concurrency question - either verify against a real Postgres instance
  whether SERIALIZABLE + FOR UPDATE really causes this on the same
  engine the app deploys to, or add a retry-on-serialization-failure
  path to `purchaseTickets` and then write the concurrency test against
  that; this touches the purchase flow so treat it carefully and small.
  (b) Controller-level tests for `EventController` and `OrderController`
  are still missing - same MockMvc + `@WebMvcTest` pattern used here for
  `AuthController`, but those two are behind auth (`anyRequest().authenticated()`
  in `SecurityConfig`), so the JWT filter can't just be excluded from the
  slice like it was for the public auth endpoints - will need either
  `@MockBean` for `JwtTokenProvider`/`UserDetailsServiceImpl` or a
  `@WithMockUser`-style approach (would need the `spring-security-test`
  dependency, not currently in `pom.xml`).
