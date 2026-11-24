package io.sentinelops.ingest.api;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.concurrent.CompletionException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class,
            ConstraintViolationException.class})
    ProblemDetail invalid(Exception ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Invalid telemetry payload");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    @ExceptionHandler(CompletionException.class)
    ProblemDetail publishFailed(CompletionException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        problem.setTitle("Event bus unavailable, retry the request");
        return problem;
    }
}
