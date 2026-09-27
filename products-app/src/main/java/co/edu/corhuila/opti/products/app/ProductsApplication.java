package co.edu.corhuila.opti.products.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point of the products service. */
@SpringBootApplication(scanBasePackages = "co.edu.corhuila.opti.products")
public class ProductsApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductsApplication.class, args);
    }
}
