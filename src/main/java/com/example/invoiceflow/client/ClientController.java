package com.example.invoiceflow.client;

import com.example.invoiceflow.client.dto.ClientResponse;
import com.example.invoiceflow.client.dto.CreateClientRequest;
import com.example.invoiceflow.client.dto.UpdateClientRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;
    private final ClientMapper clientMapper;

    @GetMapping
    public ResponseEntity<List<ClientResponse>> getClients(
            @AuthenticationPrincipal UserDetails principal) {
        List<ClientResponse> clients = clientService.getClients(principal.getUsername())
                .stream()
                .map(clientMapper::toResponse)
                .toList();
        return ResponseEntity.ok(clients);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClientResponse> getClient(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID id) {
        Client client = clientService.getClient(principal.getUsername(), id);
        return ResponseEntity.ok(clientMapper.toResponse(client));
    }

    @PostMapping
    public ResponseEntity<ClientResponse> createClient(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody CreateClientRequest request) {
        Client created = clientService.createClient(principal.getUsername(), request);
        return ResponseEntity.status(201).body(clientMapper.toResponse(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClientResponse> updateClient(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateClientRequest request) {
        Client updated = clientService.updateClient(principal.getUsername(), id, request);
        return ResponseEntity.ok(clientMapper.toResponse(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteClient(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID id) {
        clientService.deleteClient(principal.getUsername(), id);
        return ResponseEntity.noContent().build();
    }
}
