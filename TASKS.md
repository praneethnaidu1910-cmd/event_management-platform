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
- 2026-09-10, evening, `daily/2026-09-10`: Continued from this morning's
  session on this same branch (Dockerfile + docker-compose). First
  followed up on the morning log's item (c): this sandbox actually has
  a working Docker daemon (unlike the morning's, which had none at all),
  so `docker compose up --build` got further, but image pulls
  (`eclipse-temurin:17-jre-jammy`, `postgres:16-alpine`) both fail with
  a 403 from the org's egress policy on `production.cloudfront.docker.com`
  (Docker Hub's blob CDN) - confirmed with a direct `docker pull` of the
  base image alone, and via the proxy's own status endpoint, which logs
  it as a policy denial rather than a transient failure. So the
  Dockerfile/compose setup itself still isn't end-to-end verified, but
  now for a different, more precise reason than "no Docker daemon" -
  worth knowing before a future session burns time retrying the same
  pull. Did not attempt to route around the block.
  Picked three items instead, deliberately avoiding the roadmap work
  already duplicated across the unmerged backlog (controller-layer
  MockMvc tests for Auth/Event/Order show up in eight separate branches;
  refunds, admin stats, and email notifications each show up in at least
  one): (1) added the GitHub Actions CI workflow (build + `mvnw test`)
  from the morning log's item (b) - this one does duplicate
  `daily/2026-09-05`'s unmerged version, but it's a single small file and
  worth having on this branch regardless of which copy a human keeps.
  (2) Added `SecurityConfigTest`, a `@SpringBootTest` + MockMvc test that
  exercises the real filter chain (JWT filter, `@PreAuthorize`, H2 in
  place of Postgres) instead of mocking security away, since nothing
  existing verified the URL rules and role checks end to end. It
  immediately caught two real bugs - a `@PreAuthorize` denial and a
  failed `@Valid` validation both returned 500 instead of 403/400,
  because `GlobalExceptionHandler` had no handler for Spring Security's
  `AccessDeniedException` or for `MethodArgumentNotValidException` and
  both fell through to the generic 500 case. Fixed both by adding the
  two missing handlers; no authorization logic changed. (These are the
  same two bugs the morning log flagged as independently fixed in three
  or more other unmerged branches - this branch found them a different
  way, through an integration test rather than controller unit tests,
  so it's still adding to that duplicate pile rather than resolving it;
  a human merging one branch's fix should be able to drop the other
  branches' equivalent hunks as a no-op.) Deliberately left the
  403-vs-401 status for a missing/invalid token as is (Spring Security's
  default `Http403ForbiddenEntryPoint`, no custom entry point
  configured) rather than reconfiguring `SecurityConfig` for it - that's
  a legitimate default, not a bug, and changing it would have meant
  touching `SecurityConfig` itself for a cosmetic status-code preference.
  (3) Fixed a real, previously-unvalidated gap unrelated to the above:
  `EventService.createEvent`/`updateEvent` accepted any
  startDate/endDate pair, including an end date before the start date.
  Added a `BadRequestException` check, run before `updateEvent` mutates
  the existing entity so a bad request can't partially clobber it first.
  Test status: full suite green, 43 tests (`./mvnw test`, JDK 17 -
  installed via `apt-get install openjdk-17-jdk` since only JDK 21 was
  preinstalled here).
  Next session should pick up: (a) still the standing recommendation -
  a human merging even one or two of the twelve now-unmerged daily
  branches would unblock a lot of redundant future work; this session's
  CI workflow and security-handler fix both duplicate existing unmerged
  work for exactly that reason. (b) If no merge has happened yet, avoid
  re-adding controller-layer MockMvc tests for Auth/Event/Order (eight
  branches already have a version) and re-adding refunds/admin
  stats/notifications (each already has one version) - look instead for
  gaps like the two found today (SecurityConfig integration coverage,
  event date validation) that no branch has touched yet, e.g. ticket
  type price/quantity validation, or pagination on `GET /api/events`.
  (c) Don't retry `docker compose up --build` in this kind of sandbox -
  it's blocked by network policy, not something a retry or workaround
  fixes; it needs an environment whose egress policy allows Docker Hub,
  or a registry mirror the policy does allow.
- 2026-09-10, morning, `daily/2026-09-10`: Before picking up new work,
  noticed a systemic problem worth flagging: none of the eleven daily
  branches from 2026-08-29 through 2026-09-09 have been merged into
  `main` yet, and since each morning session branches fresh off `main`
  (as designed), a lot of those branches ended up independently
  re-solving the same handful of gaps - controller-layer MockMvc tests
  for Auth/Event/Order, the `@PreAuthorize`-denial-returns-500 and
  `@Valid`-failure-returns-500 exception-handler fixes, and a
  ticket-purchase concurrency test all show up in three or more
  separate branches. None of that duplicated work is wasted exactly
  (each branch is a real, reviewable candidate PR), but it means
  `main` and this log are both behind what's actually been built -
  reviewing and merging some of those branches (or at least deciding
  which one's version of each fix to keep) would make future sessions
  more useful than starting from the same stale base every day.
  Given that, picked something not attempted in any prior branch:
  roadmap item 3 (deployment/devops), starting with just the Docker
  packaging piece rather than CI or an actual deploy. Added a
  multi-stage `Dockerfile` (Temurin JDK to build, Temurin JRE to run
  as a non-root user) and a `docker-compose.yml` pairing the app with
  `postgres:16-alpine`. `schema.sql`'s leading `CREATE DATABASE`
  statement doesn't fit the official Postgres image's init-script
  convention (POSTGRES_DB already creates the database, so a second
  CREATE DATABASE aborts the init script before the tables get
  created), so added `db/docker-entrypoint-initdb.d/apply-schema.sh`,
  which strips that one line and applies the rest, rather than forking
  a second copy of the schema. Also fixed `mvnw` being committed
  without its executable bit (every prior branch had to `chmod +x` it
  separately - never made it back into `main`).
  This sandbox has no Docker daemon (`docker compose build` fails to
  reach `/var/run/docker.sock`, and `dockerd` won't start here either),
  so `docker compose up` itself is unverified. Verified as much of the
  chain as possible without it: built the jar the same way the Docker
  build stage does (`mvnw package -DskipTests`), ran the same
  grep-and-pipe the init script uses against a real local Postgres 16
  to apply the (stripped) schema, then ran the jar against that
  database - `GET /api/events` returned 200. `docker compose config`
  validates the compose file. Test status: full suite green, 32 tests
  (`./mvnw test`) - unchanged by this session, no Java source touched.
  Next session should pick up: (a) ideally a human reviews and merges
  at least one or two of the backlog of unmerged daily branches so
  `main` stops being stuck at 2026-08-28 - that's a bigger unlock than
  anything an automated session can do alone. (b) If continuing the
  deployment item directly: add the GitHub Actions CI workflow (build +
  `mvnw test`) - note `daily/2026-09-05` morning already has one
  version of this unmerged. (c) Actually run `docker compose up --build`
  somewhere with a working Docker daemon to confirm the app container
  boots end-to-end, since this session could only verify the pieces
  separately. (d) Missing backend features (refunds/cancellations,
  admin endpoints, email notifications) also have unmerged prior
  attempts (`daily/2026-08-30`, `daily/2026-09-02`, `daily/2026-09-05`).
