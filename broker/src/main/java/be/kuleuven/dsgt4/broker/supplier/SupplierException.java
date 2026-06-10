package be.kuleuven.dsgt4.broker.supplier;

/*
   Thrown by a SupplierClient for any supplier-side failure. The Reason classifies it:
   a permanent reason means the supplier ANSWERED and said no -- retrying cannot change
   that; UNAVAILABLE means there was no usable answer, so a retry may succeed later.
   The split is what lets the retry/recovery paths decide instead of guessing.
*/
public class SupplierException extends RuntimeException {

    public enum Reason {
        NOT_FOUND,        // unknown product or reservation (HTTP 404) -- permanent
        CONFLICT,         // out of stock / hold no longer confirmable (HTTP 409) -- permanent
        INVALID_REQUEST,  // the request itself was rejected (HTTP 400) -- permanent
        UNAVAILABLE       // down, timeout, 5xx, malformed transport -- transient
    }

    private final Reason reason;

    public SupplierException(String message) {
        this(Reason.UNAVAILABLE, message);
    }

    public SupplierException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    /** Permanent = the supplier answered and refused; retrying cannot change the outcome. */
    public boolean isPermanent() {
        return reason != Reason.UNAVAILABLE;
    }
}
