package be.kuleuven.dsgt4.broker.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/*
   The shop front: a grid of concerts to choose from. The catalog (tickets) is supplied to
   the view by CatalogModelAdvice; picking a concert leads to /concerts/{id}.
*/
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "home";
    }
}
