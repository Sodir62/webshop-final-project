package be.kuleuven.dsgt4.broker.web;

import be.kuleuven.dsgt4.broker.supplier.SupplierClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/*
   Spring MVC controller for the home page.
    
   Spring MVC flow: a request is matched to a method by its @GetMapping path.
   The method builds a "Model" (a name -> value map the view reads) and returns
   a *view name*; Spring renders the matching Thymeleaf template and returns HTML.
   
    The catalog is not built here: the controller asks a SupplierClient for it.
 */

@Controller
public class HomeController {
    private final SupplierClient supplier;

    // Spring injects the SupplierClient it manages (the @Component) into this constructor.
    public HomeController(SupplierClient supplier) {
        this.supplier = supplier;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("products", supplier.getProducts());
        return "home";
    }
}