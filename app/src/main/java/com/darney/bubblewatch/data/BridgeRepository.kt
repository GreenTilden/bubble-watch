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
import java.net.URLEncoder
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
    private val slowApi: BridgeApi

    init {
        scope.launch { configFlow.collect { config = it } }

        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor { config.token })
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()

        // The digest route runs a Sonnet call with a 30s server-side budget
        // (CLAWATCH_DIGEST_TIMEOUT) — the shared 15s callTimeout would abandon it
        // mid-generation while the bridge keeps paying for the answer. newBuilder()
        // shares the connection pool + dispatcher, so this is a timeout override,
        // not a second HTTP stack.
        val slowClient = client.newBuilder()
            .readTimeout(35, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            // Placeholder base; every call passes an absolute @Url.
            .baseUrl("http://localhost/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        api = retrofit.create(BridgeApi::class.java)
        slowApi = retrofit.newBuilder().client(slowClient).build().create(BridgeApi::class.java)
    }

    private fun base(): String = config.base

    /**
     * `?paneId=…` (or `&paneId=…`) when the caller knows which pane it is looking at.
     *
     * tmux pane INDICES are positional and are renumbered when a pane is killed, so a
     * watch holding index 3 can silently read -- and type into -- a different Claude
     * session after a close on the desktop. The bridge has accepted `paneId` as an
     * assertion since L17 and treats its absence as a no-op; this client never sent
     * one, so it kept the exposure the phone had already closed.
     *
     * ENCODED, not interpolated. Pane ids are `%N`, and `%` starts an escape in a
     * URL: `%35` would arrive decoded as `5` and mismatch every time. URLEncoder
     * also turns a space into `+`, which is correct for a query value.
     *
     * Null-safe by design -- an older build, or a thread whose paneId the bridge did
     * not report, sends nothing and behaves exactly as before.
     */
    private fun paneQ(paneId: String?, existingQuery: Boolean = false): String {
        if (paneId.isNullOrBlank()) return ""
        val sep = if (existingQuery) "&" else "?"
        return sep + "paneId=" + URLEncoder.encode(paneId, "UTF-8")
    }

    suspend fun listThreads(): List<ThreadDto> =
        api.threads("${base()}/api/threads").threads

    suspend fun tail(index: Int, lines: Int = 40, paneId: String? = null): TailDto =
        api.tail("${base()}/api/threads/$index/tail?lines=$lines&scrollback=false"
            + paneQ(paneId, existingQuery = true))

    suspend fun send(index: Int, text: String, submit: Boolean, paneId: String? = null): SendResponse =
        api.send("${base()}/api/threads/$index/send" + paneQ(paneId), SendRequest(text, submit))

    suspend fun sendKey(index: Int, action: String, paneId: String? = null): SendResponse =
        api.sendKey("${base()}/api/threads/$index/key" + paneQ(paneId), KeyRequest(action))

    /** Drive a multi-select menu to submission (bridge decides Tab vs confirm). */
    suspend fun submitMenu(index: Int, paneId: String? = null): SubmitMenuDto =
        api.submitMenu("${base()}/api/threads/$index/submit-menu" + paneQ(paneId))

    /** Ask the bridge to wash a pane. The bridge owns every guard and keystroke. */
    suspend fun startWash(index: Int, paneId: String? = null): WashStartDto =
        api.startWash("${base()}/api/threads/$index/wash" + paneQ(paneId))

    /** No paneId: a wash is addressed by washId, which IS an identity, and the
     *  bridge guards it per-keystroke. A 409 here would break status polling for a
     *  wash that is proceeding correctly -- the same exemption the server's own
     *  identity coverage guard carries. */
    suspend fun washStatus(index: Int, washId: String): WashStatusDto =
        api.washStatus("${base()}/api/threads/$index/wash/$washId")

    /** Momentum suggestions. Swallows any failure to [] so the VM never error-handles them. */
    suspend fun suggest(index: Int, paneId: String? = null): List<String> =
        runCatching { api.suggest("${base()}/api/threads/$index/suggest" + paneQ(paneId)).suggestions }
            .getOrDefault(emptyList())

    /** One-line summary of a working thread. Swallows any failure to "" (plain status). */
    suspend fun summary(index: Int, paneId: String? = null): String =
        runCatching { api.summary("${base()}/api/threads/$index/summary" + paneQ(paneId)).summary }
            .getOrDefault("")

    /** Catch-me-up digest over a deep (history) tail. null = the request FAILED;
     *  an all-empty DigestDto is the bridge answering "nothing to catch up on".
     *  Unlike suggest/summary this does not swallow to a default — collapsing the
     *  two would render a network failure as a reassuring blank. */
    suspend fun digest(index: Int, paneId: String? = null): DigestDto? =
        runCatching { slowApi.digest("${base()}/api/threads/$index/digest" + paneQ(paneId)) }
            .getOrNull()

    /** Menu-aware "what you're being asked to decide" line for a paused thread.
     *  Swallows any failure to "" so the detail screen just shows the options. */
    suspend fun promptSummary(index: Int, paneId: String? = null): String =
        runCatching { api.promptSummary("${base()}/api/threads/$index/prompt-summary" + paneQ(paneId)).summary }
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
