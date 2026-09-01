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
- 2026-09-01, morning, `daily/2026-09-01`: Added MockMvc controller tests for
  AuthController (register/login - success, duplicate email, invalid input).
  Writing them surfaced a real bug: `@Valid` failures on request bodies had
  no dedicated exception handler and fell through to the generic 500
  handler instead of returning 400, so fixed that in GlobalExceptionHandler
  and added a unit test for it. Full suite green (38 tests). Next session
  should keep going on roadmap item 1: EventController and OrderController
  have no controller-level tests yet (only service-level), and
  OrderController's purchase endpoint is the highest-value target given the
  overselling risk in that flow - though it needs an authenticated
  principal in the MockMvc setup, unlike AuthController's permitAll
  endpoints, so a bit more test scaffolding.
