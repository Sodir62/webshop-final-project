package be.kuleuven.dsgt4.broker.supplier;

import be.kuleuven.dsgt4.broker.data.SupplierType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

/*
    The registry must route each SupplierType to a client that actually serves it.
*/
// "stub": in-process suppliers, so the test runs without live supplier services.
@SpringBootTest
@ActiveProfiles("stub")
class SupplierRegistryTests {

    @Autowired
    private SupplierRegistry suppliers;

    @Test
    void routesEachTypeToItsOwnClient() {
        assertEquals(SupplierType.TICKET, suppliers.get(SupplierType.TICKET).type());
        assertEquals(SupplierType.FOOD, suppliers.get(SupplierType.FOOD).type());
        assertEquals(SupplierType.DRINK, suppliers.get(SupplierType.DRINK).type());
    }

    @Test
    void findReturnsCatalogProduct() {
        assertEquals("Coldplay @ Sportpaleis",
                suppliers.get(SupplierType.TICKET).find("T-001").orElseThrow().name());
    }
}
