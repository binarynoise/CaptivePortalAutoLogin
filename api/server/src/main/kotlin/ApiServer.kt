package de.binarynoise.captiveportalautologin.server

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.binarynoise.captiveportalautologin.api.Api
import de.binarynoise.captiveportalautologin.api.json.har.HAR
import de.binarynoise.captiveportalautologin.api.json.har.generateHarFileName
import de.binarynoise.captiveportalautologin.server.database.AppDatabase
import de.binarynoise.captiveportalautologin.server.database.ErrorEntity
import de.binarynoise.captiveportalautologin.server.database.SuccessEntity
import de.binarynoise.captiveportalautologin.server.routes.stats.Comparators
import de.binarynoise.captiveportalautologin.server.routes.toDuration
import de.binarynoise.filedb.FileDB
import de.binarynoise.logger.Logger.log
import de.binarynoise.util.json.prettyPrinter
import de.binarynoise.util.json.serializer
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.readText
import io.ktor.http.Url
import okhttp3.OkHttpClient

val feedbackSuccessDelta: Duration = System.getenv("STATS_SUCCESS_DELTA")?.toDuration() ?: Duration.ZERO
val feedbackErrorDelta: Duration = System.getenv("STATS_ERROR_DELTA")?.toDuration() ?: Duration.ZERO

class ApiServer(root: Path = Path(".")) : Api {
    
    companion object {
        lateinit var api: ApiServer
    }
    
    val harDB = FileDB(root, "HAR", "har")
    val harDBArchived = FileDB(root, "HAR/archived", "har")
    val harDBError = FileDB(root, "HAR/error", "har")
    val logDB = FileDB(root, "LOG", "log")
    val logDBArchived = FileDB(root, "LOG/archived", "log")
    
    val database = AppDatabase.createDatabase(root)
    
    init {
        runBlocking {
            database.useConnection(isReadOnly = true) {}
        }
        log("Database initialized at root ${root.toAbsolutePath()}")
    }
    
    override val har: Api.Har = object : Api.Har {
        override fun submitHar(name: String, har: HAR) {
            harDB.store(name, prettyPrinter.encodeToString(har))
            log("stored har $name")
        }
    }
    
    override val log: Api.Log = object : Api.Log {
        override fun submitLog(name: String, log: String) {
            logDB.store(name, log)
            log("stored log $name")
        }
    }
    
    override val liberator: Api.Liberator = object : Api.Liberator {
        override fun getLiberatorVersion(): String {
            TODO("getLiberatorVersion Not yet implemented")
        }
        
        override fun fetchLiberatorUpdate() {
            TODO("fetchLiberatorUpdate Not yet implemented")
        }
        
        override fun reportError(error: Api.Liberator.Error) {
            val timestamp = Instant.fromEpochMilliseconds(error.timestamp)
            
            val har: HAR? = error.har
            val harName = if (har == null) {
                null
            } else with(error) {
                val host = Url(url).host
                val name = generateHarFileName(ssid, host, timestamp)
                harDBError.store(name, prettyPrinter.encodeToString(har))
                name
            }
            
            runBlocking {
                val errorEntity = ErrorEntity(
                    version = error.version,
                    timestamp = timestamp,
                    ssid = error.ssid,
                    url = error.url,
                    message = error.message,
                    solver = error.solver.orEmpty(),
                    stackTrace = error.stackTrace.orEmpty(),
                    harName = harName,
                )
                val replacedError = database.errorDao().upsertSimilarError(errorEntity, feedbackErrorDelta)
                if (replacedError != null && replacedError.harName != null) {
                    harDBError.delete(replacedError.harName)
                }
            }
            log("Stored Api.Liberator.Error: $error")
        }
        
        override fun reportSuccess(success: Api.Liberator.Success) {
            runBlocking {
                val successEntity = SuccessEntity(
                    version = success.version,
                    timestamp = Instant.fromEpochMilliseconds(success.timestamp),
                    ssid = success.ssid,
                    url = success.url,
                    solver = success.solver.orEmpty(),
                )
                database.successDao().upsertSimilarSuccess(successEntity, feedbackSuccessDelta)
            }
            log("Stored Api.Liberator.Success: $success")
        }
    }
    
    override suspend fun getSSIDs(
        limit: Int?,
        maximumMajorVersion: Int?,
        since: Instant?,
        minimumSuccesses: Int?,
        minimumBayesianRating: Float?,
        bayesianWeight: Int?,
    ): List<String> {
        return database.SSIDDao().getSSIDs(
            limit = limit ?: 1024,
            maximumMajorVersion = maximumMajorVersion ?: Int.MAX_VALUE,
            since = since ?: (Clock.System.now() - 365.25.days),
            minimumSuccesses = minimumSuccesses ?: 0,
            minimumBayesianRating = minimumBayesianRating ?: 0.4f,
            bayesianWeight = bayesianWeight ?: 10,
        )
    }
    
    override suspend fun checkUpdate(installedVersion: String): Api.Update? {
        val release = fetchLatestRelease() ?: return null
        if (Comparators.VersionComparator.compare(installedVersion, release.tagName) >= 0) return null
        return Api.Update(version = release.tagName, url = release.htmlUrl)
    }
}

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
)

private val client = OkHttpClient()

private var cachedRelease: GitHubRelease? = null
private var cachedReleaseFetchedAt: Instant? = null

private suspend fun fetchLatestRelease(): GitHubRelease? {
    val now = Clock.System.now()
    val fetchedAt = cachedReleaseFetchedAt
    if (cachedRelease != null && fetchedAt != null && now - fetchedAt < 1.days) {
        return cachedRelease
    }
    val repo = System.getenv("GITHUB_REPO") ?: "binarynoise/CaptivePortalAutoLogin"
    
    val json = withContext(Dispatchers.IO) {
        val response = client.get(null, "https://api.github.com/repos/$repo/releases/latest") {
            header("User-Agent", "CaptivePortalAutoLogin-UpdateChecker")
            header("Accept", "application/vnd.github+json")
        }
        response.readText()
    }
    val release: GitHubRelease = serializer.decodeFromString(json)
    
    cachedRelease = release
    cachedReleaseFetchedAt = now
    log("Fetched latest release from GitHub: ${release.tagName}")
    return release
}
