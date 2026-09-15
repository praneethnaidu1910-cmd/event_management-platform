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
- 2026-09-15, evening, `daily/2026-09-15`: Checked in on the morning
  session's finding before writing any code. Verified independently:
  `origin/main` is still at 9e087d1 (2026-08-28), no PR is open against it,
  and this branch carries nothing but the morning's note on top of `main`.
  The blocker is unchanged and still needs the human decision described
  below (pick a base branch - `daily/2026-09-14` looks like the strongest
  candidate per the morning entry - cherry-pick the pagination/CI/Docker/
  concurrency work that lives only on other branches, open a PR by hand,
  merge it, then archive the stale `daily/*` branches). Writing more
  feature code on top of stale `main` right now would only produce a 19th
  branch with its own copy of the same fixes, so this session made no code
  changes and pushed only this log entry. Tomorrow morning: check whether
  `main` has moved: if the consolidation PR has merged, branch off the new
  `main` and resume the roadmap normally (refunds/cancellations, admin
  endpoints, purchase email are the next unclaimed items per the roadmap
  above); if `main` is still stale, repeat this check rather than adding a
  20th branch.
- 2026-09-15, morning, `daily/2026-09-15`: No code changes this session -
  found a structural problem with the automation setup that needs a human
  decision before more code should be added. `main` is still at 9e087d1
  (2026-08-28) - none of the 17 `daily/*` branches since then
  (2026-08-29 through 2026-09-14) have been merged, and per this file's own
  instructions each morning run branches fresh off `main`, not off the
  previous day's branch. The result: 18 days of twice-daily sessions have
  been re-solving the same handful of problems independently, over and
  over, on branches that don't know about each other:
  - Mapping Spring Security's `AccessDeniedException`/validation errors to
    403/400 instead of a raw 500 - done on at least 8 separate branches
    (08-31, 09-01, 09-03, 09-04, 09-06, 09-07, 09-08, 09-09, 09-11, 09-12).
  - MockMvc controller-layer tests for Auth/Event/OrderController - done
    independently on at least 9 branches (08-31, 09-01, 09-03, 09-04,
    09-06, 09-07, 09-08, 09-09, 09-12, 09-13).
  - Order cancellation - implemented 3 separate times (08-30, 09-02, 09-14).
  - A GitHub Actions CI workflow - added twice (09-05, 09-10).
  - Purchase confirmation email - done once (09-05) but the roadmap section
    above still lists it as open, because that knowledge never made it
    back to `main`.
  - Dockerfile/docker-compose (09-10), event/search pagination (09-11), and
    a couple of concurrency fixes to the ticket-purchase path (09-03,
    09-05, 09-08) each exist on exactly one branch and would be lost if
    that branch is never looked at.
  The 2026-09-13 morning session noticed and noted the staleness problem
  in its own log entry, but since that note only lived on `daily/2026-09-13`
  (never merged), it didn't reach `main` or change what later sessions saw,
  and two more branches (09-13 evening, 09-14 morning/evening) were added
  on top of stale `main` anyway.
  I did not add another branch's worth of duplicate work on top of this.
  What's needed is a human decision: pick a base (my read is that
  `daily/2026-09-14` is the most feature-complete single branch - it's the
  only one with order cancellation *and* order history/admin listing - but
  useful work such as pagination, CI, Docker, and the concurrency fixes
  live only on other branches and would need to be cherry-picked in), open
  a PR against `main`, merge it, and then either delete or archive the
  remaining stale `daily/*` branches so they stop showing up as candidates
  to build on. Once `main` reflects real progress, automated sessions
  should go back to branching off it normally. No tests were run since no
  production code changed.
