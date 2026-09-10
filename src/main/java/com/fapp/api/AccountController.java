package com.fapp.api;

import com.fapp.account.Account;
import com.fapp.account.AccountRepository;
import com.fapp.security.CurrentUser;
import com.fapp.statement.StatementImport;
import com.fapp.statement.StatementImportRepository;
import com.fapp.statement.StatementImportService;
import com.fapp.transaction.Transaction;
import com.fapp.transaction.TransactionRepository;
import com.fapp.user.User;
import com.fapp.user.UserRepository;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URI;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Accounts and the two things done to them: importing a statement and reading back what
 * it produced.
 *
 * <p>Kept thin on purpose. Deciding what in a statement is new, what the account already
 * holds and what occurrence a repeat takes belongs to
 * {@link StatementImportService} and is not restated here — this resolves the account,
 * checks the upload is actually an upload, and hands over.
 */
@RestController
@RequestMapping("/api/accounts")
class AccountController {

    private final UserRepository users;
    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final StatementImportService statementImports;
    private final StatementImportRepository imports;
    private final CurrentUser currentUser;

    AccountController(UserRepository users,
                      AccountRepository accounts,
                      TransactionRepository transactions,
                      StatementImportService statementImports,
                      StatementImportRepository imports,
                      CurrentUser currentUser) {
        this.users = users;
        this.accounts = accounts;
        this.transactions = transactions;
        this.statementImports = statementImports;
        this.imports = imports;
        this.currentUser = currentUser;
    }

    @PostMapping
    ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        // An account may only be opened for oneself.
        currentUser.requireSelf(request.userId());
        User owner = users.findById(request.userId()).orElseThrow(() -> new NotFoundException(
                "USER_NOT_FOUND", "no user with id " + request.userId()));

        Account account = Account.of(
                owner,
                request.provider(),
                request.displayName(),
                request.accountType(),
                Currency.getInstance(request.currency()));
        accounts.save(account);

        /*
         * Answered from the instance just built, not from what save returned. Ids are
         * assigned in the constructor, so Spring Data reads the account as detached and
         * merges it -- and a merge returns a managed copy whose owner is a lazy proxy,
         * which would fail to resolve once save's transaction has closed. The instance
         * here already holds the real owner and the id that was written.
         */
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.of(account));
    }

    /**
     * Imports a statement file into an account.
     *
     * <p>Which bank's format the file is read as comes from the account, never from the
     * request: an account is created against a provider and that is what selects the
     * adapter, so a caller cannot ask for their Monzo export to be read as something
     * else.
     *
     * <p>Answers 201 whether every row was new or every row was already held. Both are
     * successful imports, and the counts in the body say which happened.
     */
    @PostMapping("/{accountId}/statements")
    ResponseEntity<StatementImportResponse> importStatement(@PathVariable UUID accountId,
                                                            @RequestPart("file") MultipartFile file) {
        Account account = ownedAccount(accountId);
        if (file.isEmpty()) {
            throw new IllegalArgumentException("the uploaded statement file is empty");
        }

        StatementImport result = statementImports.importStatement(account, bytesOf(file));

        return ResponseEntity.created(URI.create("/api/imports/" + result.id()))
                .body(StatementImportResponse.of(result));
    }

    @GetMapping("/{accountId}")
    AccountResponse get(@PathVariable UUID accountId) {
        return AccountResponse.of(ownedAccount(accountId));
    }

    /**
     * Removes an account and everything imported into it.
     *
     * <p>The schema already says what "everything" means: the foreign keys from
     * {@code statement_imports}, {@code transactions} and {@code transfers} all cascade,
     * so the database removes the account's history in one statement rather than the
     * application deleting four tables in an order it has to get right. Savings goals
     * belong to the user, not to an account, and are untouched.
     *
     * <p>Irreversible, and deliberately not softened into a flag: FAPP stores only what
     * analysis needs, so keeping a deleted account's transactions to hide them later
     * would retain more than it should.
     */
    @DeleteMapping("/{accountId}")
    ResponseEntity<Void> delete(@PathVariable UUID accountId) {
        accounts.delete(ownedAccount(accountId));
        return ResponseEntity.noContent().build();
    }

    /**
     * Which statements are loaded into this account, with the period each one covers as
     * detected from the file itself. This is what makes a single month removable.
     */
    @GetMapping("/{accountId}/statements")
    List<StatementImportResponse> statements(@PathVariable UUID accountId) {
        ownedAccount(accountId);
        return imports.findByAccount(accountId).stream().map(StatementImportResponse::of).toList();
    }

    @GetMapping("/{accountId}/transactions")
    List<TransactionResponse> transactions(@PathVariable UUID accountId) {
        ownedAccount(accountId);
        return transactions.findByAccount_IdOrderByBookingDateDescCreatedAtDesc(accountId).stream()
                .map(TransactionResponse::of)
                .toList();
    }

    /**
     * The account behind this id, if it belongs to the caller.
     *
     * <p>Fetched with its owner, both so the response can report {@code userId} outside a
     * transaction and so ownership can be proved without a second query. Someone else's
     * account reports the same not-found as one that does not exist: answering 403 would
     * confirm that the id is real, which is enough to enumerate other people's accounts.
     */
    private Account ownedAccount(UUID accountId) {
        Account account = accounts.findByIdWithUser(accountId).orElseThrow(() -> new NotFoundException(
                "ACCOUNT_NOT_FOUND", "no account with id " + accountId));
        if (!account.userId().equals(currentUser.requireId())) {
            throw new NotFoundException("ACCOUNT_NOT_FOUND", "no account with id " + accountId);
        }
        return account;
    }

    private static byte[] bytesOf(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("the uploaded statement file could not be read", e);
        }
    }
}
