package com.poc.elasticsearch.service;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import com.poc.elasticsearch.exception.OrderPlacementException;
import com.poc.elasticsearch.model.Order;
import com.poc.elasticsearch.model.OrderItem;
import com.poc.elasticsearch.model.OrderStatus;
import com.poc.elasticsearch.model.Product;
import com.poc.elasticsearch.repository.OrderRepository;
import com.poc.elasticsearch.repository.ProductRepository;
import com.poc.elasticsearch.web.OrderForm;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ElasticsearchOperations elasticsearchOperations;

    public OrderService(
            OrderRepository orderRepository,
            ProductRepository productRepository,
            ElasticsearchOperations elasticsearchOperations
    ) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.elasticsearchOperations = elasticsearchOperations;
    }

    public List<Order> findAll() {
        return StreamSupport.stream(orderRepository.findAll().spliterator(), false)
                .sorted(Comparator.comparing(Order::createdAt).reversed())
                .toList();
    }

    public List<Order> search(String customerEmail, String status) {
        boolean hasEmail = StringUtils.hasText(customerEmail);
        boolean hasStatus = StringUtils.hasText(status);

        if (!hasEmail && !hasStatus) {
            return findAll();
        }

        BoolQuery.Builder boolQuery = new BoolQuery.Builder();

        if (hasEmail) {
            boolQuery.filter(query -> query.term(term -> term
                    .field("customerEmail")
                    .value(customerEmail.trim())
            ));
        }

        if (hasStatus) {
            boolQuery.filter(query -> query.term(term -> term
                    .field("status")
                    .value(status.trim())
            ));
        }

        NativeQuery query = NativeQuery.builder()
                .withQuery(boolQuery.build()._toQuery())
                .withPageable(PageRequest.of(0, 100))
                .build();

        return elasticsearchOperations.search(query, Order.class).stream()
                .map(SearchHit::getContent)
                .toList();
    }

    public Order place(OrderForm form) {
        Product product = productRepository.findById(form.getProductId())
                .orElseThrow(() -> new OrderPlacementException("Product not found."));

        int quantity = form.getQuantity();
        if (product.stock() < quantity) {
            throw new OrderPlacementException(
                    "Not enough stock for " + product.name() + ". Available: " + product.stock() + "."
            );
        }

        productRepository.save(product.withStock(product.stock() - quantity));

        BigDecimal total = product.price().multiply(BigDecimal.valueOf(quantity));
        OrderItem item = new OrderItem(product.id(), product.name(), quantity, product.price());
        Order order = new Order(
                UUID.randomUUID().toString(),
                form.getCustomerEmail().trim(),
                OrderStatus.PENDING.name(),
                List.of(item),
                total,
                Instant.now()
        );
        return orderRepository.save(order);
    }
}
