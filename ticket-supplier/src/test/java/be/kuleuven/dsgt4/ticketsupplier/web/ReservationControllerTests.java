package be.kuleuven.dsgt4.ticketsupplier.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
    Pins the ticket supplier's HTTP contract, identical to the food service so the broker's one
    HttpSupplierClient talks to both: reserve 201 {reservationId}, confirm 200, cancel 204, and
    the Reason->status mapping (out-of-stock 409, unknown 404, bad quantity 400).
*/
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReservationControllerTests {

    @Autowired private MockMvc mvc;

    // Reserve and return the supplier-generated id, parsed from the {"reservationId":"..."} body.
    private String reserveId(String productId, int quantity) throws Exception {
        String body = mvc.perform(post("/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\",\"quantity\":" + quantity + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Matcher m = Pattern.compile("\"reservationId\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        if (!m.find()) {
            throw new AssertionError("no reservationId in response: " + body);
        }
        return m.group(1);
    }

    @Test
    void reserveReturns201WithReservationId() throws Exception {
        mvc.perform(post("/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"T-001\",\"quantity\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservationId").isNotEmpty());
    }

    @Test
    void reserveOutOfStockReturns409() throws Exception {
        mvc.perform(post("/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"T-002\",\"quantity\":100000000}"))
                .andExpect(status().isConflict());
    }

    @Test
    void reserveUnknownProductReturns404() throws Exception {
        mvc.perform(post("/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"NOPE\",\"quantity\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void reserveNonPositiveQuantityReturns400() throws Exception {
        mvc.perform(post("/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"T-001\",\"quantity\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirmReturns200ThenCancelIsA204NoOp() throws Exception {
        String id = reserveId("T-001", 1);

        mvc.perform(post("/reservations/{id}/confirm", id)).andExpect(status().isOk());
        mvc.perform(post("/reservations/{id}/confirm", id)).andExpect(status().isOk());   // idempotent
        mvc.perform(delete("/reservations/{id}", id)).andExpect(status().isNoContent());  // cancel-after-confirm no-op
    }

    @Test
    void cancelReturns204AndIsIdempotent() throws Exception {
        String id = reserveId("T-001", 1);
        mvc.perform(delete("/reservations/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(delete("/reservations/{id}", id)).andExpect(status().isNoContent());   // idempotent
    }

    @Test
    void confirmUnknownReservationReturns404() throws Exception {
        mvc.perform(post("/reservations/{id}/confirm", "does-not-exist"))
                .andExpect(status().isNotFound());
    }
}
