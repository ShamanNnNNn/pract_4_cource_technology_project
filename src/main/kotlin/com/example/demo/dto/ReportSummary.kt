package com.example.demo.dto

data class ReportSummary(
    val totalCreated: Long,
    val statusDistribution: Map<String, Long>,
    val priorityDistribution: Map<String, Long>,
    val openOverdue: Long,
    val averageResolutionTimeHours: Double?
)