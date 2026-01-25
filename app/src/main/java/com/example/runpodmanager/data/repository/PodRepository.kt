package com.example.runpodmanager.data.repository

import com.example.runpodmanager.data.api.RunpodApi
import com.example.runpodmanager.data.model.CreatePodRequest
import com.example.runpodmanager.data.model.NetworkVolume
import com.example.runpodmanager.data.model.Pod
import com.example.runpodmanager.data.model.Template
import com.example.runpodmanager.data.model.UpdatePodRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String, val code: Int? = null) : ApiResult<Nothing>()
}

@Singleton
class PodRepository @Inject constructor(
    private val api: RunpodApi
) {
    suspend fun getPods(): ApiResult<List<Pod>> =
        safeApiCall("Error fetching pods") { api.getPods() }
            .mapSuccess { it ?: emptyList() }

    suspend fun getPod(podId: String): ApiResult<Pod> =
        safeApiCall("Error fetching pod") { api.getPod(podId) }

    suspend fun createPod(request: CreatePodRequest): ApiResult<Pod> =
        safeApiCall("Error creating pod") { api.createPod(request) }

    suspend fun updatePod(podId: String, request: UpdatePodRequest): ApiResult<Pod> =
        safeApiCall("Error updating pod") { api.updatePod(podId, request) }

    suspend fun deletePod(podId: String): ApiResult<Unit> =
        safeApiCall("Error deleting pod") { api.deletePod(podId) }
            .mapSuccess { }

    suspend fun startPod(podId: String): ApiResult<Pod> =
        safeApiCall("Error starting pod") { api.startPod(podId) }

    suspend fun stopPod(podId: String): ApiResult<Pod> =
        safeApiCall("Error stopping pod") { api.stopPod(podId) }

    suspend fun getNetworkVolumes(): ApiResult<List<NetworkVolume>> =
        safeApiCall("Error fetching network volumes") { api.getNetworkVolumes() }
            .mapSuccess { it ?: emptyList() }

    suspend fun getTemplates(): ApiResult<List<Template>> =
        safeApiCall("Error fetching templates") { api.getTemplates() }
            .mapSuccess { it ?: emptyList() }

    /** Helper para llamadas API seguras con manejo de errores unificado */
    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> safeApiCall(
        errorMessage: String,
        call: suspend () -> Response<T>
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        try {
            val response = call()
            if (response.isSuccessful) {
                // Para respuestas exitosas, el body puede ser null (ej: DELETE retorna 204 No Content)
                val body = response.body() ?: Unit as T
                ApiResult.Success(body)
            } else {
                ApiResult.Error(
                    message = response.errorBody()?.string() ?: errorMessage,
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            ApiResult.Error(message = e.message ?: "Unknown error")
        }
    }

    /** Extension para transformar el resultado exitoso */
    private inline fun <T, R> ApiResult<T>.mapSuccess(transform: (T) -> R): ApiResult<R> {
        return when (this) {
            is ApiResult.Success -> ApiResult.Success(transform(data))
            is ApiResult.Error -> this
        }
    }
}
