package be.kuleuven.dsgt4.ticketsupplier.web;

import be.kuleuven.dsgt4.ticketsupplier.service.TicketSupplierException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(TicketSupplierException.class)
    public ResponseEntity<ErrorResponse> handle(TicketSupplierException e) {
        HttpStatus status = switch (e.reason()) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND       -> HttpStatus.NOT_FOUND;
            case CONFLICT        -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status).body(new ErrorResponse(e.getMessage()));
    }
}
