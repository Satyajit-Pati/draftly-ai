-- 0. Enable uuid-ossp if not already
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. Draft status enum
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'draft_status') THEN
    CREATE TYPE draft_status AS ENUM (
      'PENDING',    -- AI generated, waiting for user
      'EDITED',     -- user edited but not approved
      'APPROVED',   -- user approved for send
      'SENDING',    -- in-flight to Gmail
      'SENT',       -- successfully sent
      'FAILED',     -- send failed
      'REJECTED'    -- user rejected
    );
  END IF;
END $$;

-- 2. users
CREATE TABLE IF NOT EXISTS app_user (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  email VARCHAR(320) NOT NULL UNIQUE,
  display_name VARCHAR(255),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

-- 3. oauth_tokens (store refresh token encrypted)
CREATE TABLE IF NOT EXISTS oauth_token (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  provider VARCHAR(50) NOT NULL, -- e.g., 'google'
  access_token TEXT,             -- optional; short-lived
  refresh_token_encrypted TEXT,  -- encrypted at rest (see notes)
  scope TEXT,
  expires_at TIMESTAMP WITH TIME ZONE, -- token expiry
  revoked_at TIMESTAMP WITH TIME ZONE,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
  UNIQUE(user_id, provider)
);

CREATE INDEX IF NOT EXISTS idx_oauth_token_user ON oauth_token(user_id);

-- 4. gmail_emails (minimal metadata cache)
CREATE TABLE IF NOT EXISTS gmail_email (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  gmail_message_id VARCHAR(255) NOT NULL, -- Gmail's messageId
  thread_id VARCHAR(255),
  from_address VARCHAR(512),
  subject TEXT,
  snippet TEXT,
  label_ids TEXT[],        -- store labels like {INBOX,UNREAD}
  received_at TIMESTAMP WITH TIME ZONE,
  fetched_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
  UNIQUE(user_id, gmail_message_id)
);

CREATE INDEX IF NOT EXISTS idx_gmail_email_user_received ON gmail_email(user_id, received_at DESC);

-- 5. drafts
CREATE TABLE IF NOT EXISTS draft (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  gmail_message_id VARCHAR(255), -- original message this draft replies to
  thread_id VARCHAR(255),
  tone VARCHAR(50),              -- 'formal','friendly','concise' etc.
  draft_text TEXT,
  generated_by VARCHAR(50) DEFAULT 'AI', -- 'AI' or 'USER'
  status draft_status DEFAULT 'PENDING',
  idempotency_key VARCHAR(255),  -- optional, to avoid repeated generation/sends
  attempts INT DEFAULT 0,        -- incremented for send attempts
  max_attempts INT DEFAULT 3,
  last_error TEXT,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
  approved_at TIMESTAMP WITH TIME ZONE,
  rejected_at TIMESTAMP WITH TIME ZONE,
  sent_at TIMESTAMP WITH TIME ZONE
);

-- ensure only one draft generated per (user, gmail_message_id, idempotency_key) combination if desired
CREATE UNIQUE INDEX IF NOT EXISTS uq_user_msg_idempotency ON draft(user_id, gmail_message_id, idempotency_key);

CREATE INDEX IF NOT EXISTS idx_draft_user_status ON draft(user_id, status);

-- 6. draft_log (audit trail)
CREATE TABLE IF NOT EXISTS draft_log (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  draft_id UUID NOT NULL REFERENCES draft(id) ON DELETE CASCADE,
  actor VARCHAR(50) NOT NULL, -- 'USER' or 'SYSTEM'
  action VARCHAR(100) NOT NULL, -- 'GENERATED','EDITED','APPROVED','REJECTED','SEND_ATTEMPT','SENT','FAILED'
  meta JSONB,  -- optional structured metadata (like error codes, old/new text, request id)
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_draft_log_draft ON draft_log(draft_id, created_at DESC);

-- 7. send_attempt (detailed tracking)
CREATE TABLE IF NOT EXISTS send_attempt (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  draft_id UUID NOT NULL REFERENCES draft(id) ON DELETE CASCADE,
  attempt_no INT NOT NULL,
  status VARCHAR(50) NOT NULL, -- 'STARTED','FAILED','SUCCEEDED'
  gmail_sent_message_id VARCHAR(255),
  error_code VARCHAR(255),
  error_message TEXT,
  started_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
  finished_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_send_attempt_draft ON send_attempt(draft_id);

-- 8. sent_message (final metadata)
CREATE TABLE IF NOT EXISTS sent_message (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  draft_id UUID NOT NULL REFERENCES draft(id) ON DELETE CASCADE,
  gmail_message_id VARCHAR(255) NOT NULL,
  gmail_thread_id VARCHAR(255),
  sent_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_sent_message_draft ON sent_message(draft_id);

-- 9. user_preference
CREATE TABLE IF NOT EXISTS user_preference (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  default_tone VARCHAR(50) DEFAULT 'formal',
  signature TEXT,
  style_learning_enabled BOOLEAN DEFAULT true,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
  UNIQUE(user_id)
);

-- Optional: small table to store AI prompts/templates versioning
CREATE TABLE IF NOT EXISTS prompt_template (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  name VARCHAR(255) NOT NULL,
  description TEXT,
  template TEXT NOT NULL,
  active BOOLEAN DEFAULT true,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);