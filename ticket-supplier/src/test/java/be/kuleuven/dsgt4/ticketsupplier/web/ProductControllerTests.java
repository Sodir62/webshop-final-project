package be.kuleuven.dsgt4.ticketsupplier.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
    The ticket catalog the broker lists, in the broker's {id,name,description,price,stock} shape.
    The broker sends ?type=TICKET; this service has only tickets, so the param is accepted and
    ignored and every product is a T-xxx ticket either way.
*/
@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerTests {

    @Autowired private MockMvc mvc;

    @Test
    void listReturnsTheBrokerProductShape() throws Exception {
        mvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].name").exists())
                .andExpect(jsonPath("$[0].price").exists())
                .andExpect(jsonPath("$[0].stock").exists());
    }

    @Test
    void everyListedProductIsATicket() throws Exception {
        mvc.perform(get("/products").param("type", "TICKET"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", everyItem(startsWith("T-"))));
    }
}
