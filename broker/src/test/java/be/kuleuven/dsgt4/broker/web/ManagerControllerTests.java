package be.kuleuven.dsgt4.broker.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/*
    The manager dashboard is behind HTTP Basic + the MANAGER role: an anonymous request is
    rejected with 401, an authenticated MANAGER gets the page.
*/
@SpringBootTest
@AutoConfigureMockMvc
class ManagerControllerTests {

    @Autowired
    private MockMvc mvc;

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mvc.perform(get("/manager/orders"))
                .andExpect(status().isUnauthorized());   // 401 (HTTP Basic, not a login redirect)
    }

    @Test
    void managerSeesAllOrders() throws Exception {
        mvc.perform(get("/manager/orders").with(user("admin").roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(view().name("manager"));
    }
}
