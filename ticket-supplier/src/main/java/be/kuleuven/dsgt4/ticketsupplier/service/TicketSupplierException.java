package be.kuleuven.dsgt4.ticketsupplier.service;

public class TicketSupplierException extends RuntimeException {

    public enum Reason {
        INVALID_REQUEST,
        NOT_FOUND,
        CONFLICT
    }

    private final Reason reason;

    public TicketSupplierException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
