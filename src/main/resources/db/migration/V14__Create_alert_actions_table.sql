-- Create alert_actions table for user actions on alerts
-- This table stores all actions users can take on security alerts

CREATE TABLE IF NOT EXISTS alert_actions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    breach_alert_id BIGINT NOT NULL REFERENCES breach_alerts(id) ON DELETE CASCADE,
    
    -- Action details
    action_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'NEW',
    title VARCHAR(500) NOT NULL,
    description TEXT,
    user_message TEXT,
    admin_response TEXT,
    
    -- Contact information for service requests
    contact_email VARCHAR(255),
    contact_phone VARCHAR(20),
    company_name VARCHAR(100),
    
    -- Service request details
    urgency_level VARCHAR(20) DEFAULT 'MEDIUM',
    estimated_budget VARCHAR(50),
    preferred_timeline VARCHAR(100),
    additional_context TEXT,
    
    -- Processing flags
    is_processed BOOLEAN NOT NULL DEFAULT false,
    is_service_request BOOLEAN NOT NULL DEFAULT false,
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    scheduled_for TIMESTAMP,
    
    -- Constraints
    CONSTRAINT alert_actions_action_type_check 
        CHECK (action_type IN (
            'SECURITY_ASSESSMENT', 'CODE_INVESTIGATION', 'INCIDENT_RESPONSE', 
            'EXPERT_CONSULTATION', 'COMPLIANCE_CHECK', 'SECURITY_TRAINING',
            'MARK_RESOLVED', 'MARK_FALSE_POSITIVE', 'ACKNOWLEDGE', 'ESCALATE'
        )),
    CONSTRAINT alert_actions_status_check 
        CHECK (status IN ('NEW', 'VIEWED', 'ACKNOWLEDGED', 'RESOLVED', 'DISMISSED')),
    CONSTRAINT alert_actions_urgency_check 
        CHECK (urgency_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    
    -- Prevent duplicate actions of same type for same alert
    UNIQUE(user_id, breach_alert_id, action_type)
);

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_alert_actions_user_id ON alert_actions(user_id);
CREATE INDEX IF NOT EXISTS idx_alert_actions_breach_alert_id ON alert_actions(breach_alert_id);
CREATE INDEX IF NOT EXISTS idx_alert_actions_action_type ON alert_actions(action_type);
CREATE INDEX IF NOT EXISTS idx_alert_actions_status ON alert_actions(status);
CREATE INDEX IF NOT EXISTS idx_alert_actions_created_at ON alert_actions(created_at);
CREATE INDEX IF NOT EXISTS idx_alert_actions_is_service_request ON alert_actions(is_service_request);
CREATE INDEX IF NOT EXISTS idx_alert_actions_is_processed ON alert_actions(is_processed);

-- Compound indexes for common queries
CREATE INDEX IF NOT EXISTS idx_alert_actions_user_status ON alert_actions(user_id, status);
CREATE INDEX IF NOT EXISTS idx_alert_actions_user_processed ON alert_actions(user_id, is_processed);
CREATE INDEX IF NOT EXISTS idx_alert_actions_service_pending 
    ON alert_actions(is_service_request, is_processed) 
    WHERE is_service_request = true AND is_processed = false;
