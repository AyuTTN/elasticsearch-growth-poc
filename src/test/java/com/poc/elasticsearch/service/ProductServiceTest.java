package com.poc.elasticsearch.service;

import com.poc.elasticsearch.model.Product;
import com.poc.elasticsearch.repository.ProductRepository;
import com.poc.elasticsearch.web.ProductForm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private SearchHits<Product> searchHits;

    @Mock
    private SearchHit<Product> searchHit;

    @Test
    void readsProductsFromElasticsearchRepositoryInIdOrder() {
        Product later = new Product("b", "Desk", "Standing desk", "Furniture",
                new BigDecimal("349.00"), 8);
        Product earlier = new Product("a", "Beans", "Coffee beans", "Grocery",
                new BigDecimal("18.50"), 40);
        when(productRepository.findAll()).thenReturn(List.of(later, earlier));

        ProductService service = new ProductService(productRepository, elasticsearchOperations);

        assertThat(service.findAll()).containsExactly(earlier, later);
    }

    @Test
    void savesTrimmedProductWithGeneratedId() {
        ProductForm form = new ProductForm();
        form.setName("  Keyboard  ");
        form.setDescription("  Mechanical keyboard  ");
        form.setCategory("  Electronics  ");
        form.setPrice(new BigDecimal("89.99"));
        form.setStock(12);
        when(productRepository.save(org.mockito.ArgumentMatchers.any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProductService service = new ProductService(productRepository, elasticsearchOperations);
        Product saved = service.create(form);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(saved).isEqualTo(captor.getValue());
        assertThat(saved.id()).isNotBlank();
        assertThat(saved.name()).isEqualTo("Keyboard");
        assertThat(saved.description()).isEqualTo("Mechanical keyboard");
        assertThat(saved.category()).isEqualTo("Electronics");
    }

    @Test
    void buildsBoolQueryWithFullTextMatchAndCategoryFilter() {
        Product headphones = new Product(
                "headphones",
                "Wireless Headphones",
                "Noise-cancelling headphones",
                "Electronics",
                new BigDecimal("199.99"),
                25
        );
        when(searchHit.getContent()).thenReturn(headphones);
        when(searchHits.stream()).thenReturn(Stream.of(searchHit));
        when(elasticsearchOperations.search(any(NativeQuery.class), eq(Product.class)))
                .thenReturn(searchHits);

        ProductService service = new ProductService(productRepository, elasticsearchOperations);
        List<Product> result = service.search("headphones", "Electronics");

        ArgumentCaptor<NativeQuery> captor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(elasticsearchOperations).search(captor.capture(), eq(Product.class));
        assertThat(result).containsExactly(headphones);
        assertThat(captor.getValue().getQuery().isBool()).isTrue();
        assertThat(captor.getValue().getQuery().bool().must()).hasSize(1);
        assertThat(captor.getValue().getQuery().bool().must().getFirst().isMultiMatch()).isTrue();
        assertThat(captor.getValue().getQuery().bool().filter()).hasSize(1);
        assertThat(captor.getValue().getQuery().bool().filter().getFirst().isTerm()).isTrue();
    }
}
