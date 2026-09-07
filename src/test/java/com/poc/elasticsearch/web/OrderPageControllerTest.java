package com.poc.elasticsearch.web;

import com.poc.elasticsearch.exception.OrderPlacementException;
import com.poc.elasticsearch.model.Product;
import com.poc.elasticsearch.service.OrderService;
import com.poc.elasticsearch.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(OrderPageController.class)
class OrderPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private ProductService productService;

    @BeforeEach
    void setUp() {
        when(productService.findAll()).thenReturn(List.of(
                new Product(
                        "seed-headphones",
                        "Wireless Headphones",
                        "Noise-cancelling over-ear headphones",
                        "Electronics",
                        new BigDecimal("199.99"),
                        25
                )
        ));
        when(orderService.search(nullable(String.class), nullable(String.class))).thenReturn(List.of());
    }

    @Test
    void displaysOrdersPage() throws Exception {
        mockMvc.perform(get("/orders"))
                .andExpect(status().isOk())
                .andExpect(view().name("orders"))
                .andExpect(content().string(containsString("Place order")));
    }

    @Test
    void rejectsInvalidOrderForm() throws Exception {
        mockMvc.perform(post("/orders")
                        .param("customerEmail", "not-an-email")
                        .param("productId", "")
                        .param("quantity", "0"))
                .andExpect(status().isOk())
                .andExpect(view().name("orders"));

        verify(orderService, never()).place(any(OrderForm.class));
    }

    @Test
    void showsStockErrorWithoutRedirect() throws Exception {
        when(orderService.place(any(OrderForm.class)))
                .thenThrow(new OrderPlacementException("Not enough stock for Wireless Headphones. Available: 1."));

        mockMvc.perform(post("/orders")
                        .param("customerEmail", "buyer@example.com")
                        .param("productId", "seed-headphones")
                        .param("quantity", "9"))
                .andExpect(status().isOk())
                .andExpect(view().name("orders"))
                .andExpect(content().string(containsString("Not enough stock")));
    }

    @Test
    void placesOrderAndRedirects() throws Exception {
        mockMvc.perform(post("/orders")
                        .param("customerEmail", "buyer@example.com")
                        .param("productId", "seed-headphones")
                        .param("quantity", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders"));

        verify(orderService).place(any(OrderForm.class));
    }
}
