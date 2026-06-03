package be.kuleuven.dsgt4.broker.supplier;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/*
    Fake supplier. Returns a fixed catalog so the broker has data
    to show before the real HTTP suppliers exist. This is the class that will
    be replaced by an HTTP-backed SupplierClient
 */
@Component
public class StubSupplierClient implements SupplierClient {

    @Override
    public List<Product> getProducts() {
        return List.of(
            new Product("T-001", "Coldplay @ Sportpaleis", "2026-08-15, main floor", new BigDecimal("85.00"), 100),
            new Product("F-001", "Nachos",                 "Sharing portion",        new BigDecimal("6.50"),  200),
            new Product("D-001", "Trappist beer",          "33cl bottle",            new BigDecimal("4.00"),  500)
        );
    }
}