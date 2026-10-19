package com.shareverance.dto;
import java.time.LocalDate;
public record ExpenseFilter(LocalDate fromDate, LocalDate toDate, Long categoryId, String state) {}
