
package com.example.demo.impl;

import com.example.demo.exception.ApplicationNotFoundException;
import com.example.demo.model.Application;
import com.example.demo.model.ApplicationStatusHistory;
import com.example.demo.repository.ApplicationRepository;
import com.example.demo.repository.ApplicationStatusHistoryRepository;
import com.example.demo.service.ApplicationService;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@Service
@Transactional
public class ApplicationImp implements ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final ApplicationStatusHistoryRepository historyRepository;

    public ApplicationImp(
            ApplicationRepository applicationRepository,
            ApplicationStatusHistoryRepository historyRepository
    ) {
        this.applicationRepository = applicationRepository;
        this.historyRepository = historyRepository;
    }

    @Override
    public Application createApplication(Application application) {
        application.setStatus(Application.ApplicationStatus.NEW);

        if (application.getPriority() == null) {
            application.setPriority(
                    Application.ApplicationPriority.MEDIUM
            );
        }

        application.setCreatedAt(LocalDateTime.now());
        application.setClosedAt(null);

        Application saved = applicationRepository.save(application);

        // Фиксируем первоначальный статус заявки.
        saveStatusHistory(
                saved,
                null,
                Application.ApplicationStatus.NEW
        );

        return saved;
    }

    @Override
    public Application updateApplication(
            Long id,
            Application application
    ) {
        Application existing = getApplicationById(id);

        ensureEditable(existing);

        existing.setDescription(application.getDescription());

        if (application.getPriority() != null) {
            existing.setPriority(application.getPriority());
        }

        existing.setDueDate(application.getDueDate());

        return applicationRepository.save(existing);
    }

    @Override
    public Application changeStatus(
            Long id,
            Application.ApplicationStatus newStatus
    ) {
        if (newStatus == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Status must not be null"
            );
        }

        Application application = getApplicationById(id);
        Application.ApplicationStatus oldStatus =
                application.getStatus();

        if (!isAllowedTransition(oldStatus, newStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Status transition from " + oldStatus
                            + " to " + newStatus + " is not allowed"
            );
        }

        application.setStatus(newStatus);

        if (newStatus == Application.ApplicationStatus.RESOLVED
                || newStatus == Application.ApplicationStatus.CANCELLED) {
            application.setClosedAt(LocalDateTime.now());
        }

        Application saved = applicationRepository.save(application);

        saveStatusHistory(saved, oldStatus, newStatus);

        return saved;
    }

    private boolean isAllowedTransition(
            Application.ApplicationStatus oldStatus,
            Application.ApplicationStatus newStatus
    ) {
        return switch (oldStatus) {
            case NEW ->
                    newStatus == Application.ApplicationStatus.IN_PROGRESS
                            || newStatus == Application.ApplicationStatus.CANCELLED;

            case IN_PROGRESS ->
                    newStatus == Application.ApplicationStatus.RESOLVED
                            || newStatus == Application.ApplicationStatus.CANCELLED;

            case RESOLVED, CANCELLED -> false;
        };
    }

    private void saveStatusHistory(
            Application application,
            Application.ApplicationStatus oldStatus,
            Application.ApplicationStatus newStatus
    ) {
        ApplicationStatusHistory history =
                new ApplicationStatusHistory();

        history.setApplication(application);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setChangedAt(LocalDateTime.now());

        historyRepository.save(history);
    }

    private void ensureEditable(Application application) {
        Application.ApplicationStatus status = application.getStatus();

        if (status == Application.ApplicationStatus.RESOLVED
                || status == Application.ApplicationStatus.CANCELLED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Closed or cancelled applications cannot be edited"
            );
        }
    }

    @Override
    public void deleteApplication(Long id) {
        Application application = getApplicationById(id);

        historyRepository.deleteByApplicationId(id);
        applicationRepository.delete(application);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Application> getApplication() {
        return applicationRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Application getApplicationById(Long id) {
        return applicationRepository.findById(id)
                .orElseThrow(() ->
                        new ApplicationNotFoundException(id)
                );
    }

    @Transactional(readOnly = true)
    @Override
    public Page<Application> searchApplications(
            Application.ApplicationStatus status,
            Application.ApplicationPriority priority,
            String category,
            int page,
            int size
    ) {
        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be zero or greater"
            );
        }

        if (size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page size must be between 1 and 100"
            );
        }

        Specification<Application> specification =
                (root, query, cb) -> cb.conjunction();

        if (status != null) {
            specification = specification.and(
                    (root, query, cb) ->
                            cb.equal(root.get("status"), status)
            );
        }

        if (priority != null) {
            specification = specification.and(
                    (root, query, cb) ->
                            cb.equal(root.get("priority"), priority)
            );
        }

        if (category != null && !category.isBlank()) {
            String pattern = "%"
                    + category.trim().toLowerCase()
                    + "%";

            specification = specification.and(
                    (root, query, cb) ->
                            cb.like(
                                    cb.lower(root.get("category")),
                                    pattern
                            )
            );
        }

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        return applicationRepository.findAll(
                specification,
                pageable
        );
    }

}
