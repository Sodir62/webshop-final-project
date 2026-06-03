package be.kuleuven.dsgt4.broker.web;

import be.kuleuven.dsgt4.broker.supplier.Product;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.math.BigDecimal;
import java.util.List;

/*
    Spring MVC controller for the home page.
    The method builds a "Model" (a name -> value map of data the page needs)
    and returns a *view name* (a String). Spring then renders the matching
    Thymeleaf template in src/main/resources/templates/ and sends back the HTML.
 */
@Controller
public class HomeController {

    @GetMapping("/")                        
    public String home(Model model) {        // Spring passes in an empty Model
        List<Product> products = List.of(
            new Product("T-001", "Coldplay @ Sportpaleis", "2026-08-15, main floor", new BigDecimal("85.00"), 100),
            new Product("F-001", "Nachos",                 "Sharing portion",        new BigDecimal("6.50"),  200),
            new Product("D-001", "Trappist beer",          "33cl bottle",            new BigDecimal("4.00"),  500)
        );
        model.addAttribute("products", products);   // expose under "products" -> read as ${products} in home.html
        return "home";                              // view name -> renders templates/home.html
    }
}