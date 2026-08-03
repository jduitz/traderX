package finos.traderx.tradeservice.controller;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException exception) {
    String detail = exception.getReason();
    if (detail == null || detail.isBlank()) {
      detail = "Request failed.";
    }
    return ResponseEntity.status(exception.getStatusCode())
        .body(Map.of("detail", detail));
  }
}
