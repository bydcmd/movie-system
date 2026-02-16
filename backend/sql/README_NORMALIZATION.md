# Database Normalization: Genres and Regions Migration

## Overview

This document describes the database normalization refactoring for the movie system, specifically normalizing the `genres` and `regions` fields from comma-separated strings to properly structured many-to-many relationships.

## Problem Statement

**Before Refactoring:**
- `movies` table had `genres` and `regions` columns storing comma-separated values
- Example: `genres = "动作,科幻,冒险"`, `regions = "美国,中国"`
- Issues:
  - Difficult to query and filter efficiently
  - No referential integrity
  - Data redundancy and inconsistency
  - Poor database normalization (violates 1NF)

**After Refactoring:**
- Separate `genres` and `regions` tables with unique IDs
- Junction tables `movie_genre_relation` and `movie_region_relation` for many-to-many relationships
- Proper normalization following database best practices

## Database Schema Changes

### New Tables

#### 1. genres
```sql
CREATE TABLE `genres` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '类型ID',
  `name` varchar(50) NOT NULL COMMENT '类型名称',
  `name_en` varchar(50) NULL DEFAULT NULL COMMENT '英文名称',
  `description` varchar(255) NULL DEFAULT NULL COMMENT '类型描述',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_name`(`name`)
);
```

#### 2. regions
```sql
CREATE TABLE `regions` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '地区ID',
  `name` varchar(50) NOT NULL COMMENT '地区名称',
  `name_en` varchar(50) NULL DEFAULT NULL COMMENT '英文名称',
  `description` varchar(255) NULL DEFAULT NULL COMMENT '地区描述',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_name`(`name`)
);
```

#### 3. movie_genre_relation (Junction Table)
```sql
CREATE TABLE `movie_genre_relation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `movie_id` bigint NOT NULL COMMENT '电影ID',
  `genre_id` int NOT NULL COMMENT '类型ID',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_movie_genre`(`movie_id`, `genre_id`),
  INDEX `idx_movie_id`(`movie_id`),
  INDEX `idx_genre_id`(`genre_id`)
);
```

#### 4. movie_region_relation (Junction Table)
```sql
CREATE TABLE `movie_region_relation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `movie_id` bigint NOT NULL COMMENT '电影ID',
  `region_id` int NOT NULL COMMENT '地区ID',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_movie_region`(`movie_id`, `region_id`),
  INDEX `idx_movie_id`(`movie_id`),
  INDEX `idx_region_id`(`region_id`)
);
```

## Migration Process

### Step 1: Run Migration Script

Execute the migration script to create new tables and migrate data:

```bash
mysql -u username -p database_name < backend/sql/normalize_genres_regions.sql
```

The script will:
1. Create `genres`, `regions`, and junction tables
2. Extract unique genres from `movies.genres` column
3. Extract unique regions from `movies.regions` column
4. Populate junction tables with movie-genre and movie-region relationships
5. Keep original `genres` and `regions` columns for backward compatibility

### Step 2: Verify Migration

After running the script, verify the data migration:

```sql
-- Check genres count
SELECT COUNT(*) AS total_genres FROM genres;
SELECT COUNT(*) AS total_movie_genre_relations FROM movie_genre_relation;

-- Check regions count
SELECT COUNT(*) AS total_regions FROM regions;
SELECT COUNT(*) AS total_movie_region_relations FROM movie_region_relation;

-- Sample: Compare old vs new data
SELECT m.movie_id, m.name, 
       m.genres AS old_genres,
       GROUP_CONCAT(g.name SEPARATOR ', ') AS new_genres
FROM movies m
LEFT JOIN movie_genre_relation mgr ON m.movie_id = mgr.movie_id
LEFT JOIN genres g ON mgr.genre_id = g.id
GROUP BY m.movie_id, m.name, m.genres
LIMIT 10;
```

### Step 3: Update Application Code

The application code has been updated to use the new normalized structure:

**Backend Changes:**
- Created `Genre.java` and `Region.java` entities
- Created `GenreMapper.java` and `RegionMapper.java` interfaces
- Created corresponding MyBatis XML mappers
- Updated `MovieServiceImpl.java` to use new mappers

**Query Example (Old vs New):**

Old way:
```sql
SELECT * FROM movies WHERE genres LIKE '%动作%';
```

New way:
```sql
SELECT m.* 
FROM movies m
INNER JOIN movie_genre_relation mgr ON m.movie_id = mgr.movie_id
INNER JOIN genres g ON mgr.genre_id = g.id
WHERE g.name = '动作';
```

### Step 4: Testing

Test the following scenarios:

1. **Get all genres:** `/movie/genres` endpoint
2. **Get all regions:** `/movie/regions` endpoint
3. **Search by genre:** Should work with new structure
4. **Search by region:** Should work with new structure
5. **Movie detail page:** Should display genres and regions correctly

### Step 5: Drop Old Columns (Optional)

⚠️ **WARNING:** Only execute after thorough testing!

Once you've verified that everything works correctly, you can drop the old columns:

```sql
ALTER TABLE `movies` DROP COLUMN `genres`;
ALTER TABLE `movies` DROP COLUMN `regions`;
```

## Code Changes Summary

### New Files Created

**Entities:**
- `backend/src/main/java/com/movie/backend/entity/Genre.java`
- `backend/src/main/java/com/movie/backend/entity/Region.java`

**Mappers:**
- `backend/src/main/java/com/movie/backend/mapper/GenreMapper.java`
- `backend/src/main/java/com/movie/backend/mapper/RegionMapper.java`
- `backend/src/main/resources/mapper/GenreMapper.xml`
- `backend/src/main/resources/mapper/RegionMapper.xml`

**Migration Script:**
- `backend/sql/normalize_genres_regions.sql`

### Modified Files

**Service Layer:**
- `backend/src/main/java/com/movie/backend/service/impl/MovieServiceImpl.java`
  - Updated `getAllGenres()` to use `GenreMapper`
  - Updated `getAllRegions()` to use `RegionMapper`

## Benefits of Normalization

1. **Data Integrity:** Foreign key relationships ensure data consistency
2. **Query Performance:** Indexed relationships for faster filtering
3. **Maintainability:** Easier to add/update/delete genres and regions
4. **Flexibility:** Support for additional metadata (English names, descriptions)
5. **Standards Compliance:** Follows proper database normalization principles

## Backward Compatibility

The migration keeps the original `genres` and `regions` columns in the `movies` table during the transition period. This ensures:
- Existing queries continue to work
- Gradual migration of application code
- Easy rollback if issues are discovered

## Performance Considerations

### Indexes
All junction tables have proper indexes:
- Primary key on `id`
- Unique index on `(movie_id, genre_id)` or `(movie_id, region_id)`
- Individual indexes on `movie_id` and `genre_id`/`region_id`

### Query Optimization
- Use JOIN operations efficiently
- Consider using `EXISTS` for existence checks
- Use `GROUP_CONCAT` for aggregating multiple values

### Example Optimized Queries

```sql
-- Get movies with specific genre (efficient)
SELECT m.* 
FROM movies m
WHERE EXISTS (
  SELECT 1 FROM movie_genre_relation mgr
  INNER JOIN genres g ON mgr.genre_id = g.id
  WHERE mgr.movie_id = m.movie_id AND g.name = '动作'
);

-- Get movies with multiple genres
SELECT m.*, GROUP_CONCAT(g.name SEPARATOR ', ') AS genres
FROM movies m
LEFT JOIN movie_genre_relation mgr ON m.movie_id = mgr.movie_id
LEFT JOIN genres g ON mgr.genre_id = g.id
GROUP BY m.movie_id;
```

## Rollback Plan

If issues arise, you can rollback by:

1. Stop using the new genre/region mappers
2. Revert service layer changes
3. Continue using the old `genres` and `regions` columns
4. Drop the new tables if desired:
   ```sql
   DROP TABLE movie_genre_relation;
   DROP TABLE movie_region_relation;
   DROP TABLE genres;
   DROP TABLE regions;
   ```

## Future Enhancements

1. Add foreign key constraints between tables
2. Add multilingual support for genre/region names
3. Create admin interfaces for managing genres/regions
4. Add genre/region popularity tracking
5. Support for genre hierarchies (parent-child relationships)

## Conclusion

This database normalization significantly improves the movie system's data structure, making it more maintainable, performant, and scalable. The migration process preserves backward compatibility while enabling modern database practices.

For questions or issues, please refer to the project documentation or contact the development team.
