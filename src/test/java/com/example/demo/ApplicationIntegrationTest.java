package com.example.demo;

import com.example.demo.impl.ApplicationImp;
import com.example.demo.model.Application;
import com.example.demo.model.ApplicationStatusHistory;
import com.example.demo.repository.ApplicationRepository;
import com.example.demo.repository.ApplicationStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Testcontainers
@Import(ApplicationImp.class)
class ApplicationIntegrationTest {
    LocalDateTime now = LocalDateTime.now();

    LocalDateTime dueDate = now.plusDays(2)
            .withNano((now.getNano() / 1000) * 1000);

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("test_db")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name",
                postgres::getDriverClassName);
    }

    @Autowired
    private ApplicationImp applicationService;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private ApplicationStatusHistoryRepository historyRepository;

    @Autowired
    private TestEntityManager entityManager;

    @BeforeEach
    void setUp() {
        historyRepository.deleteAll();
        applicationRepository.deleteAll();
        entityManager.flush();
    }

    // 1. Проверка создания заявки и сохранения в PostgreSQL

    @Test
    void createApplicationSavesApplicationAndInitialHistory() {
        Application application = createApplication(
                "Не работает авторизация",
                "Не получается войти в систему",
                "Техническая поддержка",
                Application.ApplicationPriority.HIGH
        );

        Application saved =
                applicationService.createApplication(application);

        entityManager.flush();
        entityManager.clear();

        Application found = applicationRepository
                .findById(saved.getId())
                .orElseThrow();

        assertNotNull(found.getId());
        assertEquals("Не работает авторизация", found.getTitle());
        assertEquals(
                Application.ApplicationStatus.NEW,
                found.getStatus()
        );
        assertEquals(
                Application.ApplicationPriority.HIGH,
                found.getPriority()
        );
        assertNotNull(found.getCreatedAt());
        assertNull(found.getClosedAt());

        List<ApplicationStatusHistory> history =
                historyRepository
                        .findByApplicationIdOrderByChangedAtAsc(found.getId());

        assertEquals(1, history.size());
        assertNull(history.get(0).getOldStatus());
        assertEquals(
                Application.ApplicationStatus.NEW,
                history.get(0).getNewStatus()
        );
    }

    // 2. Проверка получения заявки по ID

    @Test
    void getApplicationByIdReturnsSavedApplication() {
        Application application = applicationService.createApplication(
                createApplication(
                        "Ошибка сервера",
                        "Сервер возвращает ошибку 500",
                        "Backend",
                        Application.ApplicationPriority.HIGH
                )
        );

        entityManager.flush();
        entityManager.clear();

        Application found =
                applicationService.getApplicationById(application.getId());

        assertEquals(application.getId(), found.getId());
        assertEquals("Ошибка сервера", found.getTitle());
        assertEquals("Backend", found.getCategory());
        assertEquals(
                Application.ApplicationStatus.NEW,
                found.getStatus()
        );
    }

    // 3. Проверка обновления заявки

    @Test
    void updateApplicationChangesDescriptionPriorityAndDueDate() {
        Application application = applicationService.createApplication(
                createApplication(
                        "Проблема с сайтом",
                        "Старая информация",
                        "Frontend",
                        Application.ApplicationPriority.LOW
                )
        );

        LocalDateTime dueDate = LocalDateTime.now()
                .plusDays(2)
                .truncatedTo(ChronoUnit.MICROS);

        Application update = new Application();
        update.setDescription("Обновлённое описание");
        update.setPriority(Application.ApplicationPriority.HIGH);
        update.setDueDate(dueDate);

        applicationService.updateApplication(application.getId(), update);

        entityManager.flush();
        entityManager.clear();

        Application updated = applicationRepository
                .findById(application.getId())
                .orElseThrow();

        assertEquals("Обновлённое описание", updated.getDescription());
        assertEquals(
                Application.ApplicationPriority.HIGH,
                updated.getPriority()
        );
        assertEquals(dueDate, updated.getDueDate());
        assertEquals(
                Application.ApplicationStatus.NEW,
                updated.getStatus()
        );
        System.out.println("Description: " + updated.getDescription());
        System.out.println("Priority: " + updated.getPriority());
        System.out.println("DueDate expected: " + dueDate);
        System.out.println("DueDate actual: " + updated.getDueDate());
        System.out.println("Status: " + updated.getStatus());
    }

    // 4. Проверка изменения статуса и сохранения истории

    @Test
    void changeStatusUpdatesApplicationAndSavesHistory() {
        Application application = applicationService.createApplication(
                createApplication(
                        "Проверка авторизации",
                        "Нужно проверить вход",
                        "Backend",
                        Application.ApplicationPriority.MEDIUM
                )
        );

        applicationService.changeStatus(
                application.getId(),
                Application.ApplicationStatus.IN_PROGRESS
        );

        entityManager.flush();
        entityManager.clear();

        Application updated = applicationRepository
                .findById(application.getId())
                .orElseThrow();

        assertEquals(
                Application.ApplicationStatus.IN_PROGRESS,
                updated.getStatus()
        );
        assertNull(updated.getClosedAt());

        List<ApplicationStatusHistory> history =
                historyRepository
                        .findByApplicationIdOrderByChangedAtAsc(
                                application.getId()
                        );

        assertEquals(2, history.size());

        assertNull(history.get(0).getOldStatus());
        assertEquals(
                Application.ApplicationStatus.NEW,
                history.get(0).getNewStatus()
        );

        assertEquals(
                Application.ApplicationStatus.NEW,
                history.get(1).getOldStatus()
        );
        assertEquals(
                Application.ApplicationStatus.IN_PROGRESS,
                history.get(1).getNewStatus()
        );
        assertNotNull(history.get(1).getChangedAt());
    }

    // 5. Проверка поиска и фильтрации заявок

    @Test
    void searchApplicationsFiltersByStatusPriorityAndCategory() {
        Application first = applicationService.createApplication(
                createApplication(
                        "Ошибка входа",
                        "Не работает авторизация",
                        "Техническая поддержка",
                        Application.ApplicationPriority.HIGH
                )
        );

        applicationService.createApplication(
                createApplication(
                        "Ошибка интерфейса",
                        "Не отображается кнопка",
                        "Техническая поддержка",
                        Application.ApplicationPriority.MEDIUM
                )
        );

        applicationService.createApplication(
                createApplication(
                        "Ошибка оплаты",
                        "Не проходит платёж",
                        "Финансы",
                        Application.ApplicationPriority.HIGH
                )
        );

        entityManager.flush();
        entityManager.clear();

        var result = applicationService.searchApplications(
                Application.ApplicationStatus.NEW,
                Application.ApplicationPriority.HIGH,
                "ПОДДЕРЖКА",
                0,
                10
        );

        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
        assertEquals(first.getId(), result.getContent().get(0).getId());
    }

    // 6. Проверка удаления заявки и связанных записей истории

    @Test
    void deleteApplicationRemovesApplicationAndItsHistory() {
        Application application = applicationService.createApplication(
                createApplication(
                        "Заявка на удаление",
                        "Проверка удаления",
                        "Общее",
                        Application.ApplicationPriority.LOW
                )
        );

        Long applicationId = application.getId();

        assertEquals(
                1,
                historyRepository
                        .findByApplicationIdOrderByChangedAtAsc(applicationId)
                        .size()
        );

        applicationService.deleteApplication(applicationId);

        entityManager.flush();
        entityManager.clear();

        assertFalse(applicationRepository.existsById(applicationId));

        assertTrue(
                historyRepository
                        .findByApplicationIdOrderByChangedAtAsc(applicationId)
                        .isEmpty()
        );
    }

    private Application createApplication(
            String title,
            String description,
            String category,
            Application.ApplicationPriority priority
    ) {
        Application application = new Application();

        application.setTitle(title);
        application.setDescription(description);
        application.setCategory(category);
        application.setPriority(priority);

        return application;
    }
}
