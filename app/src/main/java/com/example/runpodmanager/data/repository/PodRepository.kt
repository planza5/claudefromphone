package com.example.runpodmanager.data.repository

import com.example.runpodmanager.data.api.RunpodApi
import com.example.runpodmanager.data.model.CreatePodRequest
import com.example.runpodmanager.data.model.NetworkVolume
import com.example.runpodmanager.data.model.Pod
import com.example.runpodmanager.data.model.Template
import com.example.runpodmanager.data.model.UpdatePodRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    suspend fun getPods(): ApiResult<List<Pod>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getPods()
            if (response.isSuccessful) {
                ApiResult.Success(response.body() ?: emptyList())
            } else {
                ApiResult.Error(
                    message = response.errorBody()?.string() ?: "Error fetching pods",
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            ApiResult.Error(message = e.message ?: "Unknown error")
        }
    }

    suspend fun getPod(podId: String): ApiResult<Pod> = withContext(Dispatchers.IO) {
        try {
            val response = api.getPod(podId)
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                ApiResult.Error(
                    message = response.errorBody()?.string() ?: "Error fetching pod",
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            ApiResult.Error(message = e.message ?: "Unknown error")
        }
    }

    suspend fun createPod(request: CreatePodRequest): ApiResult<Pod> = withContext(Dispatchers.IO) {
        try {
            val response = api.createPod(request)
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                ApiResult.Error(
                    message = response.errorBody()?.string() ?: "Error creating pod",
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            ApiResult.Error(message = e.message ?: "Unknown error")
        }
    }

    suspend fun updatePod(podId: String, request: UpdatePodRequest): ApiResult<Pod> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.updatePod(podId, request)
                if (response.isSuccessful && response.body() != null) {
                    ApiResult.Success(response.body()!!)
                } else {
                    ApiResult.Error(
                        message = response.errorBody()?.string() ?: "Error updating pod",
                        code = response.code()
                    )
                }
            } catch (e: Exception) {
                ApiResult.Error(message = e.message ?: "Unknown error")
            }
        }

    suspend fun deletePod(podId: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = api.deletePod(podId)
            if (response.isSuccessful) {
                ApiResult.Success(Unit)
            } else {
                ApiResult.Error(
                    message = response.errorBody()?.string() ?: "Error deleting pod",
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            ApiResult.Error(message = e.message ?: "Unknown error")
        }
    }

    suspend fun startPod(podId: String): ApiResult<Pod> = withContext(Dispatchers.IO) {
        try {
            val response = api.startPod(podId)
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                ApiResult.Error(
                    message = response.errorBody()?.string() ?: "Error starting pod",
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            ApiResult.Error(message = e.message ?: "Unknown error")
        }
    }

    suspend fun stopPod(podId: String): ApiResult<Pod> = withContext(Dispatchers.IO) {
        try {
            val response = api.stopPod(podId)
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                ApiResult.Error(
                    message = response.errorBody()?.string() ?: "Error stopping pod",
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            ApiResult.Error(message = e.message ?: "Unknown error")
        }
    }

    suspend fun restartPod(podId: String): ApiResult<Pod> = withContext(Dispatchers.IO) {
        try {
            val response = api.restartPod(podId)
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                ApiResult.Error(
                    message = response.errorBody()?.string() ?: "Error restarting pod",
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            ApiResult.Error(message = e.message ?: "Unknown error")
        }
    }

    suspend fun getNetworkVolumes(): ApiResult<List<NetworkVolume>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getNetworkVolumes()
            if (response.isSuccessful) {
                ApiResult.Success(response.body() ?: emptyList())
            } else {
                ApiResult.Error(
                    message = response.errorBody()?.string() ?: "Error fetching network volumes",
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            ApiResult.Error(message = e.message ?: "Unknown error")
        }
    }

    suspend fun getTemplates(): ApiResult<List<Template>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getTemplates()
            if (response.isSuccessful) {
                ApiResult.Success(response.body() ?: emptyList())
            } else {
                ApiResult.Error(
                    message = response.errorBody()?.string() ?: "Error fetching templates",
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            ApiResult.Error(message = e.message ?: "Unknown error")
        }
    }
}
