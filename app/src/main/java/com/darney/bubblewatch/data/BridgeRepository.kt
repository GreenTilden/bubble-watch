package com.darney.bubblewatch.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Single access point to the clawatch-bridge. Holds one OkHttp/Retrofit stack and
 * the current [BridgeConfig], which it keeps in sync with [SettingsStore]. URLs are
 * built per-call from the live base, so changing the address/token at runtime works
 * without rebuilding anything.
 */
class BridgeRepository private constructor(context: Context) {

    val settings = SettingsStore(context.applicationContext)
    val configFlow: Flow<BridgeConfig> = settings.configFlow

    @Volatile
    private var config: BridgeConfig = BridgeConfig(SettingsStore.DEFAULT_BASE_URL, "")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val api: BridgeApi

    init {
        scope.launch { configFlow.collect { config = it } }

        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor { config.token })
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()

        api = Retrofit.Builder()
            // Placeholder base; every call passes an absolute @Url.
            .baseUrl("http://localhost/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BridgeApi::class.java)
    }

    private fun base(): String = config.base

    suspend fun listThreads(): List<ThreadDto> =
        api.threads("${base()}/api/threads").threads

    suspend fun tail(index: Int, lines: Int = 40): TailDto =
        api.tail("${base()}/api/threads/$index/tail?lines=$lines&scrollback=false")

    suspend fun send(index: Int, text: String, submit: Boolean): SendResponse =
        api.send("${base()}/api/threads/$index/send", SendRequest(text, submit))

    suspend fun sendKey(index: Int, action: String): SendResponse =
        api.sendKey("${base()}/api/threads/$index/key", KeyRequest(action))

    /** Drive a multi-select menu to submission (bridge decides Tab vs confirm). */
    suspend fun submitMenu(index: Int): SubmitMenuDto =
        api.submitMenu("${base()}/api/threads/$index/submit-menu")

    /** Momentum suggestions. Swallows any failure to [] so the VM never error-handles them. */
    suspend fun suggest(index: Int): List<String> =
        runCatching { api.suggest("${base()}/api/threads/$index/suggest").suggestions }
            .getOrDefault(emptyList())

    /** One-line summary of a working thread. Swallows any failure to "" (plain status). */
    suspend fun summary(index: Int): String =
        runCatching { api.summary("${base()}/api/threads/$index/summary").summary }
            .getOrDefault("")

    /** Menu-aware "what you're being asked to decide" line for a paused thread.
     *  Swallows any failure to "" so the detail screen just shows the options. */
    suspend fun promptSummary(index: Int): String =
        runCatching { api.promptSummary("${base()}/api/threads/$index/prompt-summary").summary }
            .getOrDefault("")

    companion object {
        @Volatile
        private var instance: BridgeRepository? = null

        fun get(context: Context): BridgeRepository =
            instance ?: synchronized(this) {
                instance ?: BridgeRepository(context).also { instance = it }
            }
    }
}
