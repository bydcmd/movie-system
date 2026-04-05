-- Migration: Reorder user funnel stages and update conversion rates
-- New funnel order: view -> watched -> rating -> comment -> favorite
-- Date: 2026-04-05

-- ============================================================================
-- Step 1: Add new columns for reordered metrics
-- ============================================================================

ALTER TABLE "public"."stats_user_funnel_1d"
ADD COLUMN IF NOT EXISTS "view_to_watched_rate" numeric(10,4),
ADD COLUMN IF NOT EXISTS "watched_to_rating_rate" numeric(10,4);

-- ============================================================================
-- Step 2: Migrate data from old columns to new columns
-- Note: We cannot calculate new rates from existing data since the 
-- intermediate counts (view_to_watched_users, watched_to_rating_users) 
-- were not previously stored. These will be recalculated on next ETL run.
-- ============================================================================

-- Set new rate columns to NULL (will be populated by next ETL run)
UPDATE "public"."stats_user_funnel_1d"
SET 
    "view_to_watched_rate" = NULL,
    "watched_to_rating_rate" = NULL;

-- ============================================================================
-- Step 3: Drop old columns
-- ============================================================================

ALTER TABLE "public"."stats_user_funnel_1d"
DROP COLUMN IF EXISTS "view_to_rating_rate",
DROP COLUMN IF EXISTS "favorite_to_watched_rate";

-- ============================================================================
-- Step 4: Add comments for new columns
-- ============================================================================

COMMENT ON COLUMN "public"."stats_user_funnel_1d"."view_to_watched_rate" IS '浏览到看过转化率';
COMMENT ON COLUMN "public"."stats_user_funnel_1d"."watched_to_rating_rate" IS '看过到评分转化率';

-- ============================================================================
-- Step 5: Reorder columns (optional, for consistency with DDL)
-- PostgreSQL doesn't support direct column reordering, but we can 
-- recreate the table if needed. For most purposes, this is not required.
-- ============================================================================

-- The column order in PostgreSQL doesn't affect functionality.
-- Queries should always reference columns by name, not position.

-- ============================================================================
-- Verification query (run manually to verify migration)
-- ============================================================================

-- SELECT column_name, data_type, is_nullable
-- FROM information_schema.columns
-- WHERE table_schema = 'public' 
--   AND table_name = 'stats_user_funnel_1d'
-- ORDER BY ordinal_position;
