package com.fapp.account;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    /**
     * Loads an account together with its owner.
     *
     * <p>Needed because the owner is fetched lazily and the session is closed by the
     * time a request handler has the account: importing reads the owner's id to stamp it
     * onto every transaction, and a bare {@code findById} would fail on that outside a
     * transaction. Fetching it up front is both correct and one query rather than two.
     */
    @Query("select a from Account a join fetch a.user where a.id = :id")
    Optional<Account> findByIdWithUser(@Param("id") UUID id);

    List<Account> findByUser_IdOrderByProviderAscDisplayNameAsc(UUID userId);

    /**
     * How many accounts the user holds. Transfer detection asks first: with fewer than
     * two there is nowhere to transfer to, so there is nothing to search for.
     */
    long countByUser_Id(UUID userId);
}
