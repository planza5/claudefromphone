package com.example.runpodmanager.data.model

import com.google.gson.annotations.SerializedName

data class Pod(
    @SerializedName("id")
    val id: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("desiredStatus")
    val desiredStatus: String? = null,

    @SerializedName("imageName")
    val imageName: String? = null,

    @SerializedName("gpuType")
    val gpuType: String? = null,

    @SerializedName("gpuTypeId")
    val gpuTypeId: String? = null,

    @SerializedName("gpuCount")
    val gpuCount: Int? = null,

    @SerializedName("vcpuCount")
    val vcpuCount: Int? = null,

    @SerializedName("memoryInGb")
    val memoryInGb: Int? = null,

    @SerializedName("containerDiskInGb")
    val containerDiskInGb: Int? = null,

    @SerializedName("volumeInGb")
    val volumeInGb: Int? = null,

    @SerializedName("volumeMountPath")
    val volumeMountPath: String? = null,

    @SerializedName("ports")
    val ports: List<String>? = null,

    @SerializedName("env")
    val env: Map<String, String>? = null,

    @SerializedName("machineId")
    val machineId: String? = null,

    @SerializedName("machine")
    val machine: Machine? = null,

    @SerializedName("runtime")
    val runtime: Runtime? = null,

    @SerializedName("costPerHr")
    val costPerHr: Double? = null,

    @SerializedName("uptimeInSeconds")
    val uptimeInSeconds: Long? = null,

    @SerializedName("portMappings")
    val portMappings: Map<String, Int>? = null,

    @SerializedName("publicIp")
    val publicIp: String? = null
)

data class Machine(
    @SerializedName("gpuDisplayName")
    val gpuDisplayName: String? = null,

    @SerializedName("location")
    val location: String? = null
)

data class Runtime(
    @SerializedName("uptimeInSeconds")
    val uptimeInSeconds: Long? = null,

    @SerializedName("gpus")
    val gpus: List<GpuInfo>? = null,

    @SerializedName("ports")
    val ports: List<PortInfo>? = null
)

data class GpuInfo(
    @SerializedName("id")
    val id: String? = null,

    @SerializedName("gpuUtilPercent")
    val gpuUtilPercent: Double? = null,

    @SerializedName("memoryUtilPercent")
    val memoryUtilPercent: Double? = null
)

data class PortInfo(
    @SerializedName("ip")
    val ip: String? = null,

    @SerializedName("isIpPublic")
    val isIpPublic: Boolean? = null,

    @SerializedName("privatePort")
    val privatePort: Int? = null,

    @SerializedName("publicPort")
    val publicPort: Int? = null,

    @SerializedName("type")
    val type: String? = null
)

data class PodsResponse(
    @SerializedName("pods")
    val pods: List<Pod>? = null
)
