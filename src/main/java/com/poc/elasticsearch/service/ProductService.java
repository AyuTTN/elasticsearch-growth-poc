package com.poc.elasticsearch.service;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.poc.elasticsearch.dto.CreateProductRequest;
import com.poc.elasticsearch.dto.ProductResponse;
import com.poc.elasticsearch.exception.ResourceNotFoundException;
import com.poc.elasticsearch.model.Product;
import com.poc.elasticsearch.repository.ProductRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ElasticsearchOperations elasticsearchOperations;

    public ProductService(ProductRepository productRepository, ElasticsearchOperations elasticsearchOperations) {
        this.productRepository = productRepository;
        this.elasticsearchOperations = elasticsearchOperations;
    }

    public ProductResponse createProduct(CreateProductRequest request) {
        Product product = new Product();
        product.setName(request.name());
        product.setDescription(request.description());
        product.setCategory(request.category());
        product.setPrice(request.price());
        product.setStock(request.stock());
        product.setCreatedAt(Instant.now());

        Product saved = productRepository.save(product);
        return ProductResponse.from(saved);
    }

    public ProductResponse getProduct(String id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        return ProductResponse.from(product);
    }

    public List<ProductResponse> searchProducts(String query, String category) {
        BoolQuery.Builder boolQuery = new BoolQuery.Builder();

        if (StringUtils.hasText(query)) {
            boolQuery.must(Query.of(q -> q.multiMatch(mm -> mm
                    .fields("name", "description")
                    .query(query)
            )));
        }

        if (StringUtils.hasText(category)) {
            boolQuery.filter(Query.of(q -> q.term(t -> t
                    .field("category")
                    .value(category)
            )));
        }

        if (!StringUtils.hasText(query) && !StringUtils.hasText(category)) {
            boolQuery.must(Query.of(q -> q.matchAll(ma -> ma)));
        }

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(Query.of(q -> q.bool(boolQuery.build())))
                .withPageable(PageRequest.of(0, 50))
                .build();

        return elasticsearchOperations.search(nativeQuery, Product.class)
                .stream()
                .map(SearchHit::getContent)
                .map(ProductResponse::from)
                .toList();
    }

    Product getProductEntity(String id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

    void saveProduct(Product product) {
        productRepository.save(product);
    }
}
