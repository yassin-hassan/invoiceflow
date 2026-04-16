package com.example.invoiceflow.product;

import com.example.invoiceflow.exception.ResourceNotFoundException;
import com.example.invoiceflow.product.dto.CreateProductRequest;
import com.example.invoiceflow.product.dto.UpdateProductRequest;
import com.example.invoiceflow.user.User;
import com.example.invoiceflow.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private UserService userService;

    @InjectMocks
    private ProductService productService;

    private User user;
    private Product product;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");

        product = new Product();
        product.setId(UUID.randomUUID());
        product.setUser(user);
        product.setName("Widget");
        product.setReference("REF-001");
        product.setUnitPrice(new BigDecimal("99.99"));
        product.setVatRate(new BigDecimal("20.00"));
        product.setUnit("piece");
        product.setActive(true);
    }

    // --- getProducts ---

    @Test
    void getProducts_returnsActiveProductsForUser() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(productRepository.findByUserAndIsActiveTrue(user)).thenReturn(List.of(product));

        List<Product> result = productService.getProducts("user@example.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Widget");
    }

    // --- getProduct ---

    @Test
    void getProduct_existingProduct_returnsProduct() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(productRepository.findByIdAndUser(product.getId(), user)).thenReturn(Optional.of(product));

        Product result = productService.getProduct("user@example.com", product.getId());

        assertThat(result.getReference()).isEqualTo("REF-001");
    }

    @Test
    void getProduct_notFound_throwsResourceNotFoundException() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(productRepository.findByIdAndUser(any(), eq(user))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProduct("user@example.com", UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- createProduct ---

    @Test
    void createProduct_validRequest_savesAndReturnsProduct() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(productRepository.existsByUserAndReference(user, "REF-002")).thenReturn(false);
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateProductRequest request = new CreateProductRequest();
        request.setName("Gadget");
        request.setReference("REF-002");
        request.setUnitPrice(new BigDecimal("49.99"));
        request.setVatRate(new BigDecimal("20.00"));
        request.setUnit("piece");

        Product result = productService.createProduct("user@example.com", request);

        assertThat(result.getName()).isEqualTo("Gadget");
        assertThat(result.getReference()).isEqualTo("REF-002");
        assertThat(result.getUser()).isEqualTo(user);
        verify(productRepository).save(any());
    }

    @Test
    void createProduct_duplicateReference_throwsIllegalArgumentException() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(productRepository.existsByUserAndReference(user, "REF-001")).thenReturn(true);

        CreateProductRequest request = new CreateProductRequest();
        request.setName("Widget Copy");
        request.setReference("REF-001");
        request.setUnitPrice(new BigDecimal("99.99"));
        request.setVatRate(new BigDecimal("20.00"));
        request.setUnit("piece");

        assertThatThrownBy(() -> productService.createProduct("user@example.com", request))
                .isInstanceOf(IllegalArgumentException.class);

        verify(productRepository, never()).save(any());
    }

    // --- updateProduct ---

    @Test
    void updateProduct_nullFields_areNotOverwritten() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(productRepository.findByIdAndUser(product.getId(), user)).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateProductRequest request = new UpdateProductRequest();
        // all fields null — nothing should change

        Product result = productService.updateProduct("user@example.com", product.getId(), request);

        assertThat(result.getName()).isEqualTo("Widget");
        assertThat(result.getReference()).isEqualTo("REF-001");
    }

    @Test
    void updateProduct_notFound_throwsResourceNotFoundException() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(productRepository.findByIdAndUser(any(), eq(user))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.updateProduct("user@example.com", UUID.randomUUID(), new UpdateProductRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateProduct_duplicateReference_throwsIllegalArgumentException() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(productRepository.findByIdAndUser(product.getId(), user)).thenReturn(Optional.of(product));
        when(productRepository.existsByIdNotAndUserAndReference(product.getId(), user, "REF-TAKEN")).thenReturn(true);

        UpdateProductRequest request = new UpdateProductRequest();
        request.setReference("REF-TAKEN");

        assertThatThrownBy(() -> productService.updateProduct("user@example.com", product.getId(), request))
                .isInstanceOf(IllegalArgumentException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    void updateProduct_sameReference_doesNotThrow() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(productRepository.findByIdAndUser(product.getId(), user)).thenReturn(Optional.of(product));
        when(productRepository.existsByIdNotAndUserAndReference(product.getId(), user, "REF-001")).thenReturn(false);
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateProductRequest request = new UpdateProductRequest();
        request.setReference("REF-001");

        Product result = productService.updateProduct("user@example.com", product.getId(), request);

        assertThat(result.getReference()).isEqualTo("REF-001");
    }

    // --- deleteProduct ---

    @Test
    void deleteProduct_softDeletesProduct() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(productRepository.findByIdAndUser(product.getId(), user)).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        productService.deleteProduct("user@example.com", product.getId());

        verify(productRepository).save(argThat(p -> !p.isActive()));
    }

    @Test
    void deleteProduct_notFound_throwsResourceNotFoundException() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(productRepository.findByIdAndUser(any(), eq(user))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.deleteProduct("user@example.com", UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
