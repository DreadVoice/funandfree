# Event Platform

A platform for creating, discovering and booking events. The backend is built with Spring Boot and compiled into a GraalVM native image. It runs on a single Google Cloud always-free `e2-micro` instance, with SQLite handling the database locally.

| | |
|---|---|
| Frontend | https://funandfree.pages.dev |
| API | https://funandfree-api.dreadvoice.workers.dev |
| Origin | https://funandfree.duckdns.org |
| Running cost | **$0 / month** |

The project started as an event management application, but I also wanted to see how far I could take a production-style Spring Boot deployment with very limited resources and no monthly hosting costs. A lot of the work went into making the application fit those constraints without cutting corners on security or data integrity.

---

## 1. The application

Organizers can create and manage events, while attendees can browse events, search for them and reserve seats. When an event fills up, additional attendees are placed on a waitlist. If someone cancels, the next person in line gets promoted automatically.

**What's implemented**

- Firebase authentication for organizers.
- Event creation and editing, including title, description, time, venue and capacity.
- Public event listings, full-text search and event details.
- Booking and cancellation with strict capacity enforcement.
- FIFO waitlist with automatic promotion when a booking is cancelled.
- Automated event reminders through Resend, triggered by Cloud Scheduler using an OIDC-authenticated endpoint.

**What's intentionally left out:** Payments, recurring events, ticket tiers and check-in scanning. The application currently focuses on free events and RSVPs.

### Handling concurrent bookings

One of the more important problems was making sure two people couldn't book the same remaining seat.

Imagine an event with one seat left and two people trying to book it at exactly the same time. Only one of them should get the seat.

Capacity is tracked through `event.seats_taken`, with a database constraint enforcing `CHECK (seats_taken <= capacity)`. The actual seat reservation is handled by a single conditional SQL statement:

```sql
update event
   set seats_taken = seats_taken + 1
 where id = :id and status = 'PUBLISHED' and seats_taken < capacity
```

There's no separate read followed by a write. SQLite handles the update atomically. If the update affects zero rows, the event is either unavailable or full, and the attendee goes onto the waitlist.

I also wrote `BookingConcurrencyTest` to test this under contention. It releases **60 virtual threads simultaneously** against a 10-seat event, using a real on-disk SQLite database and a 3-connection pool.

The test checks that exactly 10 bookings are confirmed, 50 are waitlisted, `seats_taken` remains at 10, and no exceptions occur.

Cancellation needed similar care. Rather than releasing a seat and then assigning it to someone on the waitlist, the application transfers it directly. Within one transaction, it cancels the existing booking, promotes the first waitlisted attendee and creates their confirmed booking. `seats_taken` doesn't change during this process.

Only when the waitlist is empty is the counter decremented. This avoids a situation where someone outside the waitlist grabs a seat before the next person in line gets it.

There are **51 tests across 11 classes**, covering booking concurrency, authorization and reminder job idempotency.

---

## 2. The hosting constraints

These were the limits I had to work with from the beginning.

| Resource | Available |
|---|---|
| Compute | One `e2-micro`: 2 shared vCPUs, 1 GB RAM |
| Usable memory | ~500 MB after the OS |
| Disk | 30 GB standard persistent disk |
| Egress | 1 GB/month from North America |
| Region | `us-central1` (free-tier eligible) |
| Budget | $0/month |

The biggest problem was memory. A conventional Spring Boot application running on the JVM can easily use around 300 MB just sitting idle. That's a lot of the available memory gone before accounting for the operating system, database and reverse proxy.

This influenced almost every architectural decision, from how the application is compiled to how it accesses the database.

Cost was another constraint. I wanted the entire deployment to stay within Google's always-free allowance, so even otherwise reasonable choices had to be evaluated against their actual running costs.

---

## 3. Application architecture

```text
React 19 + Vite          Cloudflare Pages
         │
         │ Firebase ID token (Bearer)
         ▼
Spring Boot 3.5 / Java 21, GraalVM native image
         │
         ├── Spring Security OAuth2 resource server
         │       └── Validates Firebase JWTs
         │
         ├── JdbcClient
         │       └── No JPA or reflection-heavy ORM
         │
         └── SQLite (WAL) + Flyway
```

| Layer | Technology | Reason |
|---|---|---|
| Runtime | GraalVM native image | Lower memory usage and faster startup |
| Data access | Spring `JdbcClient` | Avoids Hibernate's reflection overhead and AOT complications |
| Database | SQLite, WAL, pool of 3 | No separate database server or managed database costs |
| Migrations | Flyway | Schema changes are version-controlled |
| Authentication | Firebase + OAuth2 resource server | JWT validation without the Firebase Admin SDK |
| Roles | Application database, keyed by Firebase UID | Avoids Firebase custom claims and the Admin SDK |

### Authentication

Firebase handles sign-in and issues ID tokens. The backend validates these as ordinary JWTs using Google's public JWK endpoint.

The expected issuer is `securetoken.google.com/funandfree`, and the audience is `funandfree`.

There's no Firebase Admin SDK and no service-account key stored in the application.

`FirebaseUserConverter` takes a verified token and creates an `AppUser`. Its authorities are loaded from the application's database rather than being taken directly from token claims.

When someone authenticates for the first time, their UID is added to the database with the `ATTENDEE` role. Promoting someone to `ORGANIZER` is handled directly in the database.

This is a deliberate trade-off: it keeps authentication relatively lightweight, but means role management isn't handled through Firebase custom claims.

### Separate security filter chains

The application has two security filter chains.

The regular API uses Firebase JWTs. Internal endpoints under `/internal/**` use a separate chain configured with `@Order(1)`.

This is necessary because Cloud Scheduler sends a Google-issued OIDC token, not a Firebase token. The two have different issuers and JWKS endpoints.

The internal chain validates the token's issuer, audience and service account email. The authorization layer checks the caller's identity again independently.

As a result, a valid Firebase token alone isn't enough to access internal endpoints.

### Time handling and search

Event times are stored in UTC alongside the event's IANA time zone. This allows the frontend to display the correct local time without losing the original time-zone information.

Search uses SQLite FTS5. An external-content virtual table is maintained through database triggers, so the search index stores indexed terms rather than another copy of the event text.

---

## 4. GraalVM native image

Compiling Spring Boot into a native image was one of the biggest decisions in this project. Without it, the memory requirements would have made the `e2-micro` deployment difficult to justify.

These are the measurements from the running instance:

| Metric | Result |
|---|---|
| Startup | **0.51 s** typical (0.24 s on faster hardware) |
| Memory, cgroup `memory.current` | **111 MB** against a 320 MB `MemoryMax` |
| Process RSS | 158 MB |
| Binary size | 111 MB, unstripped with debug info |
| Available host memory | 576 MB of 969 MB |
| Swap in use | 82 MB of 2048 MB |

For comparison, the same application took around **2.50 seconds** to start on the JVM during development. That's roughly five times longer, without even accounting for the additional heap memory a JVM would need.

The RSS and cgroup figures are different because RSS includes memory-mapped binary pages. These are file-backed pages that the kernel can reclaim, whereas the cgroup measurement is what counts against the service's memory limit.

### Building the native image

Native builds don't happen on the VM. During compilation, `native-image` needs considerably more memory than the entire instance has available. It peaked at **4.13 GB** during my builds.

GraalVM also doesn't support cross-compiling the native executable, so the build runs on a Linux x86_64 GitHub Actions runner.

The native-image step takes around 4 minutes and 30 seconds, with the complete CI pipeline taking roughly 12 minutes.

### Things I learned about AOT compilation

One issue that took some investigation was how Spring handles conditions during ahead-of-time compilation.

**Spring can evaluate configuration conditions at build time rather than when the application starts.**

I initially had a verification runner annotated with `@Profile("spike")`. It worked on the JVM, but the bean wasn't included in the compiled image. Enabling `--spring.profiles.active=spike` at runtime didn't help because the bean had never been compiled into the binary.

I replaced the Spring profile condition with a regular Java check using `args.containsOption("spike")`. That logic survives AOT compilation because it's evaluated at runtime.

I ran into a similar issue with `@ConditionalOnProperty` on Spring Security's JWT decoder. The condition is evaluated during AOT processing, which meant `FIREBASE_PROJECT_ID` had to be available during compilation or the build would fail due to an unresolved placeholder.

The Resend integration had a similar requirement. Instead of using `@ConditionalOnProperty` to choose its implementation, it uses a regular runtime `if` statement to check whether an API key is present.

This lets the same binary run with or without Resend configured. Adding the API key later doesn't require another build.

### Testing the compiled binary

I didn't want CI to stop at checking whether the native image compiled successfully. The binary also needs to prove that its database and application logic actually work.

The executable includes a small smoke test that runs with:

```bash
./event-platform --spike
```

It:

1. Applies the database migrations.
2. Creates an organizer and an event.
3. Runs an FTS5 search.
4. Attempts three seat reservations against an event with a capacity of two.
5. Checks that the results are `true/true/false`.
6. Rolls back the entire operation in a transaction.

Any failure produces a non-zero exit code.

CI runs this test before publishing the binary. This catches problems that a successful compilation alone wouldn't detect, such as SQLite initialization failures, broken migrations or incorrect capacity enforcement.

Since the test rolls back its changes, it can also be run against an existing database.

---

## 5. Why Google Cloud?

I considered several hosting options, but Google Cloud made the most sense for this particular setup.

Its always-free `e2-micro` comes with other services that are useful for running and deploying an application:

- Cloud Storage for release binaries.
- Cloud Scheduler for automated reminders.
- IAM and Workload Identity Federation for CI authentication.
- IAP for SSH access without exposing port 22.
- Cloud Monitoring for external uptime checks.

The deployment uses three separate service accounts, each with its own permissions.

| Identity | Permissions |
|---|---|
| `github-ci` | Create and read objects in the release bucket, but cannot delete them |
| `vm-runtime` | Read release artifacts and write logs |
| `scheduler-invoker` | Invoke the reminder endpoint using OIDC |

The GitHub CI account deliberately doesn't have `storage.objects.delete`. This means it can't overwrite or remove an already published release.

It also meant I couldn't use a simple mutable `releases/latest` pointer, since updating it would require delete permissions. Instead, the deployment script lists the available releases and sorts them by upload time to find the newest one.

### GitHub authentication without service-account keys

GitHub Actions authenticates to Google Cloud through Workload Identity Federation.

Rather than storing a service-account JSON key in GitHub Secrets, the runner exchanges its GitHub OIDC token for temporary GCP credentials when the workflow runs.

The provider also checks:

```text
assertion.repository == 'DreadVoice/funandfree'
```

This restriction matters. Without it, another GitHub repository could potentially attempt to impersonate the same identity.

---

## 6. Working within the e2-micro limits

Most of the deployment choices came down to making sure the application didn't exceed what the VM could handle.

| Constraint | Solution |
|---|---|
| ~500 MB usable RAM | Native image using ~111 MB, `MemoryMax=320M` in systemd and 2 GB swap as a safety net |
| 2 shared vCPUs | Concurrency tests run in CI, not on the VM |
| No free managed database | SQLite on local disk, WAL mode and a pool of 3 |
| 1 GB/month egress | No images served from the VM; organizers provide image URLs |
| Native builds need 4–8 GB RAM | Build in GitHub Actions and download the finished binary |
| Limited room for monitoring agents | Use external uptime checks instead of an on-box Ops Agent |

### Two billing surprises

My first Google Cloud bill wasn't zero. It was ₹5.

There were two things I had overlooked.

**1. The wrong disk type**

The VM was initially provisioned with a 10 GB `pd-balanced` disk.

Google's free allowance covers 30 GB of `pd-standard` storage, not `pd-balanced`. The latter is SSD-backed and billed separately.

I was paying for 10 GB of storage while the free allowance would have covered 30 GB. I rebuilt the instance using a 30 GB `pd-standard` disk.

**2. External IPv4 addresses aren't free**

Google charges **$0.005/hour** for an external IPv4 address in use on a standard VM. That works out to approximately $3.65 per month.

The free allowance is only one hour per month. This isn't particularly obvious when looking at the VM free-tier documentation, so I only caught it after checking the pricing page and invoice.

External IPv6 addresses assigned to VM instances, on the other hand, are free.

The solution was to remove the VM's IPv4 address entirely.

That saved the recurring cost, but introduced a networking problem.

---

## 7. Networking: IPv6, DuckDNS and Cloudflare

The VM now runs without a public IPv4 address.

That's fine for IPv6-capable clients, but some corporate and university networks still only support IPv4. Removing IPv4 also removes outbound IPv4 connectivity. Restoring it through Cloud NAT would cost around $32/month, which defeats the purpose.

Fortunately, outbound connectivity wasn't an issue. I checked the dependencies from the VM and confirmed that Google APIs, Cloud Storage, Debian repositories and Resend all publish AAAA records.

Incoming requests needed a different solution.

```text
Visitor (IPv4 or IPv6)
   ↓
funandfree-api.dreadvoice.workers.dev
   Cloudflare Worker, free tier
   ↓ fetch()
funandfree.duckdns.org
   AAAA only → 2600:1900:4000:af72::
   ↓ HTTPS, Let's Encrypt
Caddy → systemd → native binary → SQLite
```

The Cloudflare Worker acts as an IPv4-to-IPv6 edge proxy. Visitors connect to Cloudflare, which forwards requests to the VM over IPv6.

### Why DuckDNS?

I initially tried `sslip.io` and `nip.io`. Both services encode IP addresses into hostnames without requiring registration.

The problem was that their IPv6-encoded hostnames also returned an A record pointing to `0.0.0.0`.

Cloudflare resolved IPv4 first, received an unroutable address and failed with error 1016 without trying the AAAA record.

DuckDNS allows an explicit AAAA-only record, which solved the problem.

There was another small issue with Let's Encrypt. It rejected the compressed IPv6 hostname `2600-1900-4000-af72--.sslip.io` because the label ended in a hyphen. DNS resolution itself worked, but the certificate authority wouldn't sign it. Using the uncompressed address avoided that problem.

### Why a Cloudflare Worker?

Using Cloudflare's regular proxy would have required a domain name. A domain is relatively inexpensive, but it would still introduce a recurring cost.

The `workers.dev` subdomain is available without purchasing one, so I used a Worker instead.

One thing I wasn't sure about was whether Workers could connect to an IPv6-only origin. The documentation wasn't particularly clear, so I tested it directly. It works.

A `.com` domain through Cloudflare Registrar would cost around $10/year and make the API URL considerably nicer. That's still cheaper than the approximately $44/year an external IPv4 address would cost.

For now, I'm keeping the $0 setup.

### Security

A few things are in place to limit access to the VM and its internal endpoints:

- **SSH isn't publicly accessible.** Port 22 only accepts traffic from Google's IAP range, `35.235.240.0/20`. The default `allow-ssh` and `allow-rdp` firewall rules have been deleted. Access is through `gcloud compute ssh --tunnel-through-iap` with OS Login.
- Ports 80 and 443 are open to `::/0`. Port 80 is needed for Let's Encrypt HTTP-01 certificate renewal.
- `/actuator/*` is blocked by both Caddy and the Cloudflare Worker. This keeps internal health information private even if one of the configurations changes unexpectedly.
- CORS only allows the Cloudflare Pages frontend origin. I've verified that requests from other origins don't receive an `Access-Control-Allow-Origin` header.
- The reminder endpoint requires a Google OIDC token from the specific Cloud Scheduler service account.

---

## 8. Deployment

The application is deployed through GitHub Actions whenever code is pushed to `main`.

```text
Push to main
   ↓
Backend tests (51) ─┬─ Frontend build
                    ↓
              Native image build
                    ↓
          ./event-platform --spike
                    ↓
       WIF authentication (no keys)
                    ↓
       gs://standard-tools-releases/releases/<sha>/
                    ├── event-platform
                    └── event-platform.sha256
```

Deployment is pull-based. GitHub Actions publishes the binary to Cloud Storage, and the VM downloads it when a deployment is triggered. CI never connects directly to the VM.

The deployment script is `ops/deploy.sh`.

It handles the following:

1. Finds the newest release, or uses a specific commit SHA if supplied.
2. Downloads the binary and checksum, then verifies the SHA-256 hash before installing anything.
3. Creates a SQLite backup using `.backup` and compresses it with gzip.
4. Switches the active binary through an atomic symlink update and restarts the service.
5. Checks `/actuator/health` until the application becomes healthy or the timeout expires.
6. Rolls back if deployment fails, then checks that the restored version is healthy. If that also fails, it exits with code `2`.
7. Keeps the last three releases and removes older ones.

I also tested the rollback mechanism by deliberately deploying a broken binary rather than relying on the script's logic alone.

The application runs under systemd as a non-root user. Its service configuration includes `MemoryMax=320M`, `ProtectSystem=strict`, `NoNewPrivileges` and write access restricted to its data directory.

---

## 9. Running costs

The current setup fits within the free-tier allowances of the services it uses.

| Resource | Monthly cost |
|---|---|
| `e2-micro`, `us-central1` | $0 |
| 30 GB `pd-standard` | $0 |
| External IPv6 | $0 |
| Cloud Storage releases | $0 |
| Cloudflare Worker + Pages | $0 |
| DuckDNS + Let's Encrypt | $0 |
| **Total** | **$0** |

The only charge so far was ₹5 from the initial disk and IPv4 configuration mistakes. Both have since been fixed.

---

## 10. Running locally

Start the backend:

```bash
cd backend
FIREBASE_PROJECT_ID=funandfree ./mvnw spring-boot:run
```

Start the frontend:

```bash
cd frontend
cp .env.example .env     # Fill in the VITE_FIREBASE_* values
npm install && npm run dev
```

Run the tests, including the booking concurrency test:

```bash
cd backend && ./mvnw verify
```

Build the native image (requires Docker rather than a local GraalVM installation):

```bash
sh backend/native-build.sh
```

### Operational scripts

| Script | Purpose |
|---|---|
| `ops/bootstrap-vm.sh` | Configures swap, service user, Caddy and systemd |
| `ops/deploy.sh` | Downloads, verifies and deploys releases with rollback |
| `ops/setup-workload-identity.sh` | Sets up WIF, the CI identity and release bucket |
| `ops/create-scheduler-job.sh` | Creates the Cloud Scheduler job with OIDC |
| `ops/cloudflare-worker.js` | Handles the IPv4-to-IPv6 proxy |

---

## 11. Known gaps

There are still a few things I'd like to improve. Some are operational shortcomings, while others are things I haven't had the time or resources to benchmark properly.

- **Infrastructure isn't managed through Terraform yet.** Everything was set up through scripts in `ops/`. They're idempotent and can be run again, but I can't currently recreate the entire environment from infrastructure code alone.
- **No off-instance database backups.** The deployment script creates a local backup before each release, but losing the VM would also mean losing the database. Litestream with Cloud Storage is the planned solution.
- **No uptime check or budget alert.** Both are available for free. A budget alert would also have caught the initial billing mistakes much earlier.
- **No proper JVM-versus-native benchmark.** I compared startup times (2.50 s versus 0.51 s), but the measurements were taken on different hardware. Throughput hasn't been tested under identical conditions.
- **The native binary isn't stripped.** It's currently 111 MB and still includes debug information. Stripping it should reduce the size considerably.
- **Secrets are stored in a `chmod 600` environment file.** Moving them to Secret Manager would be a better approach.
- **Reminder emails haven't been tested against the live Resend API.** The HTTP client is tested against a mock server, but no actual reminder email has been sent.
- **The API URL uses `workers.dev`.** A custom domain would make it cleaner, but would introduce an annual cost.

