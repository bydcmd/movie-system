-- Migration: replace user funnel table with user_behavior_sankey_1d, keep search funnel
-- Date: 2026-04-05
-- Description: Drop ads_user_funnel_1d (replaced by Sankey),
--              keep ads_search_funnel_1d (still used by backend API).

CREATE DATABASE IF NOT EXISTS ads;
USE ads;

-- Drop old user funnel table (replaced by Sankey)
DROP TABLE IF EXISTS ads.ads_user_funnel_1d;

CREATE EXTERNAL TABLE IF NOT EXISTS ads.ads_user_behavior_sankey_1d (
  source_node string COMMENT 'Sankey source node name',
  target_node string COMMENT 'Sankey target node name',
  user_count bigint COMMENT 'Number of users flowing from source to target'
)
PARTITIONED BY (dt string)
STORED AS ORC
LOCATION '/warehouse/movie/ads/user_behavior_sankey_1d'
TBLPROPERTIES ('orc.compress'='SNAPPY');
