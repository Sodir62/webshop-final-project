package be.kuleuven.dsgt4.broker.supplier;

import be.kuleuven.dsgt4.broker.data.SupplierType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StubSupplierClientTests {

    private StubSupplierClient supplier() {
        return new StubSupplierClient(SupplierType.FOOD, List.of(
                new Product("F-001", "Nachos", "Sharing portion", new BigDecimal("6.50"), 10)));
    }

    private int stock(StubSupplierClient s) {
        return s.find("F-001").orElseThrow().stock();
    }

    @Test
    void confirmIsIdempotent() {
        StubSupplierClient s = supplier();
        String id = s.reserve("F-001", 3);            // 10 -> 7
        s.confirm(id);
        assertDoesNotThrow(() -> s.confirm(id));       // a second confirm is a no-op, not an error
        assertEquals(7, stock(s));                     // and it doesn't move stock
    }

    @Test
    void cancelAfterConfirmDoesNotRestoreStock() {
        StubSupplierClient s = supplier();
        String id = s.reserve("F-001", 3);            // 10 -> 7
        s.confirm(id);
        s.cancel(id);                                  // the sale is final
        assertEquals(7, stock(s));                     // so the stock is NOT given back
    }

    @Test
    void cancelIsIdempotentAndRestoresAHeldHoldExactlyOnce() {
        StubSupplierClient s = supplier();
        String id = s.reserve("F-001", 3);            // 10 -> 7
        s.cancel(id);                                  // still held -> stock restored: 7 -> 10
        assertDoesNotThrow(() -> s.cancel(id));        // second cancel is a no-op, not an error
        assertEquals(10, stock(s));                    // restored once, not twice
    }
}
