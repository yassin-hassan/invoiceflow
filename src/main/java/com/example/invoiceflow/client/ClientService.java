package com.example.invoiceflow.client;

import com.example.invoiceflow.client.dto.CreateClientRequest;
import com.example.invoiceflow.client.dto.UpdateClientRequest;
import com.example.invoiceflow.exception.ResourceNotFoundException;
import com.example.invoiceflow.user.Address;
import com.example.invoiceflow.user.User;
import com.example.invoiceflow.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;
    private final UserService userService;

    public List<Client> getClients(String email) {
        User user = userService.getByEmail(email);
        return clientRepository.findByUserAndIsActiveTrue(user);
    }

    public Client getClient(String email, UUID clientId) {
        User user = userService.getByEmail(email);
        return clientRepository.findByIdAndUser(clientId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
    }

    @Transactional
    public Client createClient(String email, CreateClientRequest request) {
        User user = userService.getByEmail(email);

        String clientEmail = request.getEmail().toLowerCase().trim();

        if (clientRepository.findByUserAndEmail(user, clientEmail).isPresent()) {
            throw new IllegalArgumentException("A client with this email already exists or has been archived");
        }

        Client client = new Client();
        client.setUser(user);
        client.setName(request.getName());
        client.setEmail(clientEmail);
        client.setPhone(request.getPhone());
        client.setVatNumber(request.getVatNumber());
        client.setNotes(request.getNotes());

        if (request.getBillingAddress() != null) {
            client.setBillingAddress(buildAddress(request.getBillingAddress()));
        }

        return clientRepository.save(client);
    }

    @Transactional
    public Client updateClient(String email, UUID clientId, UpdateClientRequest request) {
        User user = userService.getByEmail(email);
        Client client = clientRepository.findByIdAndUser(clientId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found"));

        if (request.getEmail() != null) {
            String newEmail = request.getEmail().toLowerCase().trim();
            if (clientRepository.existsByIdNotAndUserAndEmailAndIsActiveTrue(clientId, user, newEmail)) {
                throw new IllegalArgumentException("A client with this email already exists");
            }
            client.setEmail(newEmail);
        }

        if (request.getName() != null) client.setName(request.getName());
        if (request.getPhone() != null) client.setPhone(request.getPhone());
        if (request.getVatNumber() != null) client.setVatNumber(request.getVatNumber());
        if (request.getNotes() != null) client.setNotes(request.getNotes());

        if (request.getBillingAddress() != null) {
            UpdateClientRequest.AddressRequest addrReq = request.getBillingAddress();
            Address address = client.getBillingAddress() != null ? client.getBillingAddress() : new Address();
            if (addrReq.getStreet() != null) address.setStreet(addrReq.getStreet());
            if (addrReq.getPostalCode() != null) address.setPostalCode(addrReq.getPostalCode());
            if (addrReq.getCity() != null) address.setCity(addrReq.getCity());
            if (addrReq.getCountry() != null) address.setCountry(addrReq.getCountry());
            client.setBillingAddress(address);
        }

        return clientRepository.save(client);
    }

    @Transactional
    public void deleteClient(String email, UUID clientId) {
        User user = userService.getByEmail(email);
        Client client = clientRepository.findByIdAndUser(clientId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found"));

        // TODO: check if client has quotes or invoices — soft delete if so, hard delete if not
        // For now, always soft delete
        client.setActive(false);
        clientRepository.save(client);
    }

    private Address buildAddress(CreateClientRequest.AddressRequest req) {
        Address address = new Address();
        address.setStreet(req.getStreet());
        address.setPostalCode(req.getPostalCode());
        address.setCity(req.getCity());
        address.setCountry(req.getCountry());
        return address;
    }
}
