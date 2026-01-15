package com.example.runpodmanager.data.model

import com.google.gson.annotations.SerializedName

data class UpdatePodRequest(
    @SerializedName("name")
    val name: String? = null,

    @SerializedName("imageName")
    val imageName: String? = null,

    @SerializedName("containerDiskInGb")
    val containerDiskInGb: Int? = null,

    @SerializedName("volumeInGb")
    val volumeInGb: Int? = null,

    @SerializedName("ports")
    val ports: List<String>? = null,

    @SerializedName("env")
    val env: Map<String, String>? = null
)
