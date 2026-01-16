package com.example.runpodmanager.data.model

import com.google.gson.annotations.SerializedName

data class CreatePodRequest(
    @SerializedName("name")
    val name: String,

    @SerializedName("imageName")
    val imageName: String? = null,

    @SerializedName("templateId")
    val templateId: String? = null,

    @SerializedName("gpuTypeIds")
    val gpuTypeIds: List<String>,

    @SerializedName("gpuCount")
    val gpuCount: Int = 1,

    @SerializedName("containerDiskInGb")
    val containerDiskInGb: Int = 20,

    @SerializedName("volumeInGb")
    val volumeInGb: Int = 0,

    @SerializedName("volumeMountPath")
    val volumeMountPath: String = "/workspace",

    @SerializedName("ports")
    val ports: List<String>? = null,

    @SerializedName("env")
    val env: Map<String, String>? = null,

    @SerializedName("cloudType")
    val cloudType: String = "SECURE",

    @SerializedName("computeType")
    val computeType: String = "GPU",

    @SerializedName("cpuFlavorIds")
    val cpuFlavorIds: List<String>? = null,

    @SerializedName("vcpuCount")
    val vcpuCount: Int? = null,

    @SerializedName("memorySizeInGb")
    val memorySizeInGb: Int? = null,

    @SerializedName("allowedCudaVersions")
    val allowedCudaVersions: List<String>? = null,

    @SerializedName("networkVolumeId")
    val networkVolumeId: String? = null,

    @SerializedName("dockerStartCmd")
    val dockerStartCmd: List<String>? = null
)
