package be.kuleuven.dsgt4.broker.web;

import be.kuleuven.dsgt4.broker.data.SupplierType;
import be.kuleuven.dsgt4.broker.supplier.StubSupplierClient;
import be.kuleuven.dsgt4.broker.supplier.SupplierRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/*
    Read-path fault tolerance (CatalogModelAdvice): a down supplier must NOT break the page --
    its catalog section shows empty while the others still load. This is the in-process unit
    test of what tests/test-5-supplier-crash.sh checks against the live VMs.
*/
// "stub": in-process suppliers, so we can flip one "down" via setDown without a live service.
@SpringBootTest
@ActiveProfiles("stub")
@AutoConfigureMockMvc
class CatalogDegradationTests {

    @Autowired private MockMvc mvc;
    @Autowired private SupplierRegistry suppliers;

    @Test
    void homepageStillRendersWhenTicketSupplierIsDown() throws Exception {
        StubSupplierClient ticket = (StubSupplierClient) suppliers.get(SupplierType.TICKET);
        ticket.setDown(true);
        try {
            mvc.perform(get("/"))
                    .andExpect(status().isOk())                       // page did not crash
                    .andExpect(view().name("home"))
                    .andExpect(model().attribute("tickets", hasSize(0)))   // down supplier -> empty section
                    .andExpect(model().attribute("food", hasSize(3)))      // others still load
                    .andExpect(model().attribute("drinks", hasSize(3)));
        } finally {
            ticket.setDown(false);   // don't leak the simulated outage into other tests
        }
    }
}
