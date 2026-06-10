package be.kuleuven.dsgt4.food_and_beverages;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FoodAndBeveragesApplication {

	public static void main(String[] args) {
		SpringApplication.run(FoodAndBeveragesApplication.class, args);
	}

}
