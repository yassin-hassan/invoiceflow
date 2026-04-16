package com.example.invoiceflow.product;

import com.example.invoiceflow.exception.ResourceNotFoundException;
import com.example.invoiceflow.product.dto.CreateProductRequest;
import com.example.invoiceflow.product.dto.UpdateProductRequest;
import com.example.invoiceflow.user.User;
import com.example.invoiceflow.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final UserService userService;

    public List<Product> getProducts(String email) {
        User user = userService.getByEmail(email);
        return productRepository.findByUserAndIsActiveTrue(user);
    }

    public Product getProduct(String email, UUID productId) {
        User user = userService.getByEmail(email);
        return productRepository.findByIdAndUser(productId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    @Transactional
    public Product createProduct(String email, CreateProductRequest request) {
        User user = userService.getByEmail(email);

        if (productRepository.existsByUserAndReference(user, request.getReference())) {
            throw new IllegalArgumentException("A product with this reference already exists");
        }

        Product product = new Product();
        product.setUser(user);
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setReference(request.getReference());
        product.setUnitPrice(request.getUnitPrice());
        product.setVatRate(request.getVatRate());
        product.setUnit(request.getUnit());

        return productRepository.save(product);
    }

    @Transactional
    public Product updateProduct(String email, UUID productId, UpdateProductRequest request) {
        User user = userService.getByEmail(email);
        Product product = productRepository.findByIdAndUser(productId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        if (request.getReference() != null &&
                productRepository.existsByIdNotAndUserAndReference(productId, user, request.getReference())) {
            throw new IllegalArgumentException("A product with this reference already exists");
        }

        if (request.getName() != null) product.setName(request.getName());
        if (request.getDescription() != null) product.setDescription(request.getDescription());
        if (request.getReference() != null) product.setReference(request.getReference());
        if (request.getUnitPrice() != null) product.setUnitPrice(request.getUnitPrice());
        if (request.getVatRate() != null) product.setVatRate(request.getVatRate());
        if (request.getUnit() != null) product.setUnit(request.getUnit());

        return productRepository.save(product);
    }

    @Transactional
    public void deleteProduct(String email, UUID productId) {
        User user = userService.getByEmail(email);
        Product product = productRepository.findByIdAndUser(productId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        // TODO: check if product is used in quotes or invoices — soft delete if so, hard delete if not
        // For now, always soft delete
        product.setActive(false);
        productRepository.save(product);
    }
}
