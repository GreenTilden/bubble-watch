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

    @POST
    suspend fun send(@Url url: String, @Body body: SendRequest): SendResponse

    @POST
    suspend fun sendKey(@Url url: String, @Body body: KeyRequest): SendResponse

    @POST
    suspend fun suggest(@Url url: String): SuggestResponseDto

    @POST
    suspend fun summary(@Url url: String): SummaryResponseDto
}
