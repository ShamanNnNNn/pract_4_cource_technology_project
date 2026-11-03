package com.example.demo.controller;

import com.example.demo.dto.ReportSummary;
import com.example.demo.service.ReportStatisticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportStatisticsService reportStatisticsService;

    public ReportController(
            ReportStatisticsService reportStatisticsService
    ) {
        this.reportStatisticsService = reportStatisticsService;
    }

    @GetMapping("/summary")
    public ReportSummary getSummary(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime from,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime to
    ) {
        return reportStatisticsService.getSummary(from, to);
    }
}