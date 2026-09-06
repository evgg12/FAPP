package com.fapp.api;

import com.fapp.statement.StatementImportRepository;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reading back the outcome of an import, which is what the {@code Location} of a
 * successful upload points at.
 */
@RestController
@RequestMapping("/api/imports")
class StatementImportController {

    private final StatementImportRepository statementImports;

    StatementImportController(StatementImportRepository statementImports) {
        this.statementImports = statementImports;
    }

    @GetMapping("/{importId}")
    StatementImportResponse get(@PathVariable UUID importId) {
        return statementImports.findByIdWithAccount(importId)
                .map(StatementImportResponse::of)
                .orElseThrow(() -> new NotFoundException(
                        "IMPORT_NOT_FOUND", "no statement import with id " + importId));
    }
}
