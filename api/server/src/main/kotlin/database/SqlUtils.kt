package de.binarynoise.captiveportalautologin.server.database

import org.intellij.lang.annotations.Language

@Language("RoomSql")
/**
 * generate time buckets given start, end and interval
 * requires a `WITH RECURSIVE`
 * buckets are aligned with the end, 
 * if the start does not align cleanly, it will be rounded up to the next aligned bucket start
 */
const val BUCKET_GENERATOR = """
      -- Generate a sequence of buckets
      buckets(bucket_start, bucket_end, interval_ms) AS (
        -- Anchor: Create the first bucket that ends exactly at end_time
        SELECT 
          :end - :interval, 
          :end, 
          :interval
        UNION ALL
        -- Recursive: The 'end' of this bucket is the 'start' of the previous one
        SELECT 
          bucket_start - :interval,
          bucket_start,
          :interval
        FROM buckets
        -- Stop when the next bucket's start would be lower than our start_time
        WHERE (bucket_start - :interval) >= :start
      )
""" 
