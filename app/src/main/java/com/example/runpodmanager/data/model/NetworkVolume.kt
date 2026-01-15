package com.example.runpodmanager.data.model

import com.google.gson.annotations.SerializedName

data class NetworkVolume(
    @SerializedName("id")
    val id: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("size")
    val size: Int? = null,

    @SerializedName("dataCenterId")
    val dataCenterId: String? = null,

    @SerializedName("dataCenter")
    val dataCenter: DataCenter? = null
)

data class DataCenter(
    @SerializedName("id")
    val id: String? = null,

    @SerializedName("name")
    val name: String? = null,

    @SerializedName("location")
    val location: String? = null
)
