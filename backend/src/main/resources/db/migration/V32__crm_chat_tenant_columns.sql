ALTER TABLE lead_chat_messages ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(36);
ALTER TABLE lead_chat_messages ADD COLUMN IF NOT EXISTS conversation_id VARCHAR(100);
ALTER TABLE lead_chat_messages ADD COLUMN IF NOT EXISTS thread_id VARCHAR(100);

UPDATE lead_chat_messages m
SET workspace_id = l.account_id
FROM leads l
WHERE m.lead_id = l.id
  AND m.workspace_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_chat_messages_workspace_lead
    ON lead_chat_messages(workspace_id, lead_id, created_at);

CREATE INDEX IF NOT EXISTS idx_interactions_workspace_lead
    ON lead_interactions(workspace_id, lead_id, created_at);
