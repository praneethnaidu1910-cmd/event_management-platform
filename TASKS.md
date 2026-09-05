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
- 2026-09-05, evening, `daily/2026-09-05`: Picked up where this morning's
  session left off - two focused pieces of work, both scoped away from the
  duplicated admin-endpoints/refunds and controller-exception-handling work
  sitting unmerged in the `daily/2026-08-29` through `daily/2026-09-04`
  branches (still unreviewed; see this morning's entry below).

  1. Added a GitHub Actions CI workflow (roadmap item 3) - builds against
     JDK 17 and runs `./mvnw test` on every push to `main` and every pull
     request, plus a skip-tests `package` step so a compile-only break in
     the jar build itself would also be caught. Added a status badge to
     the README. Docker was the other half of item 3, and this environment
     actually had a working `dockerd` (unlike this morning's sandbox), but
     `docker pull` for any base image (temurin, alpine) came back 403 from
     the registry's blob CDN - this sandbox's network policy doesn't allow
     it - so a Dockerfile still couldn't be built or run to verify it
     actually works. Left undone rather than committing an untested one;
     next session should retry in case the network policy differs, or get
     a human to confirm Docker Hub access is intentionally unavailable
     here before working around it another way.

  2. While adding a concurrency test for the ticket purchase flow (roadmap
     item 1 - the existing OrderServiceTest only exercises purchaseTickets()
     against a mocked repository, so it never actually ran the
     PESSIMISTIC_WRITE lock findByIdForUpdate() relies on), found a real
     bug: with 20 buyers racing for 5 tickets against a real database,
     purchases started failing with "Deadlock detected" instead of either
     succeeding or cleanly reporting sold-out. OrderService.purchaseTickets()
     combined that row lock with `@Transactional(isolation = SERIALIZABLE)`,
     and under real contention the database was rejecting transactions
     outright rather than queuing them behind the lock like a plain
     PESSIMISTIC_WRITE transaction does - the lock alone is what prevents
     overselling, so the extra isolation was redundant and, under load,
     would have shown up as a failed purchase for a buyer who should have
     succeeded. Dropped back to the default isolation level; the new test
     passes reliably across repeated runs.

  Tests: 35/35 passing (`./mvnw test`), run three times to check the new
  concurrency test isn't flaky. Next: the branch-review/merge backlog from
  this morning's entry is still open and getting more relevant, not less,
  every day it's not addressed. Once that's caught up on, remaining
  roadmap item 2 work (refunds/cancellations, admin endpoints) already
  exists in `daily/2026-08-30` and `daily/2026-09-02` - review those before
  anyone writes a third copy. Docker (see above) is still blocked here.

- 2026-09-05, morning, `daily/2026-09-05`: Added a purchase confirmation
  email (roadmap item 2) - a new NotificationService sends a plain-text
  confirmation via spring-boot-starter-mail once OrderService.purchaseTickets()
  commits, with send failures caught/logged rather than propagated so a
  down SMTP server can't fail an already-successful purchase. Also
  re-applied the `chmod +x mvnw` fix (missing again on a fresh checkout
  from main). Tests: 34/34 passing (`./mvnw test`).

  Important gap found this session, flagging for a human decision rather
  than working around it: none of the `daily/2026-08-29` through
  `daily/2026-09-04` branches (7 days, 14 sessions) have been merged into
  `main`. Each one forked from `main` fresh and, not knowing about the
  others, independently re-discovered and re-fixed the same two bugs
  (GlobalExceptionHandler returning 500 instead of 403 for @PreAuthorize
  denials, and 500 instead of 400 for @Valid failures) and re-wrote
  overlapping controller test suites multiple times - e.g. `daily/2026-09-01`,
  `daily/2026-09-03`, and `daily/2026-09-04` each contain their own version
  of essentially the same fix and similar MockMvc tests. Order
  cancellation/refund and admin endpoints (roadmap item 2) were also already
  built, independently, in both `daily/2026-08-30` and `daily/2026-09-02`.
  None of this is wasted in the sense of being wrong - each branch's tests
  pass on its own - but the duplication means real engineering time went
  into rebuilding the same thing several times instead of compounding.
  This branch was deliberately scoped away from all of that (email
  notifications, untouched by every prior branch) to avoid adding an 8th
  duplicate. Recommend reviewing/merging the most complete of the existing
  daily branches (`daily/2026-09-04` looks most advanced for the
  tests/exception-handling work) before more automated sessions run,
  otherwise this will keep happening. Next session: once caught up on
  review, admin endpoints and Dockerizing (roadmap items 2/3) are still
  open; Docker couldn't be attempted/verified today since no Docker daemon
  was available in this sandbox to build or run an image against.
