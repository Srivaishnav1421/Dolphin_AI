CREATE TABLE brain_feedback_patterns (
    id VARCHAR(36) PRIMARY KEY,
    product VARCHAR(255) NOT NULL,
    tone VARCHAR(50) NOT NULL,
    audience VARCHAR(255) NOT NULL,
    platform VARCHAR(50) NOT NULL,
    pattern_status VARCHAR(50) NOT NULL, -- 'HIGH_PERFORMING' or 'LOW_PERFORMING'
    roas DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_feedback_patterns_product ON brain_feedback_patterns(product);
