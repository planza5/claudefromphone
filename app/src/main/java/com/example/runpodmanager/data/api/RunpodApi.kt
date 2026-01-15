package com.example.runpodmanager.data.api

import com.example.runpodmanager.data.model.CreatePodRequest
import com.example.runpodmanager.data.model.NetworkVolume
import com.example.runpodmanager.data.model.Pod
import com.example.runpodmanager.data.model.Template
import com.example.runpodmanager.data.model.UpdatePodRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface RunpodApi {

    companion object {
        const val BASE_URL = "https://rest.runpod.io/v1/"
    }

    @GET("pods")
    suspend fun getPods(): Response<List<Pod>>

    @GET("pods/{podId}")
    suspend fun getPod(@Path("podId") podId: String): Response<Pod>

    @POST("pods")
    suspend fun createPod(@Body request: CreatePodRequest): Response<Pod>

    @PATCH("pods/{podId}")
    suspend fun updatePod(
        @Path("podId") podId: String,
        @Body request: UpdatePodRequest
    ): Response<Pod>

    @DELETE("pods/{podId}")
    suspend fun deletePod(@Path("podId") podId: String): Response<Unit>

    @POST("pods/{podId}/start")
    suspend fun startPod(@Path("podId") podId: String): Response<Pod>

    @POST("pods/{podId}/stop")
    suspend fun stopPod(@Path("podId") podId: String): Response<Pod>

    @POST("pods/{podId}/restart")
    suspend fun restartPod(@Path("podId") podId: String): Response<Pod>

    @GET("networkvolumes")
    suspend fun getNetworkVolumes(): Response<List<NetworkVolume>>

    @GET("templates")
    suspend fun getTemplates(): Response<List<Template>>
}
