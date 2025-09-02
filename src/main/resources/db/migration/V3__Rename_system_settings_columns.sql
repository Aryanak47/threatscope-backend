-- First, add the new columns as nullable
ALTER TABLE system_settings 
    ADD COLUMN IF NOT EXISTS setting_key VARCHAR(255),
    ADD COLUMN IF NOT EXISTS setting_value VARCHAR(1000);

-- Copy data from old columns to new columns
UPDATE system_settings 
SET setting_key = key,
    setting_value = value;

-- Make the new columns non-nullable
ALTER TABLE system_settings 
    ALTER COLUMN setting_key SET NOT NULL,
    ALTER COLUMN setting_value SET NOT NULL;

-- Drop the old columns
ALTER TABLE system_settings 
    DROP COLUMN IF EXISTS key,
    DROP COLUMN IF EXISTS value;
