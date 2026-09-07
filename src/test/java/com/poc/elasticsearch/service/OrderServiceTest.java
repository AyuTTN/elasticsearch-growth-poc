package com.poc.elasticsearch.service;

import com.poc.elasticsearch.exception.OrderPlacementException;
import com.poc.elasticsearch.model.Order;
import com.poc.elasticsearch.model.Product;
import com.poc.elasticsearch.repository.OrderRepository;
import com.poc.elasticsearch.repository.ProductRepository;
import com.poc.elasticsearch.web.OrderForm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @InjectMocks
    private OrderService orderService;

    @Test
    void placesOrderAndReducesStock() {
        Product product = headphones(25);
        when(productRepository.findById("seed-headphones")).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order order = orderService.place(form("seed-headphones", 2));

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        assertThat(productCaptor.getValue().stock()).isEqualTo(23);
        assertThat(order.customerEmail()).isEqualTo("buyer@example.com");
        assertThat(order.status()).isEqualTo("PENDING");
        assertThat(order.items()).hasSize(1);
        assertThat(order.items().getFirst().productName()).isEqualTo("Wireless Headphones");
        assertThat(order.total()).isEqualByComparingTo("399.98");
    }

    @Test
    void rejectsUnknownProduct() {
        when(productRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.place(form("missing", 1)))
                .isInstanceOf(OrderPlacementException.class)
                .hasMessage("Product not found.");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void rejectsInsufficientStock() {
        when(productRepository.findById("seed-headphones")).thenReturn(Optional.of(headphones(1)));

        assertThatThrownBy(() -> orderService.place(form("seed-headphones", 5)))
                .isInstanceOf(OrderPlacementException.class)
                .hasMessageContaining("Not enough stock");
        verify(orderRepository, never()).save(any());
        verify(productRepository, never()).save(any());
    }

    private static Product headphones(int stock) {
        return new Product(
                "seed-headphones",
                "Wireless Headphones",
                "Noise-cancelling over-ear headphones",
                "Electronics",
                new BigDecimal("199.99"),
                stock
        );
    }

    private static OrderForm form(String productId, int quantity) {
        OrderForm form = new OrderForm();
        form.setCustomerEmail("buyer@example.com");
        form.setProductId(productId);
        form.setQuantity(quantity);
        return form;
    }
}
