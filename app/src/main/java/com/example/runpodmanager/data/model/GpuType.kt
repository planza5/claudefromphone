package com.example.runpodmanager.data.model

data class GpuOption(
    val id: String,
    val name: String,
    val costPerHour: Double
)

data class CpuOption(
    val id: String,
    val name: String,
    val costPerHour: Double
)

data class PodTemplate(
    val id: String,
    val name: String,
    val description: String
)

object ComputeTypes {
    val gpuTypes = listOf(
        GpuOption("NVIDIA GeForce RTX 4090", "RTX 4090", 0.44),
        GpuOption("NVIDIA GeForce RTX 4080", "RTX 4080", 0.36),
        GpuOption("NVIDIA GeForce RTX 3090", "RTX 3090", 0.28),
        GpuOption("NVIDIA GeForce RTX 3080", "RTX 3080", 0.23),
        GpuOption("NVIDIA GeForce RTX 3070", "RTX 3070", 0.18),
        GpuOption("NVIDIA RTX A6000", "RTX A6000", 0.53),
        GpuOption("NVIDIA RTX A5000", "RTX A5000", 0.36),
        GpuOption("NVIDIA RTX A4000", "RTX A4000", 0.26),
        GpuOption("NVIDIA A100 80GB PCIe", "A100 80GB PCIe", 1.89),
        GpuOption("NVIDIA A100-SXM4-80GB", "A100 SXM4 80GB", 1.89),
        GpuOption("NVIDIA A40", "A40", 0.39),
        GpuOption("NVIDIA L40", "L40", 0.69),
        GpuOption("NVIDIA H100 PCIe", "H100 PCIe", 2.49),
        GpuOption("NVIDIA H100 80GB HBM3", "H100 80GB HBM3", 3.89)
    )

    val cpuTypes = listOf(
        CpuOption("cpu3c", "3 vCPU, 12GB RAM", 0.07),
        CpuOption("cpu5c", "5 vCPU, 20GB RAM", 0.12),
        CpuOption("cpu8c", "8 vCPU, 32GB RAM", 0.19),
        CpuOption("cpu16c", "16 vCPU, 62GB RAM", 0.38),
        CpuOption("cpu24c", "24 vCPU, 93GB RAM", 0.57),
        CpuOption("cpu32c", "32 vCPU, 124GB RAM", 0.76)
    )

    val commonImages = listOf(
        "runpod/pytorch:2.1.0-py3.10-cuda11.8.0-devel-ubuntu22.04",
        "runpod/pytorch:2.0.1-py3.10-cuda11.8.0-devel-ubuntu22.04",
        "runpod/stable-diffusion:web-automatic-8.0.3",
        "runpod/tensorflow:2.13.0-py3.10-cuda11.8.0-devel-ubuntu22.04"
    )

    // Imagenes ligeras para CPU (inicio rapido)
    val cpuImages = listOf(
        "runpod/pytorch:2.1.0-py3.10-cuda11.8.0-devel-ubuntu22.04",
        "runpod/base:0.6.2-cuda12.2.0",
        "runpod/base:0.4.4-cuda11.8.0"
    )
}
