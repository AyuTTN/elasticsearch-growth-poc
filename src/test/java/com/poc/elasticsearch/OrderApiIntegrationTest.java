package com.poc.elasticsearch;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderApiIntegrationTest extends AbstractIntegrationTest {

    @Test
    void createAndSearchOrders() throws Exception {
        String productPayload = """
                {
                  "name": "Mechanical Keyboard",
                  "description": "RGB mechanical keyboard",
                  "category": "electronics",
                  "price": 129.99,
                  "stock": 10
                }
                """;

        String productResponse = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String productId = productResponse.replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        String orderPayload = String.format("""
                {
                  "customerEmail": "buyer@example.com",
                  "items": [
                    { "productId": "%s", "quantity": 2 }
                  ]
                }
                """, productId);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerEmail", is("buyer@example.com")))
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.totalAmount", is(259.98)))
                .andExpect(jsonPath("$.items", hasSize(1)));

        mockMvc.perform(get("/api/orders/search").param("customerEmail", "buyer@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].customerEmail", is("buyer@example.com")));

        mockMvc.perform(get("/api/orders/search").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void rejectsOrderWhenStockIsInsufficient() throws Exception {
        String productPayload = """
                {
                  "name": "USB Cable",
                  "description": "Short USB-C cable",
                  "category": "accessories",
                  "price": 9.99,
                  "stock": 1
                }
                """;

        String productResponse = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String productId = productResponse.replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        String orderPayload = String.format("""
                {
                  "customerEmail": "buyer@example.com",
                  "items": [
                    { "productId": "%s", "quantity": 5 }
                  ]
                }
                """, productId);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload))
                .andExpect(status().isBadRequest());
    }
}
