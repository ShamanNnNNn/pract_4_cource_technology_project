package com.example.demo.service

import com.example.demo.dto.ReportSummary
import com.example.demo.model.Application
import com.example.demo.repository.ApplicationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime

@Service
class ReportStatisticsService(
    private val applicationRepository: ApplicationRepository
) {

    @Transactional(readOnly = true)
    fun getSummary(
        from: LocalDateTime,
        to: LocalDateTime
    ): ReportSummary {

        require(from.isBefore(to)) {
            "Parameter 'from' must be earlier than 'to'"
        }

        val applications =
            applicationRepository
                .findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, to)

        val now = LocalDateTime.now()

        val statusDistribution = applications
            .mapNotNull { it.status }
            .groupingBy { it.name }
            .eachCount()
            .mapValues { it.value.toLong() }

        val priorityDistribution = applications
            .mapNotNull { it.priority }
            .groupingBy { it.name }
            .eachCount()
            .mapValues { it.value.toLong() }

        val openOverdue = applications.count { application ->
            application.dueDate?.isBefore(now) == true &&
                    (application.status == Application.ApplicationStatus.NEW ||
                            application.status == Application.ApplicationStatus.IN_PROGRESS)
        }.toLong()

        val resolvedApplications = applications.filter {
            it.status == Application.ApplicationStatus.RESOLVED &&
                    it.closedAt != null
        }

        val averageResolutionTime = resolvedApplications
            .map { application ->
                Duration.between(
                    application.createdAt,
                    application.closedAt
                ).toSeconds() / 3600.0
            }
            .takeIf { it.isNotEmpty() }
            ?.average()

        return ReportSummary(
            totalCreated = applications.size.toLong(),
            statusDistribution = statusDistribution,
            priorityDistribution = priorityDistribution,
            openOverdue = openOverdue,
            averageResolutionTimeHours = averageResolutionTime
        )
    }
}