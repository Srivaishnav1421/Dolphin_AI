CREATE TABLE lead_chat_messages (
    id VARCHAR(36) PRIMARY KEY,
    lead_id VARCHAR(36) NOT NULL,
    sender VARCHAR(20) NOT NULL, -- 'LEAD' or 'SDR_BOT'
    message VARCHAR(4000) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_message_lead FOREIGN KEY (lead_id) REFERENCES leads(id) ON DELETE CASCADE
);

CREATE INDEX idx_chat_messages_lead ON lead_chat_messages(lead_id);
