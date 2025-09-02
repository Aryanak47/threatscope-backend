-- Drop existing tables if they exist
DROP TABLE IF EXISTS anonymous_usage CASCADE;
DROP TABLE IF EXISTS user_usage CASCADE;
DROP TABLE IF EXISTS monitoring_items CASCADE;
DROP TABLE IF EXISTS subscriptions CASCADE;
DROP TABLE IF EXISTS system_settings CASCADE;

CREATE TABLE system_settings (
    id BIGSERIAL PRIMARY KEY,
    key VARCHAR(255) UNIQUE NOT NULL,
    value TEXT NOT NULL,
    description TEXT,
    data_type VARCHAR(50) NOT NULL DEFAULT 'STRING',
    category VARCHAR(50) NOT NULL DEFAULT 'SYSTEM',
    is_editable BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE subscriptions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    plan_type VARCHAR(50) NOT NULL DEFAULT 'FREE',
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    billing_cycle VARCHAR(50),
    current_period_start TIMESTAMP,
    current_period_end TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE monitoring_items (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    query VARCHAR(500) NOT NULL,
    type VARCHAR(50) NOT NULL,
    alert_enabled BOOLEAN DEFAULT true,
    frequency VARCHAR(50) DEFAULT 'DAILY',
    last_checked TIMESTAMP,
    status VARCHAR(50) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE user_usage (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    usage_date DATE NOT NULL,
    searches_count INTEGER DEFAULT 0,
    exports_count INTEGER DEFAULT 0,
    api_calls_count INTEGER DEFAULT 0,
    monitoring_items_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE(user_id, usage_date)
);

CREATE TABLE anonymous_usage (
    id BIGSERIAL PRIMARY KEY,
    ip_address VARCHAR(45) NOT NULL,
    usage_date DATE NOT NULL,
    searches_count INTEGER DEFAULT 0,
    user_agent TEXT,
    country VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(ip_address, usage_date)
);

-- Insert default system settings
INSERT INTO system_settings (key, value, description, data_type, category) VALUES
('anonymous.daily_searches', '5', 'Daily search limit for anonymous users', 'INTEGER', 'RATE_LIMITS'),
('free.daily_searches', '25', 'Daily search limit for free users', 'INTEGER', 'RATE_LIMITS'),
('basic.daily_searches', '100', 'Daily search limit for basic users', 'INTEGER', 'RATE_LIMITS'),
('professional.hourly_searches', '50', 'Hourly search limit for professional users', 'INTEGER', 'RATE_LIMITS'),
('enterprise.hourly_searches', '1000', 'Hourly search limit for enterprise users', 'INTEGER', 'RATE_LIMITS');

-- Create indexes
CREATE INDEX idx_user_usage_user_date ON user_usage(user_id, usage_date);
CREATE INDEX idx_anonymous_usage_ip_date ON anonymous_usage(ip_address, usage_date);
CREATE INDEX idx_monitoring_items_user ON monitoring_items(user_id);
CREATE INDEX idx_subscriptions_user ON subscriptions(user_id);
