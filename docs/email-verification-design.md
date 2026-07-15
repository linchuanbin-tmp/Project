# Email Verification System Design

## Overview

BankAgent uses **Resend** (resend.com) as the email delivery provider, replacing the previous QQ SMTP
implementation. The system sends HTML-formatted verification code emails for user registration and
login, secured by a three-tier rate-limiting strategy backed by Redis.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                           CLIENT                                     │
│                 Login Page  ·  Register Page                         │
│                                                                      │
│   Frontend guard: button disabled + 60s countdown after each send    │
└──────────────────────────────┬──────────────────────────────────────┘
                               │  POST /api/user/send-code
                               │  { email: "user@example.com" }
                               │  (whitelisted — no JWT required)
                               ▼
┌──────────────────────────────────────────────────────────────────────┐
│                        GATEWAY (port 8080)                            │
│                                                                       │
│   Spring Cloud Gateway                                                │
│   ├─ IP-based RequestRateLimiter (global: 10 req/s replenish,        │
│   │                                 20 burst per IP)                  │
│   └─ Forwards X-Forwarded-For / X-Real-IP headers to downstream       │
└──────────────────────────────┬───────────────────────────────────────┘
                               │  ┌─ X-Forwarded-For: <client-ip>
                               │  └─ Forward to user-service:8081
                               ▼
┌──────────────────────────────────────────────────────────────────────┐
│                      USER-SERVICE (port 8081)                         │
│                                                                       │
│   UserController.sendVerificationCode()                               │
│   │                                                                    │
│   ├─ 1. Extract real client IP from X-Forwarded-For / X-Real-IP      │
│   │                                                                    │
│   ▼                                                                    │
│   UserServiceImpl.sendVerificationCode(email, clientIp)               │
│   │                                                                    │
│   ├─ 2. Three-tier rate limiting (Redis)  ◄── see diagram below      │
│   ├─ 3. Generate 6-digit code                                         │
│   ├─ 4. Store code in Redis  (TTL: 5 min)                             │
│   └─ 5. Call EmailService.sendVerificationCode()                     │
│                                                                       │
│   ▼                                                                    │
│   EmailService                                                         │
│   └─ HTTP POST → https://api.resend.com/emails                       │
│      Authorization: Bearer <RESEND_API_KEY>                           │
│      Content-Type: application/json                                   │
└──────────────────────────────┬───────────────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────────────┐
│                        RESEND API                                     │
│                                                                       │
│   Sends HTML email with styled verification code card                 │
│   From: BankAgent <noreply@bankagent.online>                          │
└──────────────────────────────────────────────────────────────────────┘
```

---

## Three-Tier Rate Limiting

```
Incoming send-code request
        │
        ▼
┌──────────────────────────────────┐
│  TIER 1: Per-email frequency     │
│  Redis key: email:code:limit:{email}         │
│  Rule: 1 request / 60 seconds    │
│  TTL: 60 seconds                 │
└──────────────┬───────────────────┘
               │ OK
               ▼
┌──────────────────────────────────┐
│  TIER 2: IP-level cap            │
│  Redis key: email:ip:limit:{ip}            │
│  Rule: max 5 requests / hour     │
│  TTL: 1 hour (set on first req)  │
└──────────────┬───────────────────┘
               │ OK
               ▼
┌──────────────────────────────────┐
│  TIER 3: Per-email daily cap     │
│  Redis key: email:daily:limit:{email}      │
│  Rule: max 10 requests / day     │
│  TTL: until midnight (local TZ)  │
└──────────────┬───────────────────┘
               │ OK
               ▼
         Send email
```

All tier counters use Redis `INCR` (atomic increment), with <strong>fail-fast</strong> semantics:
counters increment _before_ sending, so even if the Resend API call fails, the rate limit is
still enforced.

### Redis Key Reference

| Key Pattern | Value | TTL | Purpose |
|---|---|---|---|
| `email:code:{email}` | 6-digit code | 5 min | Verification code storage |
| `email:code:limit:{email}` | `"1"` | 60 s | Per-email send cooldown |
| `email:ip:limit:{ip}` | counter (int) | 1 hour | IP-level hourly cap |
| `email:daily:limit:{email}` | counter (int) | until midnight | Per-email daily cap |
| `session:active:{username}` | JWT token | configurable | Session management |

---

## Flow: Registration with Verification Code

```
User                   Frontend               Backend                  Redis          Resend
 │                        │                      │                       │               │
 │  1. Enter email        │                      │                       │               │
 │───────────────────────►│                      │                       │               │
 │                        │                      │                       │               │
 │  2. Click "Send Code"  │                      │                       │               │
 │───────────────────────►│                      │                       │               │
 │                        │  3. POST /user/send-code                     │               │
 │                        │─────────────────────►│                       │               │
 │                        │                      │  4. Rate limit check  │               │
 │                        │                      │──────────────────────►│               │
 │                        │                      │◄──────────────────────│               │
 │                        │                      │                       │               │
 │                        │                      │  5. Generate code     │               │
 │                        │                      │  + Store in Redis     │               │
 │                        │                      │──────────────────────►│               │
 │                        │                      │◄──────────────────────│               │
 │                        │                      │                       │               │
 │                        │                      │  6. POST /emails      │               │
 │                        │                      │──────────────────────────────────────►│
 │                        │                      │◄──────────────────────────────────────│
 │                        │                      │                       │               │
 │                        │  7. 200 "Code sent"  │                       │               │
 │                        │◄─────────────────────│                       │               │
 │                        │                      │                       │               │
 │  8. Start 60s          │                      │                       │               │
 │     countdown          │                      │                       │               │
 │◄───────────────────────│                      │                       │               │
 │                        │                      │                       │               │
 │  9. User receives email with verification code                        │               │
 │◄─────────────────────────────────────────────────────────────────────────────────────│
 │                        │                      │                       │               │
 │  10. Enter code +      │                      │                       │               │
 │      register          │                      │                       │               │
 │───────────────────────►│  11. POST /user/register                     │               │
 │                        │─────────────────────►│                       │               │
 │                        │                      │  12. GET code         │               │
 │                        │                      │──────────────────────►│               │
 │                        │                      │◄──────────────────────│               │
 │                        │                      │                       │               │
 │                        │                      │  13. Validate code    │               │
 │                        │                      │  + Delete from Redis  │               │
 │                        │                      │──────────────────────►│               │
 │                        │                      │  14. Insert user → DB │               │
 │                        │                      │                       │               │
 │                        │  15. 200 OK          │                       │               │
 │                        │◄─────────────────────│                       │               │
```

---

## Email Design

The email is sent as an HTML email using `<table>` layout (for maximum email client compatibility).

### Visual Structure

```
┌─────────────────────────────────────────────┐
│  (system default background)                │
│                                             │
│  ┌───────────────────────────────────────┐  │
│  │  white card, rounded 16px, subtle     │  │
│  │  border, max-width 440px, centered    │  │
│  │                                       │  │
│  │  BankAgent              ← slate-500   │  │
│  │                                       │  │
│  │  Your verification code  ← bold 26px  │  │
│  │                                       │  │
│  │  Use the code below...   ← slate-500  │  │
│  │                                       │  │
│  │  ┌─────────────────────────────────┐  │  │
│  │  │        4 2 0 8 5 1              │  │  │
│  │  │    monospace, 38px, letter-     │  │  │
│  │  │    spacing 14px, bold           │  │  │
│  │  └─────────────────────────────────┘  │  │
│  │                                       │  │
│  │  If you did not request...            │  │
│  │  ─────────────────────────────────    │  │
│  │  BankAgent Team © 2026                │  │
│  └───────────────────────────────────────┘  │
└─────────────────────────────────────────────┘
```

- **Font stack**: `SF Mono / Fira Code / Consolas / Courier New` (monospace) for the code;
  `-apple-system / BlinkMacSystemFont / Segoe UI` (system) for body text
- **Color palette**: slate grays (`#0f172a`, `#475569`, `#64748b`, `#94a3b8`) for clean hierarchy
- **Subject line**: `[BankAgent] Email Verification Code`

---

## Configuration

### application.yml (user-service)

```yaml
resend:
  api-key: ${RESEND_API_KEY:}
  from: ${RESEND_FROM:BankAgent <noreply@bankagent.online>}
```

### .env

```bash
# ── Resend Email ────────────────────────────────────
RESEND_API_KEY=re_xxxxxxxxxxxx
```

### docker-compose.yml (user-service block)

```yaml
environment:
  - RESEND_API_KEY=${RESEND_API_KEY}
```

---

## Key Files

| File | Role |
|---|---|
| `user-service/.../EmailService.java` | HTTP client calling Resend API, HTML email template |
| `user-service/.../UserServiceImpl.java` | Rate limiting logic, code generation, Redis storage |
| `user-service/.../UserController.java` | `POST /user/send-code` endpoint, client IP extraction |
| `user-service/.../UserService.java` | Interface: `sendVerificationCode(email, clientIp, scene)` |
| `user-service/.../dto/SendCodeRequest.java` | Request DTO with `@Email` + `@Pattern(regexp = "login|register")` validation |
| `user-service/.../dto/LoginRequest.java` | Login DTO with optional `code` field |
| `user-service/.../dto/RegisterRequest.java` | Registration DTO with `code` field |
| `user-service/.../application.yml` | Resend API key + sender address config |
| `gateway-service/.../JwtAuthGlobalFilter.java` | Whitelists `/api/user/send-code` (no JWT) |
| `gateway-service/.../GatewayServiceApplication.java` | `ipKeyResolver` bean for gateway-level rate limiting |
| `.env` | `RESEND_API_KEY` environment variable |
| `docker-compose.yml` | Injects `RESEND_API_KEY` into user-service container |
| `web-ui/src/utils/request.ts` | Axios interceptor with `silent: true` support for inline errors |
| `web-ui/src/views/login/index.vue` | Login: send-code with inline field error for unregistered emails |
| `web-ui/src/views/register/index.vue` | Register: send-code with inline field error for registered emails |
| `web-ui/src/locales/en.ts` | i18n: `emailNotRegistered`, `emailAlreadyRegistered` |
| `web-ui/src/locales/zh-CN.ts` | Chinese (simplified) i18n keys |
| `web-ui/src/locales/zh-TW.ts` | Chinese (traditional) i18n keys |

---

## Setup Guide

1. **Register on Resend**: Go to [resend.com](https://resend.com) and create an account.
2. **Add your domain**: In Resend → Domains, add your domain (e.g. `bankagent.online`).
   Resend provides DNS records (SPF, DKIM, MX) — add them to your domain registrar's DNS settings.
3. **Wait for DNS verification**: May take a few hours. Resend shows a green check when done.
4. **Create an API key**: In Resend → Settings → API Keys, create a new key with "Sending" access.
5. **Add the key to `.env`**:
   ```bash
   RESEND_API_KEY=re_xxxxxxxxxxxx
   ```
6. **Restart user-service**: The key is loaded from `.env` at startup.

### Testing

```bash
curl -X POST 'https://api.resend.com/emails' \
  -H 'Authorization: Bearer <RESEND_API_KEY>' \
  -H 'Content-Type: application/json' \
  -d '{
    "from": "BankAgent <noreply@bankagent.online>",
    "to": ["you@example.com"],
    "subject": "[BankAgent] Test",
    "html": "<h1>Hello from BankAgent</h1>"
  }'
```

---

## Security

### Defense-in-Depth Overview

```
┌─────────────────────────────────────────────────────────────────┐
│  LAYER 0 — Frontend                                              │
│  ├─ Email format validation before sending                       │
│  ├─ Button disabled + 60s countdown after each send              │
│  └─ Inline field error for unregistered/registered emails        │
│      (no toast notification — guidance shown next to the input)  │
└──────────────────────────────┬──────────────────────────────────┘
                               │
┌──────────────────────────────┴──────────────────────────────────┐
│  LAYER 1 — Gateway (Spring Cloud Gateway)                        │
│  ├─ Global IP-based RequestRateLimiter (10 req/s replenish)      │
│  └─ Forwards X-Forwarded-For / X-Real-IP to downstream           │
└──────────────────────────────┬──────────────────────────────────┘
                               │
┌──────────────────────────────┴──────────────────────────────────┐
│  LAYER 2 — Rate Limiting (before any DB query)                   │
│                                                                   │
│  Rate limits are enforced BEFORE the email existence check,       │
│  so attackers cannot bypass rate limits to enumerate users.       │
│                                                                   │
│  Tier 1: Per-email cooldown   → max 1 req / 60s                  │
│  Tier 2: Per-IP hourly cap    → max 5 req / hour                 │
│  Tier 3: Per-email daily cap  → max 10 req / day                 │
│                                                                   │
│  All counters use Redis INCR (atomic). TTLs set on first request. │
└──────────────────────────────┬──────────────────────────────────┘
                               │
┌──────────────────────────────┴──────────────────────────────────┐
│  LAYER 3 — Email Existence Check                                 │
│                                                                   │
│  Registration scene: reject if email is already registered        │
│  Login scene:        reject if email is NOT registered            │
│                                                                   │
│  Error messages are user-friendly (shown inline in the form),     │
│  but enumeration is blocked by Layer 2 rate limits (5 attempts    │
│  per IP per hour makes mass enumeration infeasible).              │
└──────────────────────────────┬──────────────────────────────────┘
                               │
┌──────────────────────────────┴──────────────────────────────────┐
│  LAYER 4 — Brute-Force Protection                                │
│                                                                   │
│  Redis key: email:code:fail:{email}  (counter)                    │
│  Max 5 wrong attempts → code is invalidated and deleted          │
│  Counter resets on: successful login/register, or new code sent   │
│  TTL matches the code TTL (5 minutes)                             │
└──────────────────────────────────────────────────────────────────┘
```

### Security Fixes Applied

#### 🔴 Email Enumeration — Mitigated

Before the fix, the existence check ran _before_ rate limits, allowing unlimited probing:

| Phase | Before Fix | After Fix |
|-------|-----------|-----------|
| Rate limit position | After DB query | **Before** DB query (Layer 2) |
| Enumeration risk | Unlimited probing via script | Max 5 attempts per IP per hour |
| Error message | Distinct messages per case | User-friendly inline errors, same rate-limit backstop |

**How enumeration is blocked**: Even though error messages differ between "already registered" and "not found," an attacker's IP is capped at 5 requests per hour. Mass enumeration of thousands of emails would take days to weeks per single target. Combined with the per-email daily limit (10/day), targeted enumeration of a single email is also rate-limited.

#### 🔴 Rate Limit Bypass (existence check before limits) — Fixed

| Before Fix | After Fix |
|-----------|-----------|
| DB query → then rate limit | Rate limit → then DB query |
| Attacker could cycle emails with no throttling | IP counter increments _before_ any DB work |

#### 🟡 Verification Code Brute-Force — Fixed

New Redis key: `email:code:fail:{email}`

| Rule | Value |
|------|-------|
| Max wrong attempts | **5** |
| On reaching limit | Code deleted from Redis (invalidated) |
| On successful login/register | Failure counter cleared |
| On new code sent | Failure counter cleared |
| TTL | Matches code TTL (5 minutes) |

This prevents an attacker from brute-forcing the 6-digit code even within the 5-minute window.

#### 🟡 Weak Random Number Generation — Fixed

| Before Fix | After Fix |
|-----------|-----------|
| `Math.random()` | `java.security.SecureRandom` |

`Math.random()` is not cryptographically secure and can be predicted. `SecureRandom` uses OS-level entropy, making code prediction infeasible.

#### 🟢 Frontend UX Improvements

| Before Fix | After Fix |
|-----------|-----------|
| Error toast notification for all errors | Inline field error below the email input |
| "Please wait 60 seconds" as toast | "No account found — please register first" next to input |
| No guidance on what to do | Register scene: "This email is already registered — please log in instead" |

The axios interceptor supports a `silent: true` config option to suppress global toast notifications, allowing individual API calls to handle errors with custom UI (inline validation).

### Redis Key Reference (Security)

| Key Pattern | Value | TTL | Purpose |
|---|---|---|---|
| `email:code:{email}` | 6-digit code | 5 min | Verification code storage |
| `email:code:limit:{email}` | `"1"` | 60 s | Per-email send cooldown |
| `email:ip:limit:{ip}` | counter (int) | 1 hour | IP-level hourly cap |
| `email:daily:limit:{email}` | counter (int) | until midnight | Per-email daily cap |
| `email:code:fail:{email}` | counter (int) | 5 min | Brute-force attempt counter |
| `session:active:{username}` | JWT token | configurable | Session management |

### Credential Protection

- Resend API key is stored in `.env` (gitignored), never committed
- `application.yml` only references it via `${RESEND_API_KEY:}` with empty default
- QQ SMTP credentials have been completely removed from the codebase
- Verification code is single-use (deleted from Redis after successful validation)
- 5-minute code TTL limits the window for brute-force attacks
