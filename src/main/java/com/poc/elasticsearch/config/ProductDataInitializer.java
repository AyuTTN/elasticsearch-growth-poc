package com.poc.elasticsearch.config;

import com.poc.elasticsearch.model.Product;
import com.poc.elasticsearch.repository.ProductRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class ProductDataInitializer implements ApplicationRunner {

    private static final List<Product> SEED_PRODUCTS = List.of(
            new Product(
                    "seed-headphones",
                    "Wireless Headphones",
                    "Noise-cancelling over-ear headphones",
                    "Electronics",
                    new BigDecimal("199.99"),
                    25
            ),
            new Product(
                    "seed-desk",
                    "Standing Desk",
                    "Height-adjustable desk for home office",
                    "Furniture",
                    new BigDecimal("349.00"),
                    8
            ),
            new Product(
                    "seed-coffee",
                    "Espresso Beans",
                    "Medium roast beans from Colombia",
                    "Grocery",
                    new BigDecimal("18.50"),
                    40
            ),
            new Product(
                    "seed-shoes",
                    "Running Shoes",
                    "Lightweight road-running shoes",
                    "Sports",
                    new BigDecimal("129.00"),
                    16
            )
    );

    private final ProductRepository productRepository;

    public ProductDataInitializer(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Product> missingSeeds = SEED_PRODUCTS.stream()
                .filter(product -> !productRepository.existsById(product.id()))
                .toList();

        if (!missingSeeds.isEmpty()) {
            productRepository.saveAll(missingSeeds);
        }
    }
}
