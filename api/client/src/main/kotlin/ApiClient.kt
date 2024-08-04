import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import de.binarynoise.captiveportalautologin.api.Api
import de.binarynoise.captiveportalautologin.api.json.har.HAR
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.post
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody

val MEDIA_TYPE_JSON = "application/json; charset=utf-8".toMediaType()

class ApiClient(val base: HttpUrl) : Api {
    private val httpClient = OkHttpClient()
    
    override val har = object : Api.Har {
        override fun submitHar(name: String, har: HAR) {
            val json = har.toJson()
            val response = httpClient.post(base, "har/$name") {
                post(json.toRequestBody(MEDIA_TYPE_JSON))
            }
            response.checkSuccess()
        }
    }
    override val liberator = object : Api.Liberator {
        override fun getLiberatorVersion(): String {
            TODO("Not yet implemented")
        }
        
        override fun fetchLiberatorUpdate() {
            TODO("Not yet implemented")
        }
        
        override fun reportError(error: Api.Liberator.Error) {
            TODO("Not yet implemented")
        }
        
        override fun reportSuccess(success: Api.Liberator.Success) {
            TODO("Not yet implemented")
        }
    }
}

fun HAR.toJson(): String {
    return serializer.encodeToString(this)
}

@OptIn(ExperimentalSerializationApi::class)
val serializer = Json {
    encodeDefaults = false
    explicitNulls = false
}
