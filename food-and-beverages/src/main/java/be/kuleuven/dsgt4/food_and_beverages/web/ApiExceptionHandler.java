package be.kuleuven.dsgt4.food_and_beverages.web;

import be.kuleuven.dsgt4.food_and_beverages.service.SupplierException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/*
   Maps a rejected supplier request to an HTTP status + a JSON {"error": ...} body, so the
   broker sees a clear non-2xx (its SupplierClient treats that as the failure to abort on).
*/
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(SupplierException.class)
    public ResponseEntity<ErrorResponse> handle(SupplierException e) {
        HttpStatus status = switch (e.reason()) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status).body(new ErrorResponse(e.getMessage()));
    }
}