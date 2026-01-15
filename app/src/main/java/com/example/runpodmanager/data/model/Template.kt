package com.example.runpodmanager.data.model

import com.google.gson.annotations.SerializedName

data class Template(
    @SerializedName("id")
    val id: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("imageName")
    val imageName: String? = null,

    @SerializedName("containerDiskInGb")
    val containerDiskInGb: Int? = null,

    @SerializedName("volumeInGb")
    val volumeInGb: Int? = null,

    @SerializedName("ports")
    val ports: String? = null,

    @SerializedName("isPublic")
    val isPublic: Boolean? = null,

    @SerializedName("isServerless")
    val isServerless: Boolean? = null
)
