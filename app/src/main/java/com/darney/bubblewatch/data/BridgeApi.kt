package com.darney.bubblewatch.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Retrofit interface for clawatch-bridge. Every call takes an absolute @Url so the
 * base address can change at runtime (from Settings) without rebuilding Retrofit.
 */
interface BridgeApi {
    @GET
    suspend fun threads(@Url url: String): ThreadsDto

    @GET
    suspend fun tail(@Url url: String): TailDto

    /** Session history from Claude's transcript. The tail reads a SCREEN; this
     *  reads what Claude wrote, which is the only source that carries a message
     *  boundary — see HistoryDto. */
    @GET
    suspend fun history(@Url url: String): HistoryDto

    @POST
    suspend fun send(@Url url: String, @Body body: SendRequest): SendResponse

    @POST
    suspend fun sendKey(@Url url: String, @Body body: KeyRequest): SendResponse

    @POST
    suspend fun submitMenu(@Url url: String): SubmitMenuDto

    /**
     * Start a context wash. Takes NO body — a wash has exactly one meaning, and a
     * body would invite the client to specify how to type into a live session.
     * Returns 202 immediately; poll [washStatus] for the stage. The bridge runs it
     * asynchronously precisely so this call cannot outlive OkHttp's 15s callTimeout.
     */
    @POST
    suspend fun startWash(@Url url: String): WashStartDto

    @GET
    suspend fun washStatus(@Url url: String): WashStatusDto

    @POST
    suspend fun suggest(@Url url: String): SuggestResponseDto

    @POST
    suspend fun summary(@Url url: String): SummaryResponseDto

    @POST
    suspend fun promptSummary(@Url url: String): SummaryResponseDto

    /** Catch-me-up digest over a deep (history) tail. Server-side this is a Sonnet
     *  call with a 30s budget — call it through the SLOW client, never the 15s one. */
    @POST
    suspend fun digest(@Url url: String): DigestDto
}
