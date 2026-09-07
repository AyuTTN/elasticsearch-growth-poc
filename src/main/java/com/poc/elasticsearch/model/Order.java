package com.poc.elasticsearch.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(indexName = "orders")
public class Order {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String customerEmail;

    @Field(type = FieldType.Keyword)
    private OrderStatus status;

    @Field(type = FieldType.Nested)
    private List<OrderItem> items = new ArrayList<>();

    @Field(type = FieldType.Double)
    private BigDecimal totalAmount;

    @Field(type = FieldType.Date)
    private Instant createdAt;

    public Order() {
    }

    public Order(String id, String customerEmail, OrderStatus status, List<OrderItem> items, BigDecimal totalAmount, Instant createdAt) {
        this.id = id;
        this.customerEmail = customerEmail;
        this.status = status;
        this.items = items;
        this.totalAmount = totalAmount;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
