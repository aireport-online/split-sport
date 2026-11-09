package online.aireport.split.data.api

import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Builds [SplitApi] clients that share one connection pool. */
class SplitApiFactory(
    private val baseClient: OkHttpClient = defaultClient(),
) {
    /** A client for one server; with a [token] every request is sent as that session. */
    fun create(baseUrl: HttpUrl, token: String?): SplitApi {
        val client = if (token == null) {
            baseClient
        } else {
            baseClient.newBuilder()
                .addInterceptor { chain ->
                    chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer $token").build())
                }
                .build()
        }
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(SplitJson.asConverterFactory(JSON_MEDIA_TYPE))
            .build()
            .create(SplitApi::class.java)
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=UTF-8".toMediaType()

        // Reading a receipt takes the server a while, so wait longer than the default 10 s.
        private const val READ_TIMEOUT_SECONDS = 90L

        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder().readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS).build()
    }
}

/** JSON as the Split API speaks it: unknown keys are fine, absent values are left out. */
val SplitJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
