-- Migration: Add session tracking fields to event log table
-- All session fields are stored together in the event log table for simplicity

-- ============================================
-- Step 1: Create new event log table with all session fields
-- ============================================
CREATE EXTERNAL TABLE IF NOT EXISTS ods.ods_kafka_event_log_di_new (
  topic string,
  event_key string,
  kafka_timestamp timestamp,
  event_id string,
  event_type string,
  occurred_at timestamp,
  user_id string,
  movie_id bigint,
  comment_id bigint,
  folder_id bigint,
  folder_name string,
  folder_is_public tinyint,
  operation string,
  rating int,
  search_keyword string,
  result_count bigint,
  filter_conditions string,
  search_time bigint,
  event_data string,
  raw_json string,
  ingest_time timestamp,
  -- Session fields (all stored together in event table)
  session_id string,
  page_url string,
  sequence_number int,
  client_timestamp bigint,
  entry_url string,
  first_referrer string,
  user_agent string,
  device_type string,
  session_start_time bigint,
  first_client_timestamp bigint
)
PARTITIONED BY (dt string, hh string)
STORED AS ORC
LOCATION '/warehouse/movie/ods/kafka/event_log'
TBLPROPERTIES ('orc.compress'='SNAPPY');

-- ============================================
-- Step 2: Migrate existing data (optional - historical data won't have session info)
-- ============================================
-- Note: Historical data migration is skipped as session fields are new
-- The streaming job will populate session data going forward

-- ============================================
-- Step 3: Replace old table with new schema
-- WARNING: Stop the Spark streaming job before running this!
-- ============================================
DROP TABLE IF EXISTS ods.ods_kafka_event_log_di;

ALTER TABLE ods.ods_kafka_event_log_di RENAME TO ods.ods_kafka_event_log_di_backup;
ALTER TABLE ods.ods_kafka_event_log_di_new RENAME TO ods.ods_kafka_event_log_di;

-- Recover partitions for the new table
MSCK REPAIR TABLE ods.ods_kafka_event_log_di;
