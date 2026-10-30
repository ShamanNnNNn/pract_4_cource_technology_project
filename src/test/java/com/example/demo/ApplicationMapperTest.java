
package com.example.demo;

import com.example.demo.dto.ApplicationResponse;
import com.example.demo.mapper.ApplicationMapper;
import com.example.demo.model.Application;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ApplicationMapperTest {

    LocalDateTime now = LocalDateTime.now();

    LocalDateTime dueDate = now.plusDays(2)
            .withNano((now.getNano() / 1000) * 1000);

    private final ApplicationMapper mapper = new ApplicationMapper();

    @Test
    void overdueIsTrueWhenDeadlinePassedAndApplicationIsOpen() {
        Application application = applicationWithDeadline(
                LocalDateTime.now().minusMinutes(1)
        );

        application.setStatus(Application.ApplicationStatus.NEW);

        ApplicationResponse response = mapper.toResponse(application);

        assertTrue(response.isOverdue());
    }

    @Test
    void overdueIsFalseWhenDeadlineIsInFuture() {
        Application application = applicationWithDeadline(
                LocalDateTime.now().plusDays(1)
        );

        application.setStatus(Application.ApplicationStatus.IN_PROGRESS);

        ApplicationResponse response = mapper.toResponse(application);

        assertFalse(response.isOverdue());
    }

    @Test
    void overdueIsFalseForResolvedApplicationEvenIfDeadlinePassed() {
        Application application = applicationWithDeadline(
                LocalDateTime.now().minusDays(1)
        );

        application.setStatus(Application.ApplicationStatus.RESOLVED);

        ApplicationResponse response = mapper.toResponse(application);

        assertFalse(response.isOverdue());
    }

    @Test
    void overdueIsFalseWhenDeadlineIsMissing() {
        Application application = applicationWithDeadline(null);

        application.setStatus(Application.ApplicationStatus.NEW);

        ApplicationResponse response = mapper.toResponse(application);

        assertFalse(response.isOverdue());
    }

    private Application applicationWithDeadline(LocalDateTime dueDate) {
        Application application = new Application();

        application.setId(1L);
        application.setTitle("Тестовая заявка");
        application.setCategory("Общее");
        application.setPriority(Application.ApplicationPriority.MEDIUM);
        application.setDueDate(dueDate);

        return application;
    }
}
