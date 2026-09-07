package com.poc.elasticsearch;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductApiIntegrationTest extends AbstractIntegrationTest {

    @Test
    void createGetAndSearchProducts() throws Exception {
        String createPayload = """
                {
                  "name": "Wireless Headphones",
                  "description": "Noise cancelling over-ear headphones",
                  "category": "electronics",
                  "price": 199.99,
                  "stock": 25
                }
                """;

        String productId = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Wireless Headphones")))
                .andExpect(jsonPath("$.category", is("electronics")))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Wireless Headphones")));

        mockMvc.perform(get("/api/products/search").param("q", "noise cancelling"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Wireless Headphones")));

        mockMvc.perform(get("/api/products/search").param("category", "electronics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void returnsNotFoundForMissingProduct() throws Exception {
        mockMvc.perform(get("/api/products/{id}", "missing-id"))
                .andExpect(status().isNotFound());
    }
}
