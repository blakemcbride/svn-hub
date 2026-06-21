# SvnHub — Architecture

SvnHub is a self-hosted, GitHub-like web service for **Subversion** (not Git). It provides
repository hosting, browsing, history/diffs, issues, merge requests, and — its differentiator —
**per-user checkout/update statistics** that a centralized SVN server can produce but distributed
Git platforms cannot. It deliberately omits GitHub's Agents, Actions (CI/CD), Projects (kanban),
and Security & Quality features.

It is built on the **Kiss** Java/Groovy full-stack framework. This document describes the whole
system; `AI/ApplicationDetails.md` is the quick orientation, `AI/KnowledgeBase.md` is the Kiss
framework reference, and `Plan.md` is the original design.

---

## 1. High-level shape

```
        Browser (SPA: login / register / repos / insights / issues / MRs / admin)
                                   │  JSON-RPC over HTTP  (POST /rest)
                                   ▼
        ┌──────────────────────────────────────────────────────────────────┐
        │  Kiss backend (Tomcat 11, Java 17/21 + Groovy services)            │
        │                                                                    │
        │   Groovy services ──► RepoAccess / SvnRepo / SvnAuthManager        │
        │        │                    │            │           │             │
        │        ▼                    ▼            ▼           ▼             │
        │   PostgreSQL          SVNKit (file://)   svnserve auth files       │
        │   (app + stats)       browse/admin/merge passwd / authz / conf     │
        └──────────────────────────────────────────────────────────────────┘
              ▲                          │                        │
              │ parse --log-file         │ create/read/merge      │ writes
              │ (IngestSvnLogs cron)     ▼ repos on disk           ▼
        svnserve  ◄── svn checkout/update/commit ──►  /home/repos (FSFS repos)
        (svn:// , -r /home/repos, --log-file)         <handle>/<name> per project
```

Two **independent** SVN touch-points — both are required and serve different roles:

- **SVNKit** (pure-Java, `file://`) is how *SvnHub itself* reads, administers, and merges repos.
- **svnserve `--log-file`** is how SvnHub learns what *other developers* did (checkout/update/
  commit). SVNKit cannot observe other clients' reads — only the server log can. SvnHub-performed
  merges go over `file://` and therefore do **not** appear in the svnserve log.

---

## 2. Technology stack

| Layer | Choice |
|---|---|
| Backend | Java 17/21, **Groovy** services (hot-reload), Kiss framework, embedded **Tomcat 11** |
| Database | **PostgreSQL** (role/db `svnhub`) — app data + the statistics firehose |
| SVN access (app) | **SVNKit 1.10.11** (+ sqljet, sequence-library, antlr-runtime, lz4-java) over `file://` |
| SVN serving (clients) | **svnserve** `-r <SvnReposRoot>` (svn:// protocol), `--log-file` enabled |
| Frontend | Kiss custom components (`<text-input>`, `<drop-down>`, AG-Grid, popups), vanilla JS IIFE per screen |
| Frontend libs | marked (README), highlight.js (code), diff2html (diffs), Chart.js (loaded; charts currently HTML/CSS) |
| Build | Kiss `./bld` (no Maven/Gradle); `libs/*.jar` copied into the webapp |

---

## 3. Repository storage model (important)

**One FSFS repository per project**, namespaced under the owner's handle:

```
<SvnReposRoot>/                 (svnserve root — plain directory)
  alice/                        (a user's handle — plain directory, NOT a repo)
    utils/                      (an independent SVN repository: db/, format, uuid)
    website/                    (another independent repository)
  bob/
    utils/                      (a different repository — different uuid/revisions)
```

- `repository.repo_key` is the two-segment string **`"<handle>/<name>"`** and is globally unique.
- On disk: `fs_path = <SvnReposRoot>/<repo_key>`. URL: `svn://host/<repo_key>`. svnserve resolves a
  nested path to its repository root, so two-level nesting works with a single `svnserve -r`.
- Because `repo_key` is globally unique, **two different users can each have a repo named `utils`**,
  and all code that does `root + "/" + repo_key` (disk), `base + "/" + repo_key` (URL), or maps the
  svnserve-log "repos" field → repository row works unchanged.

**Consequences of one-repo-per-project (deliberate design):**
- **Per-project revision numbers** — each repo has its own sequential counter from r0. No
  interleaving or "skyrocketing" across projects.
- **Cheap checkout/clone** — a checkout transfers only that one project's files.
- **Cheap user deletion** — removing a user is `rm -rf <root>/<handle>` plus DB rows.
- **Expensive cross-user forks** — SVN cheap-copy (`svn copy`, copy-on-write) only works *within
  one repository*. Copying a project into another user's namespace requires a full
  `svnadmin dump | svnadmin load`, O(repo size). This trade-off is accepted: independent revision
  numbers require separate repos; cheap forks require a shared repo — SVN cannot give both.
  (Cross-user fork is a planned future feature, implemented as a backgrounded dump/load.)

---

## 4. Data model (PostgreSQL — `schema.sql`)

Conventions: timestamps are `bigint` epoch-ms (the frontend `DateTimeUtils.formatDate()`
auto-detects epoch ms); day buckets are integer `YYYYMMDD`; booleans are `char(1)` `'Y'/'N'` with
CHECK constraints.

**Identity & repositories**
- `users` — `user_name` (login id; for self-registered users = email, UNIQUE), `handle` (URL-safe
  public username / namespace, UNIQUE), `user_password` (PBKDF2 login hash), `svn_password` (clear,
  for svnserve passwd), `full_name`, `email`, `is_admin`, `user_active`, `email_verified` (`'Y'` once
  the user confirms their email via a code).
- `verification_code` — short-lived, single-use 6-digit codes for email verification and
  password reset: `(user_id, purpose)` UNIQUE (`purpose` = `email_verify` | `password_reset`),
  `code` (`char(6)`, zero-padded), `expires_ts`, `attempts`, `created_ts`.
- `repository` — `repo_key` (`handle/name`, UNIQUE), `name`, `fs_path`, `owner_id`, `visibility`
  (`public`/`private`), `description`, `default_branch`, `discovered`, `is_active`, `head_revision`
  (+ts), `created_ts`.
- `repository_access` — `(repo_id, user_id)` UNIQUE, `can_read/can_write/can_admin`. The source of
  truth serialized into svnserve `authz`.
- `svn_user_alias` — maps a raw SVN realm name in the log → `user_id` (nullable until reconciled).

**Statistics (the differentiator)**
- `access_event` — the raw firehose: one row per logged SVN operation (`action`, `verb_class`,
  `path`, `revision`, `event_ts/day`, `client_host`, `repo_id`, `user_id`/`raw_user`, …) with a
  SHA-256 `event_hash` (UNIQUE) for idempotent ingest.
- `log_ingest_state` — per physical log file cursor (`inode` via `Files.fileKey()`, `byte_offset`,
  rotation-safe) so ingest is incremental.
- `access_daily_rollup` — per `(repo, user, day)` aggregate counts (checkout/update/switch/browse/
  commit/other) for fast charts.
- `working_copy_state` — per `(repo, user)` "last sync" position (last-synced revision, last
  activity) — the freshness signal.

**Browse cache**
- `commit_cache` / `commit_cache_path` — cached revision metadata + changed paths.

**Collaboration**
- `issue` / `issue_comment` — per-repo numbered issues + threaded comments.
- `merge_request` / `mr_comment` — source→target merge proposals + (optionally line-anchored) comments.

---

## 5. Backend services (`src/main/backend/services/`, Groovy, hot-reload)

All follow the Kiss signature `void method(JSONObject in, JSONObject out, Connection db,
ProcessServlet servlet)` and are called by JSON-RPC as `services/<Class>` + method. Authentication
is enforced by the framework; the current user is `servlet.getUserData().getUserId()`.

| Service | Responsibility |
|---|---|
| `Register` | Public self-registration (no-auth; allow-listed in `KissInit.groovy`). Validates email + handle, creates an **unverified** regular user, and emails a 6-digit verification code (Postmark). |
| `AccountService` | Authenticated self-service: `verifyEmail`/`resendVerification` (confirm the email with the emailed code), `changePassword` (verifies current, updates the PBKDF2 hash + `svn_password`, regenerates svnserve passwd), `status`. |
| `PasswordResetService` | Forgotten-password flow (no-auth; allow-listed): `requestReset` (emails a code; never reveals whether the account exists) and `resetPassword` (validates the code, sets a new password). |
| `Users` | Admin-only account management (list/add/update/delete), including handle, admin flag, and SVN password; regenerates svnserve passwd. |
| `RepositoryService` | Create repos (SVNKit) under the owner's handle namespace; list owned/granted (`getRepositories`); keyword Explore (`searchRepositories`); update; admin disk `scanRepositories`. |
| `DiscoverService` | GitHub-style discovery: `searchUsers` (people directory — handle/name/public-repo-count, no emails), `searchRepos` (public repos by name/description/key), and `getProfile` (a user's public repos; private ones too if the viewer is the owner or an admin). All searches are case-insensitive substring matches and paginated (`page`/`pageSize` in, `total` out). |
| `RepositoryAccessService` | Per-repo grant/revoke + set SVN password; rewrites `authz`/`passwd` on every change. |
| `BrowseService` | Read-only `listDir` / `cat` / `readme` via SVNKit. |
| `HistoryService` | `log` / `revisionDetail` (changed paths + diff) / `diff`. |
| `StatsService` | The Insights metrics: `insights` (date-ranged repo + activity + branches/tags/clones), plus freshness/heatmap/etc. |
| `IssueService` | Per-repo issues: list/get/create/comment/setStatus. |
| `MergeRequestService` | Merge requests: list/get/create/diffPreview/comment/approveAndMerge (real SVNKit merge+commit)/close. |

(The repo still contains Kiss template/demo services — `Crud`, `FileUpload`, `MyGroovyService`,
`OllamaQuery`, `xxx` — and demo screens; these are not part of SvnHub.)

### Precompiled helpers (`src/main/precompiled/com/svnhub/`, Java)
Always-loaded (require `./bld build` + restart). Used by the hot-reloaded Groovy services so they
don't depend on cross-Groovy-service static calls.

- `SvnRepo` — SVNKit wrapper: create repo, listDir/cat, log/diff, revision date, file count, **merge**.
- `SvnAuth` — pure builders for svnserve `passwd` / `authz` / `svnserve.conf` (string in/out, unit-testable).
- `SvnAuthManager` — regenerates those files from the DB (`repository_access` + `users`).
- `RepoAccess` — centralized `canRead/canWrite/canAdmin` + `isAdmin` (admin ⇒ full access; public ⇒ readable).
- `SvnLogParser` — parses the svnserve `--log-file` line format (unit-tested: `src/test/core/com/svnhub/SvnLogParserTest.java`).
- `Json` — converts Java collections (from SVNKit) → Kiss JSON.
- `Mailer` — sends transactional email via Postmark's REST API (`RestClient` POST to
  `https://api.postmarkapp.com/email`, `X-Postmark-Server-Token`); sending only. Config (token, from
  address, stream, on/off) from `application.ini`.
- `VerificationCodes` — issue/verify the 6-digit codes (`SecureRandom`, expiring, single-use,
  attempt-limited). Failed-attempt increments commit on a **separate connection** so the limit
  survives the request rollback that `UserException` triggers.
- `EmailBodies` — HTML bodies for the verification and reset emails.

### Auto-update at startup (`src/main/precompiled/com/svnhub/migrate/`)
`KissInit.init2` runs a two-stage migration so a deploy is just a WAR swap +
restart — the server brings the DB current with the code. Stage 1 `SchemaMigrator`
applies ordered, additive, idempotent schema `Migration`s tracked by a `db_version`
table; Stage 2 `RecordMigrator` applies per-row `RecordUpgrader`s tracked by
`repository.record_version`. A schema-migration failure (or a DB ahead of the
code) marks `SchemaStatus` not-ready, and `Login.login` then refuses logins
(fail-closed). Full detail: `AutoUpdate.md`.

### Cron tasks (`src/main/backend/CronTasks/`, `crontab`)
- `IngestSvnLogs` — **every minute**: incremental, idempotent tail of the svnserve log →
  `access_event` + rollups + `working_copy_state`; auto-provisions discovered repos/aliases.
- `RefreshRepoHead` — **every 5 min**: refresh cached HEAD + warm `commit_cache`.

---

## 6. Authentication & authorization

**Login:** email is the credential; password verified against the PBKDF2 hash (`org.kissweb.PasswordHash`).
The session UUID is returned and sent on every subsequent JSON-RPC call. Login also returns `isAdmin`,
`handle`, `email`, and `emailVerified`.

**Email verification (gate):** self-registered accounts start `email_verified='N'`. Registration emails
a 6-digit code (`SecureRandom`) and logs the user straight in, but the frontend **gates** the app on the
verify screen (enter code / resend / log out) until the email is confirmed — assuring the address is real
and deliverable. Pre-existing accounts were grandfathered to verified by the v4 migration.

**Passwords:** a logged-in user changes their own password from the top-bar account area
(`AccountService.changePassword`); a forgotten password is reset, unauthenticated, via an emailed code
(`PasswordResetService`, no account enumeration). Either path updates both the PBKDF2 login hash and the
clear `svn_password`, then regenerates the svnserve `passwd`. Codes: 15-min TTL, 5 attempts, single-use.

**Two user classes:**
- **Regular** (default; all self-registered users) — full rights on repos they own or are granted,
  read on public repos; can create repos (becoming owner). Cannot manage other users.
- **Admin** (`is_admin='Y'`) — manage all user accounts, see/fully-access **every** repo (including
  others' private), scan disk. `RepoAccess.isAdmin` short-circuits all access checks to true.

**Visibility:** `private` (default) vs `public`. Public repos are readable by any authenticated
user; this is enforced **twice** — `RepoAccess.canRead` returns true for public (web side), and the
repo's svnserve `authz` gets a `* = r` catch-all (svn side). Write always requires an explicit grant.

**svnserve auth is generated, not hand-edited:** SvnHub is the system of record. On any access/
password/visibility change, `SvnAuthManager` rewrites a shared `passwd` (from `users.svn_password`)
and each repo's `conf/authz` + `conf/svnserve.conf` (from `repository_access`). svnserve auth
usernames are the login email; the handle is only the URL namespace.

> **Note (anonymous):** "public" currently means *any logged-in user* can checkout — svnserve runs
> `anon-access = none`. True no-account anonymous checkout is not enabled.

---

## 7. Statistics pipeline (the differentiator)

```
svnserve --log-file  ──►  IngestSvnLogs (cron, 1/min)
  one line per SVN op       ├─ tail incrementally by inode+byte_offset (rotation/truncation safe)
                            ├─ parse (SvnLogParser); skip partial trailing line
                            ├─ dedupe via SHA-256 event_hash (UNIQUE) → idempotent
                            ├─ map repos-field "handle/name" → repository; raw user → users
                            └─ insert access_event + upsert access_daily_rollup + working_copy_state
                                       │
                                       ▼
                            StatsService ──► Insights screen
   (working-copy freshness / revisions-behind, checkout-vs-update, heatmaps, clones, per-range counts)
```

This is only possible because the model is **centralized**: a single authoritative server sees every
checkout/update and can attribute it to a user and revision — the core argument of *Why This Service
Exists*.

---

## 8. Frontend (`src/main/frontend/`, never the `kiss/` subdir)

- Entry pages: `index.html` (SPA shell), `login.html` (with "Forgot password?"), `register.html`,
  `why.html`, `verify.html` (email-verification gate, authenticated), `forgot.html` (password reset,
  unauthenticated).
- After login (and once verified), `screens/Framework/` is the nav shell — its top bar shows the
  account handle + a **Change password** popup. SvnHub screens:
  `Repositories` (My / Explore + create + access), `Discover` (people directory → a user's public
  repos), `Repository` (browse + README + commits + diffs), `Insights` (stats), `Issues`,
  `MergeRequests`, `Users` (admin-only, hidden for regulars).
- **DOM rule:** application screens never touch the DOM directly; rich HTML (markdown/code/diffs) is
  injected via the component API `text-label.setHTMLValue(...)`. Charts are currently rendered as
  HTML/CSS rather than Chart.js canvases for the same reason.
- Each screen is `<name>.html` + a `<name>.js` IIFE using `Server.call(class, method, data)`,
  `AGGrid`, and popups.

---

## 9. Configuration (`application.ini` `[main]`, gitignored)

Read via `MainServlet.getEnvironment(key)`. SvnHub keys: `SvnReposRoot`, `SvnConfDir`, `SvnLogFile`,
`SvnLogRotateGlob`, `SvnLogPathPrefix`, `SvnLogMaxLinesPerRun`, `SvnServiceUser`/`SvnServicePassword`
(merge identity), `SvnBaseUrl` (for checkout URLs); email/Postmark: `PostmarkApiToken`, `MailFrom`,
`MailFromName`, `MailMessageStream`, `MailEnabled`; plus the PostgreSQL connection. Secrets live only
here (the file is gitignored); **`application.template.ini`** (committed) documents every key with the
secrets blank. **Gotcha:** empty values must be quoted (`Key = ""`) — a bare `Key =` parses to null
and NPEs Kiss's `Hashtable`-backed environment at startup.

---

## 10. Build, run, test

- `./bld build` — compile core + precompiled. `./bld start-backend` / `stop-backend` — run Tomcat
  (port 8080) non-interactively. `./bld develop` — frontend (8000) + backend, interactive.
- Static frontend changes need only a browser reload; Groovy services hot-reload; precompiled/core
  changes need `build` + restart.
- `./bld unit-tests` then `java -jar work/KissUnitTest.jar --select-class=com.svnhub.SvnLogParserTest`.
- Dev SVN data lives under `dev-repos/` (gitignored); a dev `svnserve` runs on port 3691.

---

## 11. Operational prerequisites (production)

- Run svnserve with logging: `svnserve -d -r /home/repos --log-file /var/log/svnserve/svnserve.log`.
- The backend process needs write access to `SvnReposRoot` and `SvnConfDir` (run as the `svn` user
  or equivalent) to create repos and write auth files.
- SVN passwords are stored in svnserve's `passwd` in the clear (an SVN limitation), intentionally
  separate from the PBKDF2 login hash. Hashed SVN auth (SASL / Apache+htpasswd) is a future option.

---

## 12. Known gaps / future work

- **Cross-user fork** — planned, as a backgrounded `svnadmin dump | load` into the forker's namespace
  (acknowledged O(repo-size) cost); record fork ancestry in the DB.
- **User deletion vs owned repos** — `repository.owner_id` FK currently blocks deleting a user who
  owns repos; a proper delete (reassign or cascade `rm -rf` + auth regen) is not yet built.
- **Discovery / search** — keyword Explore (`searchRepositories`) and a Discover screen
  (people directory + per-user public profiles, via `DiscoverService`) exist. Possible further
  work: reachable profile links from repo lists, sort/activity filters, consolidating Explore
  and Discover.
- **Charts** — Chart.js is bundled but Insights uses HTML/CSS; canvas charts need a DOM-access exception.
- **Anonymous public access**, full-text code search, releases/tags downloads, wikis, notifications — not built.
```
