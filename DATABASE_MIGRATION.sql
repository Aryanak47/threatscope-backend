-- Migration script to update enums and fix inconsistencies
-- Run this after updating the entity classes

-- 1. Update Plans table to use new structure
ALTER TABLE plans 
ADD COLUMN IF NOT EXISTS plan_type VARCHAR(50),
ADD COLUMN IF NOT EXISTS webhook_alerts BOOLEAN DEFAULT FALSE;

-- Update existing plans to have plan_type based on name
UPDATE plans SET plan_type = 'FREE' WHERE UPPER(name) = 'FREE' OR UPPER(display_name) LIKE '%FREE%';
UPDATE plans SET plan_type = 'BASIC' WHERE UPPER(name) = 'BASIC' OR UPPER(display_name) LIKE '%BASIC%';
UPDATE plans SET plan_type = 'PROFESSIONAL' WHERE UPPER(name) = 'PROFESSIONAL' OR UPPER(display_name) LIKE '%PROFESSIONAL%' OR UPPER(display_name) LIKE '%PRO%';
UPDATE plans SET plan_type = 'ENTERPRISE' WHERE UPPER(name) = 'ENTERPRISE' OR UPPER(display_name) LIKE '%ENTERPRISE%';

-- Make plan_type not null after setting values
ALTER TABLE plans ALTER COLUMN plan_type SET NOT NULL;

-- 2. Update Subscriptions table
ALTER TABLE subscriptions 
ADD COLUMN IF NOT EXISTS plan_type VARCHAR(50),
ADD COLUMN IF NOT EXISTS max_searches_per_day INTEGER DEFAULT 10,
ADD COLUMN IF NOT EXISTS max_monitoring_items INTEGER DEFAULT 5,
ADD COLUMN IF NOT EXISTS max_exports_per_month INTEGER DEFAULT 3,
ADD COLUMN IF NOT EXISTS max_team_members INTEGER DEFAULT 1,
ADD COLUMN IF NOT EXISTS has_api_access BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS has_priority_support BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS has_dedicated_account_manager BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS can_export_results BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS can_monitor_domains BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS can_monitor_emails BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS can_monitor_keywords BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS can_use_api BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS has_custom_branding BOOLEAN DEFAULT FALSE;

-- Update subscription plan_type based on existing data
UPDATE subscriptions SET plan_type = 'FREE' WHERE UPPER(planType) = 'FREE';
UPDATE subscriptions SET plan_type = 'BASIC' WHERE UPPER(planType) = 'BASIC';
UPDATE subscriptions SET plan_type = 'PROFESSIONAL' WHERE UPPER(planType) = 'PROFESSIONAL';
UPDATE subscriptions SET plan_type = 'ENTERPRISE' WHERE UPPER(planType) = 'ENTERPRISE';
UPDATE subscriptions SET plan_type = 'CUSTOM' WHERE UPPER(planType) = 'CUSTOM';

-- Set plan_type to FREE if null
UPDATE subscriptions SET plan_type = 'FREE' WHERE plan_type IS NULL;

-- Make plan_type not null
ALTER TABLE subscriptions ALTER COLUMN plan_type SET NOT NULL;

-- Update billing_cycle to use consistent values
UPDATE subscriptions SET billingCycle = 'MONTHLY' WHERE UPPER(billingCycle) IN ('MONTH', 'MONTHLY');
UPDATE subscriptions SET billingCycle = 'QUARTERLY' WHERE UPPER(billingCycle) IN ('QUARTER', 'QUARTERLY');
UPDATE subscriptions SET billingCycle = 'ANNUALLY' WHERE UPPER(billingCycle) IN ('ANNUAL', 'ANNUALLY', 'YEARLY', 'YEAR');

-- Update subscription status to use new enum values
UPDATE subscriptions SET status = 'ACTIVE' WHERE UPPER(status) = 'ACTIVE';
UPDATE subscriptions SET status = 'TRIALING' WHERE UPPER(status) IN ('TRIAL', 'TRIALING');
UPDATE subscriptions SET status = 'PAST_DUE' WHERE UPPER(status) = 'PAST_DUE';
UPDATE subscriptions SET status = 'CANCELED' WHERE UPPER(status) IN ('CANCELLED', 'CANCELED');
UPDATE subscriptions SET status = 'UNPAID' WHERE UPPER(status) = 'UNPAID';
UPDATE subscriptions SET status = 'INCOMPLETE' WHERE UPPER(status) = 'INCOMPLETE';
UPDATE subscriptions SET status = 'INCOMPLETE_EXPIRED' WHERE UPPER(status) = 'INCOMPLETE_EXPIRED';
UPDATE subscriptions SET status = 'PAUSED' WHERE UPPER(status) = 'PAUSED';

-- 3. Update MonitoringItems table
ALTER TABLE monitoring_items 
ADD COLUMN IF NOT EXISTS webhook_alerts BOOLEAN DEFAULT FALSE;

-- Update monitor_type to ensure consistency
UPDATE monitoring_items SET monitorType = 'EMAIL' WHERE UPPER(monitorType) = 'EMAIL';
UPDATE monitoring_items SET monitorType = 'DOMAIN' WHERE UPPER(monitorType) = 'DOMAIN';
UPDATE monitoring_items SET monitorType = 'USERNAME' WHERE UPPER(monitorType) = 'USERNAME';
UPDATE monitoring_items SET monitorType = 'KEYWORD' WHERE UPPER(monitorType) = 'KEYWORD';
UPDATE monitoring_items SET monitorType = 'IP_ADDRESS' WHERE UPPER(monitorType) IN ('IP_ADDRESS', 'IP');
UPDATE monitoring_items SET monitorType = 'PHONE' WHERE UPPER(monitorType) IN ('PHONE', 'PHONE_NUMBER');
UPDATE monitoring_items SET monitorType = 'ORGANIZATION' WHERE UPPER(monitorType) IN ('ORGANIZATION', 'ORG');

-- Update frequency to ensure consistency
UPDATE monitoring_items SET frequency = 'REAL_TIME' WHERE UPPER(frequency) IN ('REAL_TIME', 'REALTIME', 'REAL-TIME');
UPDATE monitoring_items SET frequency = 'HOURLY' WHERE UPPER(frequency) = 'HOURLY';
UPDATE monitoring_items SET frequency = 'DAILY' WHERE UPPER(frequency) = 'DAILY';
UPDATE monitoring_items SET frequency = 'WEEKLY' WHERE UPPER(frequency) = 'WEEKLY';

-- 4. Update BreachAlerts table structure
ALTER TABLE breach_alerts 
ADD COLUMN IF NOT EXISTS affected_email VARCHAR(255),
ADD COLUMN IF NOT EXISTS affected_domain VARCHAR(255),
ADD COLUMN IF NOT EXISTS affected_username VARCHAR(255),
ADD COLUMN IF NOT EXISTS data_types TEXT,
ADD COLUMN IF NOT EXISTS record_count BIGINT,
ADD COLUMN IF NOT EXISTS is_verified BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS acknowledgment_notes TEXT,
ADD COLUMN IF NOT EXISTS risk_score INTEGER,
ADD COLUMN IF NOT EXISTS confidence_level INTEGER,
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
ADD COLUMN IF NOT EXISTS viewed_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS acknowledged_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS dismissed_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS escalated_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS email_notification_sent BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS webhook_notification_sent BOOLEAN DEFAULT FALSE;

-- Update alert status to use new enum values
UPDATE breach_alerts SET status = 'NEW' WHERE UPPER(status) IN ('UNREAD', 'NEW');
UPDATE breach_alerts SET status = 'VIEWED' WHERE UPPER(status) IN ('READ', 'VIEWED');
UPDATE breach_alerts SET status = 'ACKNOWLEDGED' WHERE UPPER(status) = 'ACKNOWLEDGED';
UPDATE breach_alerts SET status = 'RESOLVED' WHERE UPPER(status) IN ('RESOLVED', 'ARCHIVED');
UPDATE breach_alerts SET status = 'DISMISSED' WHERE UPPER(status) IN ('DISMISSED', 'IGNORED');

-- Update severity to use new enum values
UPDATE breach_alerts SET severity = 'CRITICAL' WHERE UPPER(severity) = 'CRITICAL';
UPDATE breach_alerts SET severity = 'HIGH' WHERE UPPER(severity) = 'HIGH';
UPDATE breach_alerts SET severity = 'MEDIUM' WHERE UPPER(severity) = 'MEDIUM';
UPDATE breach_alerts SET severity = 'LOW' WHERE UPPER(severity) = 'LOW';

-- Set viewed_at for read alerts
UPDATE breach_alerts SET viewed_at = readAt WHERE readAt IS NOT NULL AND viewed_at IS NULL;
UPDATE breach_alerts SET resolved_at = resolvedAt WHERE resolvedAt IS NOT NULL AND resolved_at IS NULL;

-- 5. Create default plans if they don't exist
INSERT INTO plans (plan_type, display_name, description, price, billing_cycle, daily_searches, monthly_searches, max_monitoring_items, monitoring_frequencies, max_alerts_per_day, alert_retention_days, daily_exports, monthly_exports, api_access, real_time_monitoring, email_alerts, in_app_alerts, webhook_alerts, priority_support, custom_integrations, advanced_analytics, is_active, is_public, sort_order, created_at, updated_at)
VALUES 
('FREE', 'Free Plan', 'Basic monitoring for individuals', 0.00, 'MONTHLY', 5, 150, 3, '["DAILY", "WEEKLY"]', 10, 30, 0, 0, FALSE, FALSE, TRUE, TRUE, FALSE, FALSE, FALSE, FALSE, TRUE, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('BASIC', 'Basic Plan', 'Enhanced monitoring for small teams', 9.99, 'MONTHLY', 25, 750, 10, '["HOURLY", "DAILY", "WEEKLY"]', 50, 60, 3, 10, FALSE, FALSE, TRUE, TRUE, FALSE, FALSE, FALSE, FALSE, TRUE, TRUE, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('PROFESSIONAL', 'Professional Plan', 'Advanced monitoring for growing businesses', 29.99, 'MONTHLY', 100, 3000, 50, '["REAL_TIME", "HOURLY", "DAILY", "WEEKLY"]', 200, 90, 10, 50, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, FALSE, TRUE, TRUE, TRUE, 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ENTERPRISE', 'Enterprise Plan', 'Complete monitoring solution for large organizations', 99.99, 'MONTHLY', 1000, 30000, 200, '["REAL_TIME", "HOURLY", "DAILY", "WEEKLY"]', 1000, 365, 50, 200, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (plan_type) DO NOTHING;

-- 6. Update monitoring frequencies for existing plans
UPDATE plans SET monitoring_frequencies = '["DAILY", "WEEKLY"]' WHERE plan_type = 'FREE';
UPDATE plans SET monitoring_frequencies = '["HOURLY", "DAILY", "WEEKLY"]' WHERE plan_type = 'BASIC';
UPDATE plans SET monitoring_frequencies = '["REAL_TIME", "HOURLY", "DAILY", "WEEKLY"]' WHERE plan_type IN ('PROFESSIONAL', 'ENTERPRISE');

-- 7. Sync subscription limits from plans
UPDATE subscriptions s SET 
    max_searches_per_day = p.daily_searches,
    max_monitoring_items = p.max_monitoring_items,
    max_exports_per_month = p.monthly_exports,
    has_api_access = p.api_access,
    has_priority_support = p.priority_support,
    can_export_results = (p.daily_exports > 0),
    can_monitor_domains = (p.max_monitoring_items > 0),
    can_monitor_emails = (p.max_monitoring_items > 0),
    can_monitor_keywords = (p.max_monitoring_items > 0),
    can_use_api = p.api_access,
    has_custom_branding = p.custom_integrations
FROM plans p 
WHERE s.plan_id = p.id;

-- 8. Clean up old columns (run these after confirming the migration works)
-- ALTER TABLE plans DROP COLUMN IF EXISTS name;
-- ALTER TABLE subscriptions DROP COLUMN IF EXISTS planType;
-- ALTER TABLE breach_alerts DROP COLUMN IF EXISTS readAt;
-- ALTER TABLE breach_alerts DROP COLUMN IF EXISTS archivedAt;
-- ALTER TABLE breach_alerts DROP COLUMN IF EXISTS resolvedAt;

-- 9. Add indexes for better performance
CREATE INDEX IF NOT EXISTS idx_plans_plan_type ON plans(plan_type);
CREATE INDEX IF NOT EXISTS idx_plans_is_active ON plans(is_active);
CREATE INDEX IF NOT EXISTS idx_subscriptions_plan_type ON subscriptions(plan_type);
CREATE INDEX IF NOT EXISTS idx_subscriptions_status ON subscriptions(status);
CREATE INDEX IF NOT EXISTS idx_monitoring_items_monitor_type ON monitoring_items(monitor_type);
CREATE INDEX IF NOT EXISTS idx_monitoring_items_frequency ON monitoring_items(frequency);
CREATE INDEX IF NOT EXISTS idx_monitoring_items_is_active ON monitoring_items(is_active);
CREATE INDEX IF NOT EXISTS idx_breach_alerts_status ON breach_alerts(status);
CREATE INDEX IF NOT EXISTS idx_breach_alerts_severity ON breach_alerts(severity);
CREATE INDEX IF NOT EXISTS idx_breach_alerts_created_at ON breach_alerts(created_at);

-- 10. Update any existing user roles to use consistent enum values
UPDATE users SET role = 'USER' WHERE UPPER(role) IN ('USER', 'CUSTOMER', 'MEMBER');
UPDATE users SET role = 'ADMIN' WHERE UPPER(role) IN ('ADMIN', 'ADMINISTRATOR');
UPDATE users SET role = 'SUPER_ADMIN' WHERE UPPER(role) IN ('SUPER_ADMIN', 'SUPERADMIN', 'ROOT');

COMMIT;
