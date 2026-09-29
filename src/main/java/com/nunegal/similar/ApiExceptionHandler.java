package com.nunegal.similar;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(UpstreamException.class)
    ResponseEntity<Void> upstreamFailure(UpstreamException exception) {
        int status = switch (exception.kind()) {
            case NOT_FOUND -> 404;
            case TIMEOUT -> 504;
            case FAILURE -> 502;
        };
        return ResponseEntity.status(status).build();
    }
}
