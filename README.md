# HackerCode

**Understand your code. Master your patterns. Get placement-ready.**

HackerCode is a full-stack placement-preparation platform: an AI-assisted code review workspace, a structured library of
all **442 problems** from the CodeSheet placement sheet, a six-language code editor with sandboxed execution, progress
tracking, an advanced settings centre and a role-protected operator console.

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.5 (Web, Security, OAuth2 Client, Data JPA, Validation, Mail, Actuator) |
| Database | Flyway migrations; H2 file DB for development, PostgreSQL for production |
| Frontend | React 19 + Vite, React Router, Monaco Editor (bundled locally), Three.js (lazy-loaded) |
| AI | Official Anthropic Java SDK (`claude-opus-5-5` by default), structured JSON output |
| Execution | Pluggable sandbox: Docker, Judge0 CE or Piston; never inside the application JVM |
| Tests | JUnit 5 + MockMvc + spring-security-test (70 tests), Vitest (4 tests) |

The React client is compiled into the Spring Boot jar, so the whole product runs as **one Java service**.

---

## Quick start (development)

Prerequisites: **JDK 21+** (tested on 25), **Maven 3.9+**, **Node.js 20+**.

```bash
mvn spring-boot:run
```

Open <http://localhost:8080>. The first start creates `./data/hackercode.mv.db`, runs the Flyway migration and imports
all 442 problems. Maven builds the React client automatically (`npm ci` + `npm run build`).

For frontend work with hot reload, run the backend, then in another terminal:

```bash
cd client && npm install && npm run dev
```

and open <http://localhost:5173> (API calls are proxied to :8080).

### Out of the box vs. what needs credentials

Everything below works with **no configuration**: sign up/in, sessions and remember-me, CSRF protection, onboarding, the
problem library, search/filters, bookmarks, notes, saved drafts, dashboard, settings, data export/deletion, the operator
console, audit log and rule-based static code checks.

Without SMTP, email links (verification, password reset) are printed to the **server console** — on `localhost` only.

These features need external configuration, and the UI says so plainly until they are configured:

| Feature | Needs |
|---|---|
| Running / submitting code | `EXECUTION_PROVIDER` = `docker`, `judge0` or `piston` (see below) |
| AI-generated reviews | `AI_PROVIDER=anthropic` + `ANTHROPIC_API_KEY` |
| Continue with Google | `GOOGLE_CLIENT_ID` + `GOOGLE_CLIENT_SECRET` |
| Real email delivery | `SMTP_HOST`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `MAIL_FROM` |

All variables are listed in [`.env.example`](.env.example). Secrets are read only on the server and are never sent to the browser.

### First administrator

There are no built-in admin credentials. Sign up normally, then restart once with:

```bash
HC_BOOTSTRAP_ADMIN_EMAIL=you@example.com mvn spring-boot:run
```

The account is promoted to `ADMIN` (recorded in the audit log). Remove the variable afterwards. Admins can then promote
others to `MODERATOR` or `ADMIN` from **Operator console → Users**.

---

## Configuration details

### Code execution

Submitted code is never executed in the application process. Pick one sandbox:

**Docker (local machine or a dedicated execution host).** Install Docker, pull the images and set `EXECUTION_PROVIDER=docker`:

```bash
docker pull gcc:14 && docker pull eclipse-temurin:21-jdk && docker pull python:3.12-alpine && docker pull node:22-alpine && docker pull golang:1.23-alpine
```

Each run uses a throwaway container with `--network none`, a read-only root filesystem, a 128 MB tmpfs, memory/CPU/PID
limits, all capabilities dropped, `no-new-privileges`, an unprivileged user, and a hard `timeout -s KILL` plus a
host-side wall-clock kill. Output is capped at 64 KB. Do **not** mount the host Docker socket into a public web server
container; in a hosted deployment use Judge0 or Piston on a separate host instead.

**Judge0 CE.** Self-host Judge0 (`EXECUTION_PROVIDER=judge0`, `JUDGE0_URL`, `JUDGE0_AUTH_TOKEN`) or use the RapidAPI
edition (`JUDGE0_URL=https://judge0-ce.p.rapidapi.com`, `JUDGE0_RAPIDAPI_HOST=judge0-ce.p.rapidapi.com`,
`JUDGE0_AUTH_TOKEN=<RapidAPI key>`). Language ids used: C 50, C++ 54, Java 62, Python 71, JavaScript 63, Go 60.

**Piston.** Self-host [Piston](https://github.com/engineer-man/piston), install the `c`, `c++`, `java`, `python`,
`javascript` and `go` runtimes, and set `EXECUTION_PROVIDER=piston`, `PISTON_URL`.

Limits: `EXECUTION_TIMEOUT_SECONDS` (default 5, max 15), `EXECUTION_MEMORY_MB` (default 256). Runs are rate limited per
user (operator-configurable) and require a verified email.

**Submit vs. Run.** The source PDF lists problems by title only — it contains no statements or test cases, and none are
invented. *Run* executes your code with your own input. *Submit* saves the solution; if an operator has added test cases
(Operator console → Problems → Edit → Test cases), it is judged against them and the problem is marked solved on success.
Otherwise the response says it could not be judged and you mark the problem solved yourself.

### AI code review

Set `AI_PROVIDER=anthropic` and `ANTHROPIC_API_KEY`. The reviewer uses the official Anthropic Java SDK with:

- `claude-opus-5-5` by default (`ANTHROPIC_MODEL` to change),
- structured output constrained to a JSON schema (summary, score, Big-O complexity, findings with severity/category/line,
  positives, next steps, optional refactor),
- effort matched to the user's *review detail level* (brief → low, standard → medium, thorough → high),
- server-side refusal fallbacks (`fallbacks: "default"`, beta `server-side-fallback-2026-07-01`): if a safety
  classifier declines a request (security-heavy code can trigger this), the platform retries on a suitable model instead
  of failing,
- typed error handling for auth, rate-limit, timeout, overload and invalid-response cases, each shown as a clear message.

Every review also includes deterministic **static checks** (rule-based, labelled "not AI"). Reviews never claim code was
executed or tested.

### Google sign-in

Create an OAuth client (type *Web application*) in Google Cloud Console with the redirect URI
`{APP_BASE_URL}/login/oauth2/code/google`, then set `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`. Only Google accounts
with a verified email are accepted; an existing account with the same email is linked (audited).

### Database

Development uses an H2 file in `./data`. For production use PostgreSQL:

```bash
DATABASE_URL=jdbc:postgresql://localhost:5432/hackercode DATABASE_USER=hackercode DATABASE_PASSWORD=... java -jar target/hackercode-1.0.0.jar
```

The schema is created by Flyway (`src/main/resources/db/migration`); Hibernate only validates it.

---

## The 442-problem library

The authoritative source is `CodeSheet File.pdf`. The import is reproducible:

1. `scripts/extract_problems.py` reads the PDF with PyMuPDF. It uses the PDF's typography (16.5 pt = topic,
   13.5 pt = pattern, numbered 12 pt lines = problems, wrapped lines joined) and maps each hyperlink annotation to its
   entry by position. It **fails** unless it finds exactly 442 entries numbered 1–442 in order, and unless every link
   annotation is matched.
2. The result is committed as `src/main/resources/data/problems.json`, with stable ids `cf-001` … `cf-442`, original
   numbering, order, topic, pattern, the verbatim source text (including the sheet's notes), and practice URLs.
3. On startup `ProblemImporter` validates the dataset and inserts any missing records. It never overwrites a record, so
   operator corrections survive restarts.

```bash
pip install pymupdf
python scripts/extract_problems.py "path/to/CodeSheet File.pdf" src/main/resources/data/problems.json
```

Verified facts about the extract: 442 entries, 18 topics, 61 patterns, 424 entries with a practice link (the other 18
have none in the PDF, e.g. *“What is an array? How is it represented?”*). Three titles appear twice in the sheet under
different patterns (*Word break*, *Word boggle*, *Minimum cost of ropes*); they are kept as separate entries and
reported as possible duplicates by the validator.

Data provenance is explicit in the UI: source fields are shown in an orange *“From the source sheet”* block, and anything
an operator adds later (statement, constraints, examples, difficulty, tests) appears in a separate *“Added after
import”* block. Nothing is fabricated for title-only entries.

---

## Security model

- **Passwords** are hashed with BCrypt (cost 12). Server-side policy: 8–128 characters, letters plus a number or symbol,
  common passwords and passwords containing the email name rejected.
- **Sessions**: server-side HTTP sessions (`HttpOnly`, `SameSite=Lax`, `Secure` with `COOKIE_SECURE=true`); the session
  id is rotated on login (session-fixation protection). Optional remember-me uses Spring Security's persistent-token
  scheme (series + rotating token stored server side).
- **Device sessions** are tracked; users can sign out other devices, and a password change or reset signs out all others.
- **CSRF**: cookie-to-header tokens on every state-changing request.
- **RBAC**: `USER`, `MODERATOR` (problem curation, read-only user views), `ADMIN` (accounts, roles, configuration, audit).
  Enforced by URL rules plus `@PreAuthorize` on each admin endpoint. Roles are re-read from the database on every
  request, so promotions, demotions and suspensions apply immediately. The last administrator cannot be demoted.
- **Rate limits**: sign-in/sign-up/reset per IP; code runs and AI reviews per user (operator-configurable).
- **Open-redirect protection** for post-login destinations (server and client).
- **Headers**: strict Content-Security-Policy (`script-src 'self'`; Monaco and fonts are bundled, no CDNs),
  `frame-ancestors 'none'`, `X-Frame-Options: DENY`, `nosniff`, strict referrer policy, restrictive permissions policy.
- **Email tokens** are random 256-bit values stored only as SHA-256 hashes, single use, short-lived (verification 24 h,
  reset 30 min). The forgot-password endpoint returns the same response for unknown emails.
- **Audit log** records security and administrative events (sign-ins, failures, resets, role/status changes,
  configuration changes, problem edits, exports, deletions) with a masked IP. It never stores passwords, tokens,
  secrets or source code.
- **Maintenance mode** makes the API read-only for non-staff users.
- **Retention** (daily job): code runs 30 days, reviews 180 days, activity and audit log 365 days, stale sessions 60 days.

---

## Testing

```bash
mvn test                # 70 backend tests
cd client && npm test   # client unit tests
mvn package             # client build + tests + executable jar in target/
```

Backend coverage includes: dataset integrity (exactly 442 entries, numbering, order, stable unique ids, all 18 topics
and subsections, known entries, URL provenance); import idempotency and DB validation; search by title, `#number`,
topic and keyword; pagination caps; bookmark/status/notes/draft persistence and per-user isolation; sign-up validation,
hashing, duplicates, CSRF enforcement, generic login errors, logout, email verification, password reset (single-use,
signs out other sessions), suspension, device sign-out, password change, auth rate limiting, open-redirect protection,
account deletion; RBAC for user/moderator/admin, live role changes, audit records, last-admin guard, config validation,
maintenance mode, secret non-disclosure, source-provenance protection; execution for C and Go plus other languages via
a fake sandbox (compile errors, timeouts, unverified users, rate limits, test-case judging, honest no-test submissions);
Judge0/Piston response parsing; Docker sandbox flags; AI review labelling and failure reporting; static analysis rules;
settings persistence, validation, reset and privacy opt-out.

The execution tests use a deterministic fake sandbox and the review tests a fake AI provider (only the external call is
faked; security, validation, persistence and progress logic are real). Real C/Go compilation was **not** exercised in
this environment because neither Docker nor a Judge0/Piston endpoint was available.

---

## Deployment

```bash
cp .env.example .env    # fill in values
docker compose up --build
```

`docker-compose.yml` runs the app with PostgreSQL. Put HTTPS in front (reverse proxy) and set `COOKIE_SECURE=true`,
`APP_BASE_URL=https://…`, `HC_REMEMBER_ME_KEY`, SMTP, and an execution provider. If the proxy terminates TLS, also set
`SERVER_FORWARD_HEADERS_STRATEGY=framework` so client addresses are seen correctly for rate limiting.

The in-memory rate limiter and HTTP sessions assume a single instance; for horizontal scaling add Spring Session (JDBC or
Redis) and a shared rate-limit store.

---

## Project layout

```
pom.xml                         Maven build (also builds the client)
scripts/extract_problems.py     Reproducible PDF → JSON extraction
src/main/java/dev/hackercode/
  auth/        sign-up/in, tokens, email, Google OAuth, session guard, remember-me
  user/        users, roles, preferences, account & settings API
  problem/     catalog, importer/validator, search, topics
  progress/    progress, bookmarks, notes, code drafts
  execution/   languages, sandbox providers (Docker, Judge0, Piston), run/submit
  review/      Anthropic reviewer, static analyzer, review history
  activity/    activity events, dashboard, notifications, reminders
  admin/       operator console API, audit log, platform settings, retention
  config/      security, CSP, CSRF, rate limiting, SPA routing
src/main/resources/
  db/migration/V1__init.sql     schema
  data/problems.json            442-problem dataset
client/src/                     React app (pages, components, styles, 3D scenes)
```

## Origin

HackerCode upgrades the earlier *CodeSage — Smart Code Review Assistant* prototype (single-page HTML with Java/Python/C++/C
support, a problem list, review panel and assistant chat, whose backend was not part of the repository and whose
fallbacks returned simulated logins, test results and reviews). Those features were rebuilt on a real backend, C++/C/Java/Python
support was kept, JavaScript and Go were added, and all simulated results were removed.
