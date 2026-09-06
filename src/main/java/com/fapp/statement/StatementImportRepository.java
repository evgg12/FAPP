package com.fapp.statement;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatementImportRepository extends JpaRepository<StatementImport, UUID> {

    /**
     * Whether this exact file has already been imported into this account, matching
     * {@code uq_statement_imports_content} on {@code (account_id, content_hash)}. Asked
     * before parsing, so re-uploading a statement costs nothing.
     */
    boolean existsByAccount_IdAndContentHash(UUID accountId, String contentHash);
}
