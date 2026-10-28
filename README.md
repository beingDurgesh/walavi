# Walavi

Walavi turns an Android phone into an OpenAI-compatible gateway for **multiple
CodeBuddy accounts**. Add accounts once, point any coding agent at the phone's
LAN address, and Walavi pools the accounts: round-robin selection, automatic
failover on `401`/`403`/`429`/`5xx`, and cooldowns so a bad account does not
stall the pool.

```
Cline / Continue / Aider / OpenCode / Codex CLI
                     │
                     ▼
        http://<phone-ip>:8787/v1
                     │
              Walavi (Android)
          ┌──────────┼──────────┐
          ▼          ▼          ▼
      account 1  account 2  account 3
          └──────────┴──────────┘
                     │
          https://www.codebuddy.ai
```

## Features

- **Browser sign-in** using CodeBuddy's CLI device flow (`/v2/plugin/auth/state`
  → browser approval → token polling), or paste an access token / full credential
  JSON.
- **Encrypted storage** for tokens (`EncryptedSharedPreferences`, AES-256-GCM
  master key in the Android Keystore).
- **Account pool** with round-robin rotation, per-account enable/disable,
  priority ordering, health checks and automatic token refresh.
- **Stable endpoint** — the LAN address and API key are chosen once and saved.
  Restarting the gateway or the app keeps the same `http://<ip>:<port>/v1` URL
  and key, so agents do not need to be reconfigured.
- **Failover** before the first byte: `401/403` → refresh and retry once, then
  next account; `429` → honour `Retry-After`; `5xx`/network → short cooldown,
  next account. Client errors (`400`, `404`, `422`) are returned unchanged.
- **OpenAI-compatible endpoints**: `/v1/chat/completions` (streaming and
  non-streaming), `/v1/models`, `/health`, `/v1/walavi/status`.
- **Non-streaming clients work**: CodeBuddy only accepts streaming chat requests,
  so Walavi always streams upstream and aggregates the SSE into a normal
  `chat.completion` response (content, tool calls, usage) when a client sends
  `stream: false`.
- **Channel verification**: CodeBuddy requires conversation/request id headers and
  a `system` message first; Walavi adds both, including when the agent sends only
  user messages.
- **Model selection**: local catalogue of CodeBuddy models plus live discovery
  from `/v2/models`; choose a default, force it, or expose only selected models.
- **Foreground service** with wake/Wi-Fi locks, optional start on boot.
- **Local API key** (`sk-walavi-…`) required by default; CORS enabled for
  browser-based clients.

## Install

Download `Walavi-release.apk` from the latest release and install it. The release
is signed with the project's release key; upgrades keep app data.

> Android may warn about installing an app from outside the Play Store. Walavi is
> a personal gateway, not a Play Store app.

## Use

1. Open Walavi → **Accounts** → **Add**.
2. Choose **Browser sign-in**, approve the CodeBuddy login in the browser, and
   wait for the sheet to confirm. Repeat for each account. Alternatively paste an
   access token from an existing CLI login.
3. Tap **Start gateway** on the Home screen. Grant notification permission so the
   foreground service survives.
4. Copy the endpoint (`http://192.168.x.x:8787/v1`) and the API key from
   **Settings**.

### Agent configuration

Use the endpoint as the OpenAI base URL and the Walavi key as the API key:

```bash
export OPENAI_BASE_URL="http://192.168.1.20:8787/v1"
export OPENAI_API_KEY="sk-walavi-..."
```

- **Cline / Roo Code**: Provider `OpenAI Compatible`, Base URL
  `http://<ip>:8787/v1`, API Key `sk-walavi-...`, model from `/v1/models`.
- **Continue**: `provider: openai`, `apiBase: http://<ip>:8787/v1`,
  `apiKey: sk-walavi-...`.
- **Aider**: `OPENAI_API_BASE=http://<ip>:8787/v1`, `OPENAI_API_KEY=sk-walavi-...`.
- **Codex CLI**: use `wire_api = "chat"`; Walavi does not implement the Responses
  API.
- **OpenCode**: custom OpenAI-compatible provider with the same base URL/key.

The phone and the agent must be on the same network. Some routers isolate Wi-Fi
clients (AP isolation); disable that if the endpoint is unreachable.

## API

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/health` | no | Liveness and account counts |
| GET | `/v1/models` | yes | Exposed model list |
| POST | `/v1/chat/completions` | yes | Chat completions (SSE if `stream: true`) |
| GET | `/v1/walavi/status` | yes | Per-account health and cooldown state |

Authentication: `Authorization: Bearer sk-walavi-...` or `x-api-key`. The key can
be regenerated or disabled in Settings.

## Costs and tokens

Each model row shows its CodeBuddy credit multiplier (`×0.00`, `×0.03`,
`×0.79`, …). Walavi estimates a request's cost as:

```
credits = (prompt_tokens + completion_tokens) / 1000 × multiplier
```

If the upstream response includes `usage.credit`, that exact value is used
instead of the estimate. Home and Activity show all-time combined tokens (in +
out) and all-time estimated credits; each request row shows its own tokens and
cost. The Activity list keeps the last 200 requests, but the totals are lifetime
counters persisted separately, so trimming the list never reduces them.

## Failover semantics

- The pool rotates the starting account per request, so load spreads evenly.
- Disabled accounts and accounts without a token are skipped.
- Accounts marked **sign-in needed** are tried only after healthy and cooling
  accounts, so a stale flag can heal after a successful request.
- If every account is cooling down, the least-cooling account is tried as a last
  resort.
- A `401` triggers one refresh-token attempt for that account. If it still
  fails, the account is marked **sign-in needed** for six hours and the next
  account is used. `403`/`429`/`5xx` cool the account down instead.
- Every enabled account is tried before returning `502`. If a pass fails only
  on transport/`5xx` errors, the same accounts get one more pass on a fresh
  connection. A failed token refresh counts as transient too, so an account is
  not dropped just because the refresh endpoint hiccuped.
- Upstream calls are pinned to HTTP/1.1: CodeBuddy resets long-lived HTTP/2
  streams with `INTERNAL_ERROR`, which clients saw as "stream was reset".
- A mid-stream failure ends the SSE body with an `error` frame and `[DONE]`, so
  the client never sees a truncated stream.
- Upstream calls use HTTP/1.1. CodeBuddy can reset long-lived HTTP/2 streams
  with `INTERNAL_ERROR`; HTTP/1.1 avoids that failure mode.
- Streaming responses always end with `data: [DONE]`. If the upstream stream
  breaks mid-flight, the client receives an SSE error frame instead of a
  truncated/reset connection.
- Streaming starts only after the upstream returns `200`; after that point a
  mid-stream failure cannot be retried without duplicating output.

## Security

- Tokens never leave the device except in requests to the configured upstream.
- The API key is generated with `SecureRandom`; comparisons are constant-time.
  It is kept in app-private settings separately from encrypted account tokens,
  so a keystore reset cannot silently change the key clients use.
- The HTTP listener binds `0.0.0.0` and is reachable by any device on the LAN
  that has the key. Keep the key enabled on shared networks.
- `usesCleartextTraffic` is enabled because the gateway itself speaks plain HTTP
  on the LAN and self-hosted upstreams may use HTTP. Traffic to
  `https://www.codebuddy.ai` is TLS.

## Build

CI only: GitHub Actions (`ubuntu-latest`, JDK 21, Android SDK 35) runs

```
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

and publishes signed APKs to releases. The project deliberately has no NDK and
is not built on the local aarch64 host.

Signing secrets (repository settings):

- `WALAVI_KEYSTORE_BASE64`
- `WALAVI_KEYSTORE_PASSWORD`
- `WALAVI_KEY_ALIAS`
- `WALAVI_KEY_PASSWORD`

If the secrets are absent, the release build falls back to the debug key.

## Limitations

- Chat Completions only; `/v1/responses` returns `501`.
- Request detail is limited to the last 200 requests on-device; all-time token
  and credit totals are kept for the whole life of the install.
- The upstream API is CodeBuddy's private CLI surface and can change; the app
  surfaces upstream errors verbatim.
- One phone, one port. Run one instance per network.

## Layout

```
app/src/main/java/com/flux/walavi/
  data/       encrypted store, models, JSON extraction
  gateway/    HTTP server, account pool, auth, upstream client
  service/    foreground service, boot receiver
  ui/         Compose screens and design system
```
