package com.example.demo;

import com.example.demo.exception.ApplicationNotFoundException;
import com.example.demo.impl.ApplicationImp;
import com.example.demo.model.Application;
import com.example.demo.model.ApplicationStatusHistory;
import com.example.demo.repository.ApplicationRepository;
import com.example.demo.repository.ApplicationStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationImpTest {
    LocalDateTime now = LocalDateTime.now();

    LocalDateTime dueDate = now.plusDays(2)
            .withNano((now.getNano() / 1000) * 1000);

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private ApplicationStatusHistoryRepository historyRepository;

    @InjectMocks
    private ApplicationImp applicationService;

    private Application application;

    @BeforeEach
    void setUp() {
        application = new Application();
        application.setId(7L);
        application.setTitle("Не работает авторизация");
        application.setDescription("Ошибка входа");
        application.setCategory("Техническая поддержка");
        application.setPriority(Application.ApplicationPriority.HIGH);
        application.setStatus(Application.ApplicationStatus.NEW);
        application.setCreatedAt(LocalDateTime.now().minusHours(2));
    }

    @Test
    void createApplicationSetsInitialStatusAndDefaultPriority() {
        Application newApplication = new Application();
        newApplication.setTitle("Новая заявка");
        newApplication.setPriority(null);

        when(applicationRepository.save(any(Application.class)))
                .thenAnswer(invocation -> {
                    Application saved = invocation.getArgument(0);
                    saved.setId(1L);
                    return saved;
                });

        Application result =
                applicationService.createApplication(newApplication);

        assertEquals(Application.ApplicationStatus.NEW, result.getStatus());
        assertEquals(Application.ApplicationPriority.MEDIUM, result.getPriority());
        assertNotNull(result.getCreatedAt());
        assertNull(result.getClosedAt());

        verify(historyRepository).save(any(ApplicationStatusHistory.class));
    }

    @Test
    void createApplicationWritesInitialHistoryEntry() {
        when(applicationRepository.save(any(Application.class)))
                .thenReturn(application);

        applicationService.createApplication(application);

        ArgumentCaptor<ApplicationStatusHistory> captor =
                ArgumentCaptor.forClass(ApplicationStatusHistory.class);

        verify(historyRepository).save(captor.capture());

        assertNull(captor.getValue().getOldStatus());
        assertEquals(
                Application.ApplicationStatus.NEW,
                captor.getValue().getNewStatus()
        );
        assertSame(application, captor.getValue().getApplication());
    }

    @Test
    void changeStatusAllowsNewToInProgressAndStoresHistory() {
        when(applicationRepository.findById(7L))
                .thenReturn(Optional.of(application));

        when(applicationRepository.save(any(Application.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Application result = applicationService.changeStatus(
                7L,
                Application.ApplicationStatus.IN_PROGRESS
        );

        assertEquals(
                Application.ApplicationStatus.IN_PROGRESS,
                result.getStatus()
        );
        assertNull(result.getClosedAt());

        verify(historyRepository).save(any(ApplicationStatusHistory.class));
    }

    @Test
    void changeStatusAllowsInProgressToResolvedAndSetsClosedAt() {
        application.setStatus(Application.ApplicationStatus.IN_PROGRESS);

        when(applicationRepository.findById(7L))
                .thenReturn(Optional.of(application));

        when(applicationRepository.save(any(Application.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Application result = applicationService.changeStatus(
                7L,
                Application.ApplicationStatus.RESOLVED
        );

        assertEquals(
                Application.ApplicationStatus.RESOLVED,
                result.getStatus()
        );
        assertNotNull(result.getClosedAt());
    }

    @Test
    void changeStatusAllowsCancellationFromNew() {
        when(applicationRepository.findById(7L))
                .thenReturn(Optional.of(application));

        when(applicationRepository.save(any(Application.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Application result = applicationService.changeStatus(
                7L,
                Application.ApplicationStatus.CANCELLED
        );

        assertEquals(
                Application.ApplicationStatus.CANCELLED,
                result.getStatus()
        );
        assertNotNull(result.getClosedAt());
    }

    @Test
    void changeStatusRejectsNewDirectlyToResolved() {
        when(applicationRepository.findById(7L))
                .thenReturn(Optional.of(application));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> applicationService.changeStatus(
                        7L,
                        Application.ApplicationStatus.RESOLVED
                )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        verify(applicationRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
    }

    @Test
    void changeStatusRejectsChangingStatusAfterResolved() {
        application.setStatus(Application.ApplicationStatus.RESOLVED);

        when(applicationRepository.findById(7L))
                .thenReturn(Optional.of(application));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> applicationService.changeStatus(
                        7L,
                        Application.ApplicationStatus.IN_PROGRESS
                )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        verify(applicationRepository, never()).save(any());
    }

    @Test
    void changeStatusRejectsNullStatusWithBadRequest() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> applicationService.changeStatus(7L, null)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());

        verify(applicationRepository, never()).findById(any());
    }

    @Test
    void updateApplicationRejectsClosedApplication() {
        application.setStatus(Application.ApplicationStatus.CANCELLED);

        when(applicationRepository.findById(7L))
                .thenReturn(Optional.of(application));

        Application update = new Application();
        update.setDescription("Новое описание");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> applicationService.updateApplication(7L, update)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        verify(applicationRepository, never()).save(any());
    }

    @Test
    void getApplicationByIdThrowsNotFoundForUnknownId() {
        when(applicationRepository.findById(404L))
                .thenReturn(Optional.empty());

        assertThrows(
                ApplicationNotFoundException.class,
                () -> applicationService.getApplicationById(404L)
        );
    }

    @Test
    void searchRejectsNegativePage() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> applicationService.searchApplications(
                        null, null, null, -1, 10
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());

        verifyNoInteractions(applicationRepository);
    }

    @Test
    void searchRejectsPageSizeAboveLimit() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> applicationService.searchApplications(
                        null, null, null, 0, 101
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());

        verifyNoInteractions(applicationRepository);
    }
}
