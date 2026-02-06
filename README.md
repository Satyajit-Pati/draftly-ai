# Draftly AI (Backend)

AI-powered Gmail assistant backend that:

- Connects to Gmail using OAuth2
- Fetches unread emails from the user’s inbox
- Generates reply drafts using an AI provider (Gemini / OpenAI)
- Lets the user review, edit, approve, reject drafts
- Sends approved replies via Gmail while keeping the thread intact
- Stores drafts + logs + send attempts in Postgres

---

## Tech Stack

- Java 17
- Spring Boot 3
- Spring Security OAuth2 Client (Google login)
- Gmail API (google-api-services-gmail)
- PostgreSQL + Spring Data JPA
- Flyway migrations

---

## Prerequisites

- Java 17
- PostgreSQL running locally
- Google Cloud project with **Gmail API enabled**
- OAuth2 Client credentials (Client ID + Client Secret)

---

## Configuration

Update `src/main/resources/application.yml`.

### 1) Database

```yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/draftly_ai
    username: <your_db_user>
    password: <your_db_password>
```

### 2) Google OAuth2 (Gmail)

```yml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: <GOOGLE_CLIENT_ID>
            client-secret: <GOOGLE_CLIENT_SECRET>
            scope:
              - openid
              - profile
              - email
              - https://www.googleapis.com/auth/gmail.modify
              - https://www.googleapis.com/auth/gmail.send
        provider:
          google:
            issuer-uri: https://accounts.google.com
```

Important:

- **Redirect URI** is configured as:
  - `"{baseUrl}/login/oauth2/code/{registrationId}"`
- If your app runs at `http://localhost:8080`, add this redirect URI in Google Cloud Console:
  - `http://localhost:8080/login/oauth2/code/google`

### 3) AI Provider

This project supports 2 AI providers:

- `gemini`
- `openai`

```yml
ai:
  provider: gemini  # or openai
```

#### Gemini

```yml
gemini:
  api-key: <GEMINI_API_KEY>
```

#### OpenAI

```yml
openai:
  api-key: <OPENAI_API_KEY>
```

### 4) Refresh token encryption key (optional but recommended)

Refresh tokens are stored in DB in `oauth_token.refresh_token_encrypted`.

If you set an encryption key, refresh tokens are stored encrypted using **AES-GCM**.

```yml
token:
  crypto:
    key: ""  # Base64 encoded 16/24/32 byte AES key
```

If `token.crypto.key` is empty, encryption/decryption is pass-through (useful for local dev).

---

## Run the app

```bash
./mvnw spring-boot:run
```

App runs at:

- `http://localhost:8080`

---

## Swagger / OpenAPI

This project exposes OpenAPI docs via Springdoc.

- Swagger UI:
  - `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON:
  - `http://localhost:8080/v3/api-docs`

Note: Swagger endpoints are publicly accessible, but most `/api/**` endpoints require OAuth login.

---

# Testing Procedure (End-to-End)

## Step 1 — Login with Google

Open in browser:

- `http://localhost:8080/oauth2/authorization/google`

After successful login, Spring redirects to:

- `GET /auth/success`

This endpoint stores OAuth tokens in the DB (`oauth_token` table).

---

## Step 2 — Fetch unread emails

Call:

- `GET /api/gmail/unread?maxResults=5`

Example:

```bash
curl -L "http://localhost:8080/api/gmail/unread?maxResults=5"
```

Notes:

- This endpoint requires you to be authenticated (OAuth login). Using `curl` directly can be inconvenient due to session cookies.
- The easiest way to test is via:
  - browser (if you expose an endpoint via GET)
  - or Postman (it will preserve cookies)

Response contains:

- `gmailMessageId`
- `threadId`
- `from`
- `subject`
- `snippet`
- `bodyText`

It also caches minimal metadata to the `gmail_email` table.

---

## Step 3 — Generate a draft

Call:

- `POST /api/drafts/generate`

Example payload:

```json
{
  "userId": "<UUID of app_user>",
  "gmailMessageId": "<gmailMessageId from unread API>",
  "threadId": "<threadId from unread API>",
  "emailContent": "<bodyText from unread API>",
  "tone": "friendly"
}
```

Example:

```bash
curl -X POST "http://localhost:8080/api/drafts/generate" \
  -H "Content-Type: application/json" \
  -d '{"userId":"...","gmailMessageId":"...","threadId":"...","emailContent":"...","tone":"friendly"}'
```

The generated draft is stored in `draft` and the action is logged in `draft_log`.

---

## Step 4 — Review drafts

### List drafts

- `GET /api/drafts`
- Optional filter:
  - `GET /api/drafts?statuses=PENDING,EDITED,APPROVED`

### Get a draft by id

- `GET /api/drafts/{draftId}`

---

## Step 5 — Edit or reject

### Edit

- `POST /api/drafts/{draftId}/edit`

Body:

```json
{ "draftText": "<your updated draft>" }
```

### Reject

- `POST /api/drafts/{draftId}/reject`

---

## Step 6 — Approve and send

### Approve

- `POST /api/drafts/{draftId}/approve`

### Send

- `POST /api/drafts/{draftId}/send`

Sending behavior:

- If the draft was created with a `gmailMessageId`, the system sends a **threaded reply**:
  - Uses `threadId`
  - Sets `In-Reply-To` and `References`
  - Sends to the original sender extracted from `From` header

Send attempts are tracked in:

- `send_attempt`

Final sent metadata is recorded in:

- `sent_message`

---

## Step 7 — Logout / revoke token

- `POST /auth/logout`

This sets `oauth_token.revoked_at`.

---

# API Summary

## Auth

- `GET /auth/success`
- `POST /auth/logout`

## Gmail

- `GET /api/gmail/unread?maxResults=10`

## Drafts

- `POST /api/drafts/generate`
- `GET /api/drafts`
- `GET /api/drafts/{draftId}`
- `POST /api/drafts/{draftId}/edit`
- `POST /api/drafts/{draftId}/reject`
- `POST /api/drafts/{draftId}/approve`
- `POST /api/drafts/{draftId}/send`

---

# Design Decisions (minimal, rubric-focused)

- **Workflow service**: `DraftWorkflowService` is the single orchestration layer for draft lifecycle (generate/edit/approve/reject/send).
- **Persistence-first for reliability**:
  - Drafts, logs, attempts, and sent messages are stored in DB.
  - Sends are idempotent using the existence of `sent_message` per `draft_id`.
- **Retries**:
  - `DraftRetryService` periodically retries `FAILED`/`APPROVED` drafts (simple scheduler).
  - `GmailClient` refreshes the token and retries once on failure.
- **AI provider selection**:
  - `AiServiceRouter` selects Gemini/OpenAI by `ai.provider`.
- **Security**:
  - `/api/**` requires OAuth login.
  - Refresh token encryption is supported via `token.crypto.key`.

---

## Repo Structure (quick)

- `controller/` REST APIs
- `service/workflow/` orchestration (draft lifecycle)
- `external/gmail/` Gmail API integration
- `external/google/` token refresh
- `domain/entity/` JPA entities
- `repository/` Spring Data repositories
- `db/migration/` Flyway migrations
