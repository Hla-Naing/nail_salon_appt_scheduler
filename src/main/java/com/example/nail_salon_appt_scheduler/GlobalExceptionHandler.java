package com.example.nail_salon_appt_scheduler;

import java.time.Instant;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.*;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    public record ApiError(Instant timestamp, int status, String error, String message, String path) {}

    private ResponseEntity<ApiError> error(int code, String message, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(code);
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), code,
                status.getReasonPhrase(), message, request.getRequestURI()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> status(ResponseStatusException e, HttpServletRequest request) {
        int code = e.getStatusCode().value();
        return error(code, code >= 500 ? "An unexpected server error occurred" :
                (e.getReason() == null ? "Request failed" : e.getReason()), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(field -> field.getField() + ": " + field.getDefaultMessage()).distinct()
                .collect(java.util.stream.Collectors.joining("; "));
        return error(400, message, request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, ConstraintViolationException.class,
            HandlerMethodValidationException.class})
    public ResponseEntity<ApiError> invalid(Exception e, HttpServletRequest request) {
        return error(400, "Invalid or missing input; check IDs, dates and required fields", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> conflict(DataIntegrityViolationException e, HttpServletRequest request) {
        return error(409, "Request conflicts with an existing record", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> missing(NoResourceFoundException e, HttpServletRequest request) {
        return error(404, "Resource not found", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> method(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        return error(405, "HTTP method not supported", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> media(HttpMediaTypeNotSupportedException e, HttpServletRequest request) {
        return error(415, "Use the supported content type", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception e, HttpServletRequest request) {
        log.error("Request failed: {}", request.getRequestURI(), e);
        return error(500, "An unexpected server error occurred", request);
    }
}
