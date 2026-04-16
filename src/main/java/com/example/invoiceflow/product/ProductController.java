package com.example.invoiceflow.product;

import com.example.invoiceflow.product.dto.CreateProductRequest;
import com.example.invoiceflow.product.dto.ProductResponse;
import com.example.invoiceflow.product.dto.UpdateProductRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductMapper productMapper;

    @GetMapping
    public ResponseEntity<List<ProductResponse>> getProducts(
            @AuthenticationPrincipal UserDetails principal) {
        List<ProductResponse> products = productService.getProducts(principal.getUsername())
                .stream()
                .map(productMapper::toResponse)
                .toList();
        return ResponseEntity.ok(products);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProduct(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID id) {
        Product product = productService.getProduct(principal.getUsername(), id);
        return ResponseEntity.ok(productMapper.toResponse(product));
    }

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody CreateProductRequest request) {
        Product created = productService.createProduct(principal.getUsername(), request);
        return ResponseEntity.status(201).body(productMapper.toResponse(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> updateProduct(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductRequest request) {
        Product updated = productService.updateProduct(principal.getUsername(), id, request);
        return ResponseEntity.ok(productMapper.toResponse(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID id) {
        productService.deleteProduct(principal.getUsername(), id);
        return ResponseEntity.noContent().build();
    }
}
