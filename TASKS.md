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
- 2026-09-02, evening, `daily/2026-09-02`: Continued on top of this
  morning's session (CurrentUserService and JwtTokenProvider expired-token
  tests). Given the choice the morning entry left open - port the
  controller/PreAuthorize work sitting on `daily/2026-09-01` or start
  roadmap item 2 - went with item 2, since none of the daily branches have
  been merged yet and duplicating that controller-test work risked
  conflicting with `daily/2026-09-01` once it does get reviewed. Also
  treated this as ticket-inventory-adjacent (it mutates the same
  `TicketType.available` counter as purchase) rather than as the
  lower-risk pick, so it got the same pessimistic-locking/SERIALIZABLE
  treatment as the purchase path and thorough tests, not a shortcut.
  Implemented order cancellation/refund: `POST /api/orders/{id}/cancel`
  for the ATTENDEE who placed the order. It locks the order row and each
  affected ticket type (`OrderRepository.findByIdForUpdate`, reusing the
  existing `TicketTypeRepository.findByIdForUpdate` pattern), marks every
  ticket on the order CANCELLED, sets the order's paymentStatus to
  REFUNDED, and adds the cancelled quantity back to each ticket type's
  available count. Guards: only the buyer can cancel their own order
  (403), an already-refunded order can't be cancelled again (400), and
  cancellation is blocked once the event's start date has passed (400).
  `OrderResponse` now includes `paymentStatus` so purchase and cancel
  responses both show COMPLETED/REFUNDED without a second lookup. Five
  new OrderServiceTest cases cover the happy path plus each guard rail
  (wrong owner, already cancelled, event already started, order not
  found). Full suite green (43 tests, up from 38). Did not touch
  admin endpoints or email notifications from item 2 - those are still
  open. Next session: pick up either the remaining roadmap item 2 work
  (admin endpoints, email notification on purchase - the latter needs a
  decision on what mail sending mechanism to use, e.g. Spring Mail vs. a
  stub/log-only sender for a portfolio project without a real SMTP
  provider) or revisit the `daily/2026-08-29` through `daily/2026-09-01`
  backlog once a human has had a chance to review/merge some of them, so
  future runs stop branching off the same stale `main`.
- 2026-09-02, morning, `daily/2026-09-02`: Note first - branches
  `daily/2026-08-29` through `daily/2026-09-01` are pushed to origin but
  none have been opened as PRs or merged into `main` yet, so `main` is
  still at the state from PR #2 (GraphQL query layer) and this log was
  empty on `main` even though those branches' own copies of this file
  have entries. Per the daily-branch instructions this run created
  `daily/2026-09-02` fresh off `main` rather than off `daily/2026-09-01`,
  so it doesn't have that branch's controller-test/PreAuthorize-bugfix
  work - that's all still waiting in its own unmerged branch. Worth
  reviewing/merging the backlog of daily branches soon so future runs
  build on top of each other instead of on `main` as of PR #2.
  This session continued roadmap item 1 with tests that had no coverage
  on `main`: CurrentUserService.getCurrentUser() (authenticated lookup,
  no Authentication in the context, an unauthenticated Authentication,
  and an authenticated principal with no matching user row - the last
  three all throw and had nothing pinning that), and two expired-token
  cases for JwtTokenProvider (validateToken and getUserIdFromToken both
  surface ExpiredJwtException) that were called out as a gap in an
  earlier note but hadn't been added yet. Also fixed a stale comment on
  JwtTokenProviderTest's malformed-token test claiming a bad
  Authorization header 500s the request - JwtAuthenticationFilter already
  catches that (JwtAuthenticationFilterTest covers it), so the comment
  was just wrong. Full suite green (38 tests). Next session: either close
  out roadmap item 1 on `main` by porting/redoing the AuthController/
  EventController/OrderController MockMvc tests and the @PreAuthorize
  AccessDeniedException fix that already exist on `daily/2026-09-01` (once
  someone confirms that branch isn't about to be merged, to avoid
  duplicate work), or start roadmap item 2 (refunds/cancellations, admin
  endpoints, email notification on purchase) - if picking refunds/
  cancellations, treat it like auth code given it touches the same
  transactional ticket-inventory logic as purchase, and write tests
  alongside the implementation.
