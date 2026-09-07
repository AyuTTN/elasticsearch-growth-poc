package com.poc.elasticsearch;

import com.poc.elasticsearch.controller.ProductController;
import com.poc.elasticsearch.dto.CreateProductRequest;
import com.poc.elasticsearch.dto.ProductResponse;
import com.poc.elasticsearch.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @Test
    void createProductReturnsCreated() throws Exception {
        ProductResponse response = new ProductResponse(
                "prod-1",
                "Wireless Headphones",
                "Noise cancelling",
                "electronics",
                BigDecimal.valueOf(199.99),
                25,
                Instant.parse("2026-01-01T00:00:00Z")
        );

        when(productService.createProduct(any(CreateProductRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Wireless Headphones",
                                  "description": "Noise cancelling",
                                  "category": "electronics",
                                  "price": 199.99,
                                  "stock": 25
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is("prod-1")))
                .andExpect(jsonPath("$.name", is("Wireless Headphones")));
    }

    @Test
    void getProductReturnsProduct() throws Exception {
        ProductResponse response = new ProductResponse(
                "prod-1",
                "Wireless Headphones",
                "Noise cancelling",
                "electronics",
                BigDecimal.valueOf(199.99),
                25,
                Instant.parse("2026-01-01T00:00:00Z")
        );

        when(productService.getProduct("prod-1")).thenReturn(response);

        mockMvc.perform(get("/api/products/prod-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category", is("electronics")));
    }

    @Test
    void searchProductsReturnsMatches() throws Exception {
        ProductResponse response = new ProductResponse(
                "prod-1",
                "Wireless Headphones",
                "Noise cancelling",
                "electronics",
                BigDecimal.valueOf(199.99),
                25,
                Instant.parse("2026-01-01T00:00:00Z")
        );

        when(productService.searchProducts(eq("headphones"), eq("electronics")))
                .thenReturn(List.of(response));

        mockMvc.perform(get("/api/products/search")
                        .param("q", "headphones")
                        .param("category", "electronics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Wireless Headphones")));
    }
}
