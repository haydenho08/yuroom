package dev.yuroom.api;

import dev.yuroom.availability.NotFoundException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every error leaves the API in one shape: RFC 9457 "problem details"
 * ({@code application/problem+json} with type, title, status, detail). Extending
 * {@link ResponseEntityExceptionHandler} gives Spring's own errors (bad types, missing params,
 * unknown URLs) the same shape as ours.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** One invalid parameter: which one, and why. */
    record ParamError(String parameter, String message) {
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        p.setTitle("Not found");
        return p;
    }

    @ExceptionHandler(BadRequestException.class)
    ProblemDetail badRequest(BadRequestException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getParameter() + ": " + e.getMessage());
        p.setTitle("Invalid request parameter");
        p.setProperty("errors", List.of(new ParamError(e.getParameter(), e.getMessage())));
        return p;
    }

    /** Constraint violations on @RequestParam/@PathVariable (e.g. minMinutes=-1). */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ParamError> errors = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(err -> new ParamError(r.getMethodParameter().getParameterName(), err.getDefaultMessage())))
                .toList();
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, errors.stream()
                .map(e -> e.parameter() + ": " + e.message()).reduce((a, b) -> a + "; " + b).orElse("Invalid request"));
        p.setTitle("Invalid request parameter");
        p.setProperty("errors", errors);
        return ResponseEntity.badRequest().headers(headers).body(p);
    }

    /** Anything unexpected: log it with the stack trace, but never send internals to the client. */
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        log.error("Unhandled error", e);
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong on our side.");
        p.setTitle("Internal error");
        return p;
    }
}
