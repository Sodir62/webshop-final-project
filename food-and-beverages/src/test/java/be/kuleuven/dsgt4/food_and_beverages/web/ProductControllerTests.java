package be.kuleuven.dsgt4.food_and_beverages.web;

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
    The catalog the broker lists. GET /products returns the full {id,name,description,price,stock}
    shape the broker deserialises; ?type=FOOD / ?type=DRINK returns only that kind, which is how
    the broker's separate FOOD and DRINK suppliers each list their own catalog from this one service.
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
    void typeFoodReturnsOnlyFoodProducts() throws Exception {
        mvc.perform(get("/products").param("type", "FOOD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", everyItem(startsWith("F-"))));
    }

    @Test
    void typeDrinkReturnsOnlyDrinkProducts() throws Exception {
        mvc.perform(get("/products").param("type", "DRINK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", everyItem(startsWith("D-"))));
    }
}
