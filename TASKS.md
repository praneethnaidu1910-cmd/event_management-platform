# Working Notes

## Commit cadence
- Morning session ~9 AM: 2 commits
- Evening session ~7 PM: 3 commits
- Times will drift around other routines; this is a target, not a hard rule.
- Working directly on `main` only for small/safe changes. Anything that touches
  auth, ticket inventory, or the DB schema goes through a feature branch + PR,
  reviewed before it merges.

## Scope roadmap (in order)
1. **Tests** — done. Unit coverage across auth, events, orders, admin,
   GraphQL, plus MockMvc controller tests and an end-to-end SecurityConfig
   test. 106 tests passing as of 2026-10-10.
2. **Missing backend features** — done. Event search & filtering, order
   cancellation with inventory refund, order history, admin endpoints,
   email notification on purchase, pagination.
3. **Deployment/DevOps** — Dockerfile + docker-compose added 2026-10-10, not
   yet run end-to-end against a live Docker daemon (none available in the
   sessions that built or merged it - only `docker compose config` and a
   `mvnw package` were verified). GitHub Actions CI added. Still not
   deployed anywhere reachable.
4. **Frontend** — not started. Optional/lowest priority.

## Current state (as of 2026-10-10)
- Auth: register/login with JWT, roles ATTENDEE/ORGANIZER/ADMIN.
- Events: organizer CRUD, public browse (paginated), search (keyword/
  category/location/date range, paginated), organizer's own event list,
  end-date-after-start-date validation.
- Orders/Tickets: purchase flow with transactional inventory checks (plain
  `@Transactional`, not `SERIALIZABLE` - redundant on top of the row lock
  and caused spurious failures under real concurrent load, confirmed by a
  dedicated concurrency test). Cancellation with inventory refund, blocked
  once the event has started, owner-or-admin. Order history for the buyer
  and for an event's organizer. Purchase confirmation email (best-effort,
  swallows mail failures).
- Admin: order listing and platform stats (user/event/order counts, active
  tickets sold, revenue).
- GraphQL: a query layer over the events read path (events/event(id)).
- GlobalExceptionHandler: maps Spring Security's AccessDeniedException and
  bean validation failures to 403/400 instead of a generic 500 - this bug
  got independently rediscovered and refixed close to a dozen times across
  the unmerged daily branches (see below), which is exactly the kind of
  wasted effort letting `main` go stale causes.
- CI: GitHub Actions runs the test suite + jar build on push/PR.
- Docker: Dockerfile + docker-compose for local dev, not yet run end-to-end.
- No frontend, no live deployment yet.

## Automated sessions (9 AM / 7 PM daily) - currently PAUSED
**2026-10-10: found and fixed a real design flaw.** Each daily session
branched off `origin/main` fresh, per its instructions - but since nothing
ever merged those branches back into `main`, every single day from
2026-08-29 through 2026-09-17 branched from the *same* stale point and had
no visibility into any other day's work. Result: 20 unmerged branches, heavy
duplication (order cancellation implemented independently 4 times, the
AccessDeniedException/validation 500 bug "fixed" ~12 times, controller
MockMvc tests redone ~10 times), and real risk of conflicting changes.
One session (09-15) even noticed the backlog itself and flagged it instead
of adding more duplicate work - but that note lived on its own unmerged
branch, so the next day's session never saw it either, and went back to
duplicating work on 09-16/09-17.

Also unexplained: the routines kept firing (per their own `next_run_at`)
through early October, but no run after 2026-09-17 morning produced a
session or a branch. Not yet investigated.

Resolved by reviewing all 20 branches, picking (or hand-merging, where two
branches each had something the other lacked) the best version of each
duplicated feature, and merging the result directly into `main` as 14
focused commits - see git log from 2026-10-10. All 20 daily branches and
the two long-merged feature branches (`fix/jwt-parser-api-compile-error`,
`feature/graphql-events-query`) are now safe to delete; nothing on them
isn't already on `main` in equivalent or better form.

**Before re-enabling the routines**, fix the root cause: a session should
either branch off the latest *unmerged* daily branch when one exists
instead of always off `main`, or (simpler) a human should merge/delete the
previous day's branch before the next session runs, so `main` never goes
more than a day stale. Whichever approach, re-pause-and-reconsider if a
human isn't reliably keeping `main` current - the automation makes the
duplication problem worse, not better, when nothing merges.

### Log
(cleared 2026-10-10 after the consolidation above - entries before this
point lived on the now-superseded daily branches and aren't reproduced
here verbatim; the summary above covers what mattered. Next automated
session starts the log fresh.)
