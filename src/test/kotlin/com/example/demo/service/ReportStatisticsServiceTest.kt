package com.example.demo.service

import com.example.demo.model.Application
import com.example.demo.repository.ApplicationRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class ReportStatisticsServiceTest {

    @Mock
    lateinit var applicationRepository: ApplicationRepository

    @InjectMocks
    lateinit var reportStatisticsService: ReportStatisticsService

    private val from = LocalDateTime.of(2026, 1, 1, 0, 0)
    private val to = LocalDateTime.of(2027, 1, 1, 0, 0)

    private fun createApplication(
        status: Application.ApplicationStatus,
        priority: Application.ApplicationPriority,
        dueDate: LocalDateTime? = null,
        closedAt: LocalDateTime? = null
    ): Application {
        return Application().apply {
            createdAt = from.plusDays(1)
            this.status = status
            this.priority = priority
            this.dueDate = dueDate
            this.closedAt = closedAt
        }
    }

    @Test
    fun `should calculate summary for applications`() {
        val resolved = createApplication(
            Application.ApplicationStatus.RESOLVED,
            Application.ApplicationPriority.HIGH,
            closedAt = from.plusDays(2)
        )

        val new = createApplication(
            Application.ApplicationStatus.NEW,
            Application.ApplicationPriority.MEDIUM
        )

        val inProgress = createApplication(
            Application.ApplicationStatus.IN_PROGRESS,
            Application.ApplicationPriority.HIGH
        )

        `when`(
            applicationRepository
                .findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, to)
        ).thenReturn(listOf(resolved, new, inProgress))

        val result = reportStatisticsService.getSummary(from, to)

        assertEquals(3L, result.totalCreated)
        assertEquals(1L, result.statusDistribution["RESOLVED"])
        assertEquals(1L, result.statusDistribution["NEW"])
        assertEquals(1L, result.statusDistribution["IN_PROGRESS"])
        assertEquals(2L, result.priorityDistribution["HIGH"])
        assertEquals(1L, result.priorityDistribution["MEDIUM"])
        assertNotNull(result.averageResolutionTimeHours)

        verify(applicationRepository)
            .findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, to)
    }

    @Test
    fun `should return null average when there are no resolved applications`() {
        `when`(
            applicationRepository
                .findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, to)
        ).thenReturn(
            listOf(
                createApplication(
                    Application.ApplicationStatus.NEW,
                    Application.ApplicationPriority.LOW
                )
            )
        )

        val result = reportStatisticsService.getSummary(from, to)

        assertNull(result.averageResolutionTimeHours)
    }

    @Test
    fun `should count overdue open applications`() {
        val overdueNew = createApplication(
            Application.ApplicationStatus.NEW,
            Application.ApplicationPriority.HIGH,
            dueDate = LocalDateTime.now().minusDays(2)
        )

        val overdueInProgress = createApplication(
            Application.ApplicationStatus.IN_PROGRESS,
            Application.ApplicationPriority.MEDIUM,
            dueDate = LocalDateTime.now().minusDays(1)
        )

        val notOverdue = createApplication(
            Application.ApplicationStatus.NEW,
            Application.ApplicationPriority.LOW,
            dueDate = LocalDateTime.now().plusDays(1)
        )

        `when`(
            applicationRepository
                .findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, to)
        ).thenReturn(listOf(overdueNew, overdueInProgress, notOverdue))

        val result = reportStatisticsService.getSummary(from, to)

        assertEquals(2L, result.openOverdue)
    }

    @Test
    fun `should reject invalid date range`() {
        assertThrows(IllegalArgumentException::class.java) {
            reportStatisticsService.getSummary(to, from)
        }
    }
}