package be.kuleuven.dsgt4.broker.supplier;

import be.kuleuven.dsgt4.broker.data.SupplierType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/*
    Fake supplier service, should be done for real as a project in the other folders
*/
public class StubSupplierClient implements SupplierClient {
    private final SupplierType type;
    private final Map<String, Product> catalog = new LinkedHashMap<>();        // productId -> product
    private final Map<String, Integer> stock = new ConcurrentHashMap<>();      // productId -> units left
    private final Map<String, Reservation> reservations = new ConcurrentHashMap<>(); // id -> hold
    private volatile boolean down = false;

    public StubSupplierClient(SupplierType type, List<Product> products) {
        this.type = type;
        for (Product p : products) {
            catalog.put(p.id(), p);
            stock.put(p.id(), p.stock());
        }
    }

    /** Test/demo hook: flip the supplier "offline" so every call throws. */
    public void setDown(boolean down) {
        this.down = down;
    }

    private void ensureUp() {
        if (down) {
            throw new SupplierException(type + " supplier is unreachable (simulated outage)");
        }
    }

    @Override
    public SupplierType type() {
        return type;
    }

    @Override
    public List<Product> list() {
        ensureUp();
        // Rebuild each Product with its CURRENT stock so the catalog page is live.
        return catalog.values().stream()
                .map(p -> new Product(p.id(), p.name(), p.description(), p.price(), stock.get(p.id())))
                .toList();
    }

    @Override
    public Optional<Product> find(String productId) {
        ensureUp();
        Product p = catalog.get(productId);
        if (p == null) {
            return Optional.empty();
        }
        return Optional.of(new Product(p.id(), p.name(), p.description(), p.price(), stock.get(productId)));
    }

    @Override
    public synchronized String reserve(String productId, int quantity) {
        ensureUp();
        Integer available = stock.get(productId);
        if (available == null) {
            throw new SupplierException("unknown product " + productId + " at " + type);
        }
        if (quantity <= 0) {
            throw new SupplierException("quantity must be positive, was " + quantity);
        }
        if (available < quantity) {
            throw new SupplierException("out of stock for " + productId
                    + " (have " + available + ", need " + quantity + ")");
        }
        stock.put(productId, available - quantity);   // hold the stock now
        String reservationId = type + "-" + UUID.randomUUID();
        reservations.put(reservationId, new Reservation(productId, quantity));
        return reservationId;
    }

    @Override
    public synchronized void confirm(String reservationId) {
        ensureUp();
        Reservation r = reservations.get(reservationId);
        if (r == null) {
            throw new SupplierException("unknown reservation " + reservationId);
        }
        // Keep the hold and mark it sold. Idempotent: a second confirm (e.g. the broker
        // retrying it on recovery) just re-sets the flag. Stock was taken at reserve time.
        r.confirmed = true;
    }

    @Override
    public synchronized void cancel(String reservationId) {
        ensureUp();
        Reservation r = reservations.remove(reservationId);
        if (r == null) {
            return;   // already gone -> releasing nothing is not an error (idempotent)
        }
        if (!r.confirmed) {
            stock.merge(r.productId, r.quantity, Integer::sum);   // give a still-held hold back
        }
        // a confirmed sale is final: its stock is not restored
    }

    /** A hold. Once confirmed the sale is final, so cancel won't give its stock back. */
    private static final class Reservation {
        final String productId;
        final int quantity;
        boolean confirmed = false;

        Reservation(String productId, int quantity) {
            this.productId = productId;
            this.quantity = quantity;
        }
    }
}
