package com.fapp.api;

import com.fapp.security.CurrentUser;
import com.fapp.statement.StatementImport;
import com.fapp.statement.StatementImportRepository;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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

    /**
     * Removes one imported statement and every transaction it produced — the foreign key
     * from {@code transactions} cascades, so the month goes as a unit.
     *
     * <p>Deliberately makes the same file importable again: duplicate detection is by
     * content hash per account and by fingerprint per transaction, and deleting the
     * import removes both records, so re-uploading the month behaves exactly like a
     * first import rather than being rejected as already held.
     */
    @DeleteMapping("/{importId}")
    ResponseEntity<Void> delete(@PathVariable UUID importId) {
        statementImports.delete(owned(importId));
        return ResponseEntity.noContent().build();
    }

    private StatementImport owned(UUID importId) {
        StatementImport statementImport = statementImports.findByIdWithAccount(importId)
                .orElseThrow(() -> new NotFoundException(
                        "IMPORT_NOT_FOUND", "no statement import with id " + importId));
        if (!statementImport.userId().equals(currentUser.requireId())) {
            throw new NotFoundException("IMPORT_NOT_FOUND", "no statement import with id " + importId);
        }
        return statementImport;
    }
}
