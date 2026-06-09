package be.kuleuven.dsgt4.broker.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/*
    Web-layer tests for the order form. @Transactional rolls back any saved order so the
    happy-path test leaves no rows in the DB. Security is on the classpath now, so the POSTs
    carry a CSRF token via .with(csrf()) -- the real browser form gets one automatically from
    Thymeleaf (th:action).
*/
// "stub": in-process suppliers, so the test runs without live supplier services.
@SpringBootTest
@ActiveProfiles("stub")
@AutoConfigureMockMvc
@Transactional
class OrderControllerTests {

    @Autowired
    private MockMvc mvc;

    @Test
    void invalidOrderReshowsConcertPageWithFieldErrors() throws Exception {
        // A valid concert but a missing delivery address -> re-render THAT concert's page
        // (not the landing grid), with the field error preserved.
        mvc.perform(post("/orders").with(csrf())
                        .param("ticketProductId", "T-001")
                        .param("ticketQty", "1")
                        .param("deliveryAddress", "")          // missing -> @NotBlank
                        .param("cardholderName", "Alice")
                        .param("cardLast4", "4242"))
                .andExpect(status().isOk())
                .andExpect(view().name("concert"))
                .andExpect(model().attributeHasFieldErrors("orderForm", "deliveryAddress"));
    }

    @Test
    void foodPickedWithZeroPortionsIsRejectedNotSilentlyDropped() throws Exception {
        mvc.perform(post("/orders").with(csrf())
                        .param("ticketProductId", "T-001")
                        .param("ticketQty", "1")
                        .param("foodProductId", "F-001")       // dish picked...
                        .param("foodQty", "0")                 // ...but zero portions
                        .param("deliveryAddress", "Diestsestraat 1, Leuven")
                        .param("cardholderName", "Alice Smith")
                        .param("cardLast4", "4242"))
                .andExpect(status().isOk())
                .andExpect(view().name("concert"))
                .andExpect(model().attributeHasFieldErrors("orderForm", "foodLineConsistent"));
    }

    @Test
    void validOrderRedirectsToTheOrderPage() throws Exception {
        mvc.perform(post("/orders").with(csrf())
                        .param("ticketProductId", "T-001")
                        .param("ticketQty", "1")
                        .param("deliveryAddress", "Diestsestraat 1, Leuven")
                        .param("cardholderName", "Alice Smith")
                        .param("cardLast4", "4242"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/orders/*"));
    }

    @Test
    void drinkPickedWithZeroPortionsIsRejectedNotSilentlyDropped() throws Exception {
        mvc.perform(post("/orders").with(csrf())
                        .param("ticketProductId", "T-001")
                        .param("ticketQty", "1")
                        .param("drinkProductId", "D-001")      // drink picked...
                        .param("drinkQty", "0")                // ...but zero portions
                        .param("deliveryAddress", "Diestsestraat 1, Leuven")
                        .param("cardholderName", "Alice Smith")
                        .param("cardLast4", "4242"))
                .andExpect(status().isOk())
                .andExpect(view().name("concert"))
                .andExpect(model().attributeHasFieldErrors("orderForm", "drinkLineConsistent"));
    }

    @Test
    void nonNumericCardLast4IsRejected() throws Exception {
        mvc.perform(post("/orders").with(csrf())
                        .param("ticketProductId", "T-001")
                        .param("ticketQty", "1")
                        .param("deliveryAddress", "Diestsestraat 1, Leuven")
                        .param("cardholderName", "Alice Smith")
                        .param("cardLast4", "abcd"))           // not 4 digits -> @Pattern fails
                .andExpect(status().isOk())
                .andExpect(view().name("concert"))
                .andExpect(model().attributeHasFieldErrors("orderForm", "cardLast4"));
    }
}
