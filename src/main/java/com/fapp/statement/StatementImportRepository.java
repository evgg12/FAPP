package com.fapp.statement;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StatementImportRepository extends JpaRepository<StatementImport, UUID> {

    /**
     * Loads an import together with the account it belongs to, so a caller can be told
     * which account it applied to without the lazy association failing outside a
     * transaction.
     */
    @Query("select i from StatementImport i join fetch i.account where i.id = :id")
    Optional<StatementImport> findByIdWithAccount(@Param("id") UUID id);

    /**
     * Every statement imported into this account, earliest period first, so a caller can
     * see which months are loaded and remove one.
     */
    @Query("select i from StatementImport i join fetch i.account where i.account.id = :accountId"
            + " order by i.period.start asc, i.importedAt asc")
    java.util.List<StatementImport> findByAccount(@Param("accountId") UUID accountId);

    /**
     * Whether this exact file has already been imported into this account, matching
     * {@code uq_statement_imports_content} on {@code (account_id, content_hash)}. Asked
     * before parsing, so re-uploading a statement costs nothing.
     */
    boolean existsByAccount_IdAndContentHash(UUID accountId, String contentHash);
}
