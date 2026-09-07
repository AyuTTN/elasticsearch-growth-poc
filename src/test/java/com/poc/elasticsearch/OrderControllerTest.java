package com.poc.elasticsearch;

import com.poc.elasticsearch.controller.OrderController;
import com.poc.elasticsearch.dto.CreateOrderRequest;
import com.poc.elasticsearch.dto.OrderResponse;
import com.poc.elasticsearch.model.OrderStatus;
import com.poc.elasticsearch.service.OrderService;
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

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @Test
    void createOrderReturnsCreated() throws Exception {
        OrderResponse response = new OrderResponse(
                "order-1",
                "buyer@example.com",
                OrderStatus.PENDING,
                List.of(new OrderResponse.OrderItemResponse("prod-1", "Keyboard", 2, BigDecimal.valueOf(129.99))),
                BigDecimal.valueOf(259.98),
                Instant.parse("2026-01-01T00:00:00Z")
        );

        when(orderService.createOrder(any(CreateOrderRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerEmail": "buyer@example.com",
                                  "items": [
                                    { "productId": "prod-1", "quantity": 2 }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is("order-1")))
                .andExpect(jsonPath("$.status", is("PENDING")));
    }

    @Test
    void searchOrdersReturnsMatches() throws Exception {
        OrderResponse response = new OrderResponse(
                "order-1",
                "buyer@example.com",
                OrderStatus.PENDING,
                List.of(new OrderResponse.OrderItemResponse("prod-1", "Keyboard", 2, BigDecimal.valueOf(129.99))),
                BigDecimal.valueOf(259.98),
                Instant.parse("2026-01-01T00:00:00Z")
        );

        when(orderService.searchOrders(eq("buyer@example.com"), eq(OrderStatus.PENDING)))
                .thenReturn(List.of(response));

        mockMvc.perform(get("/api/orders/search")
                        .param("customerEmail", "buyer@example.com")
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].customerEmail", is("buyer@example.com")));
    }
}
