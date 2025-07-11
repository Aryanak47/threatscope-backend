-- Create the database if it doesn't exist
SELECT 'CREATE DATABASE threatscope_dev'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'threatscope_dev')\gexec

-- Connect to the database
\c threatscope_dev

-- Create necessary extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Add any additional initialization SQL here
-- For example, create tables, roles, etc.
