package de.binarynoise.captiveportalautologin.server.database

import kotlin.time.Instant
import androidx.room.Dao
import androidx.room.Query

@Dao
interface SSIDDao {
    @Query(
        """
        WITH raw_data AS (
            -- Step 1: Consolidate all successes and errors into one stream
            SELECT ssid, 'success' as type, version, timestamp
            FROM successes
            UNION ALL
            SELECT ssid, 'error' as type, version, timestamp
            FROM errors
        ),
        filtered_data AS (
            -- Step 2: Filter data by given parameters
            SELECT ssid , type
            FROM raw_data
            WHERE version NOT LIKE '%+%'
              AND CAST(SUBSTR(version, 1, INSTR(version, '-') - 1) AS INTEGER) <= :maximumMajorVersion
              AND timestamp >= :since
        ),
        ssid_totals AS (
            -- Step 3: Aggregate totals per SSID
            SELECT 
                ssid,
                SUM(CASE WHEN type = 'success' THEN 1 ELSE 0 END) AS success_count,
                SUM(CASE WHEN type = 'error' THEN 1 ELSE 0 END) AS error_count
            FROM filtered_data
            GROUP BY ssid
        ),
        qualified_ssids AS (
            -- Step 4: Filter for SSIDs that meet the :minimum requirement.
            SELECT *
            FROM ssid_totals
            WHERE success_count >= :minimumSuccesses
        ),
        global_stats AS (
            -- Step 5: Calculate the global rate
            SELECT (SUM(success_count) * 1.0) / NULLIF(SUM(success_count) + SUM(error_count), 0) AS global_rate
            FROM qualified_ssids
        ),
        bayesian_ranking AS (
            -- Step 6: Bayesian Ranking
            SELECT 
                ssid, 
                success_count, 
                error_count,
                -- Bayesian Formula: (Successes + (Global_Rate * W)) / (Total_Attempts + W)
                (success_count + (global_rate * :bayesianWeight)) / (success_count + error_count + :bayesianWeight) AS bayesian_rating
            FROM qualified_ssids
            CROSS JOIN global_stats
        )
        
        SELECT ssid
        FROM bayesian_ranking
        WHERE bayesian_rating > :minimumBayesianRating
        ORDER BY bayesian_rating DESC
        LIMIT :limit
        """
    )
    /**
     * get list of SSIDs from the database
     * sorted by descending bayesian rating
     * @param limit maximum amount of SSIDs to return
     * @param maximumMajorVersion only count feedback of this major version or below
     * @param since only count feedback after this timestamp
     * @param minimumSuccesses only count SSIDs with at least this amount of successes (after previous filters)
     * @param minimumBayesianRating set a minimum bayesian rating to filter likely disfunctional SSIDs
     * @param bayesianWeight define approximately at how many successes over average SSIDs count as statistically significant
     */
    suspend fun getSSIDs(
        limit: Int = Int.MAX_VALUE,
        maximumMajorVersion: Int = Int.MAX_VALUE,
        since: Instant = Instant.DISTANT_PAST,
        minimumSuccesses: Int = 0,
        minimumBayesianRating: Float = 0.0f,
        bayesianWeight: Int = 10,
    ): List<String>
}
