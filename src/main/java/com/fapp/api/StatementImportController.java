package com.fapp.api;

import com.fapp.security.CurrentUser;
import com.fapp.statement.StatementImport;
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
    private final CurrentUser currentUser;

    StatementImportController(StatementImportRepository statementImports, CurrentUser currentUser) {
        this.statementImports = statementImports;
        this.currentUser = currentUser;
    }

    @GetMapping("/{importId}")
    StatementImportResponse get(@PathVariable UUID importId) {
        StatementImport statementImport = statementImports.findByIdWithAccount(importId)
                .orElseThrow(() -> new NotFoundException(
                        "IMPORT_NOT_FOUND", "no statement import with id " + importId));
        // Somebody else's import is reported as absent rather than forbidden, so the id
        // cannot be used to learn that it exists.
        if (!statementImport.userId().equals(currentUser.requireId())) {
            throw new NotFoundException("IMPORT_NOT_FOUND", "no statement import with id " + importId);
        }
        return StatementImportResponse.of(statementImport);
    }
}
