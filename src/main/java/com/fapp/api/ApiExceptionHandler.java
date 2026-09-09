package com.fapp.api;

import com.fapp.analytics.MixedCurrencyException;
import com.fapp.analytics.UnknownAnalyticsSubjectException;
import com.fapp.goal.GoalNotFoundException;
import com.fapp.pinned.PinnedGroupNotFoundException;
import com.fapp.security.ForbiddenException;
import com.fapp.statement.DuplicateStatementException;
import com.fapp.statement.StatementImportException;
import com.fapp.statement.StatementParseException;
import com.fapp.statement.UnsupportedProviderException;
import com.fapp.transaction.UnknownUserException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * Turns the exceptions the domain and the import pipeline already raise into the one
 * error shape the API returns.
 *
 * <p>All the translation lives here so that controllers stay free of it and the domain
 * stays free of HTTP. Each mapping is a judgement about whose fault the failure is: a
 * statement FAPP cannot read is the upload's problem, a bank FAPP cannot read is the
 * account's, and a file already imported is neither — it is a request that has already
 * been satisfied.
 */
@RestControllerAdvice
class ApiExceptionHandler {

    /**
     * The caller is known but is asking for another user's data. 403 rather than 404
     * because the path they sent already contains the user id, so there is nothing left
     * to conceal; resources reached by their own id answer 404 instead.
     */
    @ExceptionHandler(ForbiddenException.class)
    ResponseEntity<ApiError> forbidden(ForbiddenException e) {
        return status(HttpStatus.FORBIDDEN, "FORBIDDEN", e.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ApiError> notFound(NotFoundException e) {
        return status(HttpStatus.NOT_FOUND, e.code(), e.getMessage());
    }

    /**
     * A savings goal, or its owner, could not be resolved for this caller. A goal
     * belonging to somebody else reports the same thing as one that does not exist.
     */
    @ExceptionHandler(GoalNotFoundException.class)
    ResponseEntity<ApiError> goalNotFound(GoalNotFoundException e) {
        return status(HttpStatus.NOT_FOUND, e.code(), e.getMessage());
    }

    /**
     * A pinned group, or a transaction being added to it, could not be resolved for
     * this caller. Belonging to somebody else reports the same thing as not existing.
     */
    @ExceptionHandler(PinnedGroupNotFoundException.class)
    ResponseEntity<ApiError> pinnedGroupNotFound(PinnedGroupNotFoundException e) {
        return status(HttpStatus.NOT_FOUND, e.code(), e.getMessage());
    }

    @ExceptionHandler(UnknownUserException.class)
    ResponseEntity<ApiError> unknownUser(UnknownUserException e) {
        return status(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", e.getMessage());
    }

    /**
     * A total was asked for that would have to add unlike currencies. Unprocessable
     * rather than a bad request: the question is well formed, there is simply no correct
     * answer to give without an exchange rate.
     */
    @ExceptionHandler(MixedCurrencyException.class)
    ResponseEntity<ApiError> mixedCurrencies(MixedCurrencyException e) {
        return status(HttpStatus.UNPROCESSABLE_ENTITY, "MIXED_CURRENCY_ACCOUNTS", e.getMessage());
    }

    /** Analytics asked for a user or account that cannot be resolved for this caller. */
    @ExceptionHandler(UnknownAnalyticsSubjectException.class)
    ResponseEntity<ApiError> unknownSubject(UnknownAnalyticsSubjectException e) {
        return status(HttpStatus.NOT_FOUND, e.code(), e.getMessage());
    }

    /** The statement itself cannot be read: wrong format, or a row FAPP cannot parse. */
    @ExceptionHandler(StatementParseException.class)
    ResponseEntity<ApiError> unreadableStatement(StatementParseException e) {
        return status(HttpStatus.BAD_REQUEST, "STATEMENT_MALFORMED", e.getMessage());
    }

    /**
     * Already imported. A conflict rather than an error in the upload: the account is
     * in the state the caller was asking for.
     */
    @ExceptionHandler(DuplicateStatementException.class)
    ResponseEntity<ApiError> alreadyImported(DuplicateStatementException e) {
        return status(HttpStatus.CONFLICT, "STATEMENT_ALREADY_IMPORTED", e.getMessage());
    }

    /**
     * The file may be fine, but no adapter reads the bank this account is configured
     * for, so the request cannot be carried out however it is re-sent.
     */
    @ExceptionHandler(UnsupportedProviderException.class)
    ResponseEntity<ApiError> unsupportedProvider(UnsupportedProviderException e) {
        return status(HttpStatus.UNPROCESSABLE_ENTITY, "PROVIDER_NOT_SUPPORTED", e.getMessage());
    }

    /** Anything else the import refused, including losing a race to a concurrent import. */
    @ExceptionHandler(StatementImportException.class)
    ResponseEntity<ApiError> importFailed(StatementImportException e) {
        return status(HttpStatus.CONFLICT, "IMPORT_CONFLICT", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalidBody(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(ApiError.of(
                "VALIDATION_FAILED", "one or more fields were rejected", fields));
    }

    /**
     * A path variable or query parameter that is not the type it has to be: a malformed
     * UUID, or a date that is not an ISO date.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> invalidParameter(MethodArgumentTypeMismatchException e) {
        return status(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                "'" + e.getName() + "' is not a valid " + expectedTypeOf(e));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadableBody(HttpMessageNotReadableException e) {
        return status(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST_BODY",
                "the request body could not be read as JSON");
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<ApiError> missingPart(MissingServletRequestPartException e) {
        return status(HttpStatus.BAD_REQUEST, "FILE_REQUIRED",
                "a statement file must be uploaded as the 'file' part of a multipart request");
    }

    /** A required query parameter was left out, most often an analytics date range. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiError> missingParameter(MissingServletRequestParameterException e) {
        return status(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER",
                "'" + e.getParameterName() + "' is required");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> uploadTooLarge(MaxUploadSizeExceededException e) {
        return status(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                "the uploaded statement is larger than this API accepts");
    }

    /**
     * A domain rule refused the input: an amount FAPP would have to round, a currency
     * that is not the account's, an unusable email. The message comes from the domain,
     * which states these plainly enough to show a caller.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> rejectedByDomain(IllegalArgumentException e) {
        return status(HttpStatus.BAD_REQUEST, "INVALID_INPUT", e.getMessage());
    }

    /**
     * A uniqueness or referential rule in the database refused the write — a second
     * user on one email address, most likely. Reported without the SQL.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> constraintViolated(DataIntegrityViolationException e) {
        return status(HttpStatus.CONFLICT, "CONFLICT",
                "the request conflicts with data that already exists");
    }

    private static String expectedTypeOf(MethodArgumentTypeMismatchException e) {
        Class<?> required = e.getRequiredType();
        return required == null ? "value" : required.getSimpleName();
    }

    private static ResponseEntity<ApiError> status(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiError.of(code, message));
    }
}
