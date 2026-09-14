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
- 2026-09-14, evening, `daily/2026-09-14`: Added order history endpoints -
  `GET /api/orders/me` (any authenticated user, their own orders) and
  `GET /api/orders` (ADMIN-only, all orders) - closing part of the
  item-2 "admin endpoints" gap. `OrderResponse` now carries eventId,
  eventTitle, paymentStatus, and createdAt so a listing is useful without
  a follow-up call per order; `OrderRepository` gained
  `findByUserIdOrderByCreatedAtDesc`/`findAllByOrderByCreatedAtDesc`
  (replacing the unused `findByUserId`). Added 3 new OrderServiceTest
  cases for the two new read paths. Full suite: 40/40 passing. Combined
  with this morning: order cancellation plus now order history/admin
  listing are both in. Next: email notification on purchase is still
  open from item 2, then CI/Docker/deployment (item 3). Per-ticket
  partial refunds within an order remain out of scope for now.
- 2026-09-14, morning, `daily/2026-09-14`: Added order cancellation
  (`OrderService.cancelOrder`, `POST /api/orders/{id}/cancel`). Restores the
  ticket type's available count under the same pessimistic-lock read the
  purchase flow uses, marks the order and its tickets CANCELLED, and is
  restricted to the order's owner or an ADMIN. Added 5 new OrderServiceTest
  cases (happy path, admin override, non-owner rejection, already-cancelled
  rejection, order-not-found). Full suite: 37/37 passing. Next: this only
  covers whole-order cancellation - partial/per-ticket refunds within an
  order are still unhandled, and there's no refund/payment-reversal step
  since payment is just a status flag. Otherwise still open on the roadmap:
  remaining item-2 backend features (admin endpoints, email notification on
  purchase), then CI/Docker/deployment.
