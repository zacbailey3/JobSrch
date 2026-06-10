package com.jobsrch.common;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail handleNotFound(NotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail handleResponseStatus(ResponseStatusException exception) {
        return ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Request validation failed");
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        problem.setProperty("errors", errors);
        return problem;
    }
}
