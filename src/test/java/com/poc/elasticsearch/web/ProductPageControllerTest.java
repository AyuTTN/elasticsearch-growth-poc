package com.poc.elasticsearch.web;

import com.poc.elasticsearch.model.Product;
import com.poc.elasticsearch.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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

@WebMvcTest(ProductPageController.class)
class ProductPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @BeforeEach
    void setUpProducts() {
        List<Product> products = List.of(
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
        when(productService.findAll()).thenReturn(products);
        when(productService.search(nullable(String.class), nullable(String.class)))
                .thenReturn(products);
    }

    @Test
    void displaysProductPage() throws Exception {
        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(view().name("products"))
                .andExpect(content().string(containsString("Product catalog")))
                .andExpect(content().string(containsString("Wireless Headphones")))
                .andExpect(content().string(containsString("Standing Desk")))
                .andExpect(content().string(containsString("Espresso Beans")))
                .andExpect(content().string(containsString("Running Shoes")));
    }

    @Test
    void forwardsSearchParametersAndKeepsThemInThePage() throws Exception {
        mockMvc.perform(get("/products")
                        .param("q", "headphones")
                        .param("category", "Electronics"))
                .andExpect(status().isOk())
                .andExpect(view().name("products"))
                .andExpect(model().attribute("q", "headphones"))
                .andExpect(model().attribute("category", "Electronics"))
                .andExpect(model().attribute("searchActive", true));

        verify(productService).search("headphones", "Electronics");
    }

    @Test
    void rejectsInvalidProduct() throws Exception {
        mockMvc.perform(post("/products")
                        .param("name", "")
                        .param("description", "")
                        .param("category", "")
                        .param("price", "0")
                        .param("stock", "-1"))
                .andExpect(status().isOk())
                .andExpect(view().name("products"))
                .andExpect(model().attributeHasFieldErrors(
                        "productForm", "name", "description", "category", "price", "stock"
                ));

        verify(productService, never()).create(any(ProductForm.class));
    }

    @Test
    void addsProductAndRedirects() throws Exception {
        mockMvc.perform(post("/products")
                        .param("name", "Wireless Headphones")
                        .param("description", "Noise-cancelling over-ear headphones")
                        .param("category", "Electronics")
                        .param("price", "199.99")
                        .param("stock", "25"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/products"));

        verify(productService).create(any(ProductForm.class));
    }
}
