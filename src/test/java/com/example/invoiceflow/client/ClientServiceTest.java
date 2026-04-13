package com.example.invoiceflow.client;

import com.example.invoiceflow.client.dto.CreateClientRequest;
import com.example.invoiceflow.client.dto.UpdateClientRequest;
import com.example.invoiceflow.exception.ResourceNotFoundException;
import com.example.invoiceflow.user.User;
import com.example.invoiceflow.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock private ClientRepository clientRepository;
    @Mock private UserService userService;

    @InjectMocks
    private ClientService clientService;

    private User user;
    private Client client;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");

        client = new Client();
        client.setId(UUID.randomUUID());
        client.setUser(user);
        client.setName("Acme Corp");
        client.setEmail("acme@example.com");
        client.setActive(true);
    }

    // --- getClients ---

    @Test
    void getClients_returnsActiveClientsForUser() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(clientRepository.findByUserAndIsActiveTrue(user)).thenReturn(List.of(client));

        List<Client> result = clientService.getClients("user@example.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Acme Corp");
    }

    // --- getClient ---

    @Test
    void getClient_existingClient_returnsClient() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(clientRepository.findByIdAndUser(client.getId(), user)).thenReturn(Optional.of(client));

        Client result = clientService.getClient("user@example.com", client.getId());

        assertThat(result.getName()).isEqualTo("Acme Corp");
    }

    @Test
    void getClient_notFound_throwsResourceNotFoundException() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(clientRepository.findByIdAndUser(any(), eq(user))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.getClient("user@example.com", UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- createClient ---

    @Test
    void createClient_validRequest_savesAndReturnsClient() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(clientRepository.findByUserAndEmail(user, "new@client.com")).thenReturn(java.util.Optional.empty());
        when(clientRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateClientRequest request = new CreateClientRequest();
        request.setName("New Client");
        request.setEmail("new@client.com");

        Client result = clientService.createClient("user@example.com", request);

        assertThat(result.getName()).isEqualTo("New Client");
        assertThat(result.getEmail()).isEqualTo("new@client.com");
        assertThat(result.getUser()).isEqualTo(user);
        verify(clientRepository).save(any());
    }

    @Test
    void createClient_duplicateEmail_throwsIllegalArgumentException() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(clientRepository.findByUserAndEmail(user, "acme@example.com")).thenReturn(java.util.Optional.of(client));

        CreateClientRequest request = new CreateClientRequest();
        request.setName("Acme Corp");
        request.setEmail("acme@example.com");

        assertThatThrownBy(() -> clientService.createClient("user@example.com", request))
                .isInstanceOf(IllegalArgumentException.class);

        verify(clientRepository, never()).save(any());
    }

    @Test
    void createClient_emailNormalized() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(clientRepository.findByUserAndEmail(user, "new@client.com")).thenReturn(java.util.Optional.empty());
        when(clientRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateClientRequest request = new CreateClientRequest();
        request.setName("New Client");
        request.setEmail("  NEW@CLIENT.COM  ");

        Client result = clientService.createClient("user@example.com", request);

        assertThat(result.getEmail()).isEqualTo("new@client.com");
    }

    // --- updateClient ---

    @Test
    void updateClient_nullFields_areNotOverwritten() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(clientRepository.findByIdAndUser(client.getId(), user)).thenReturn(Optional.of(client));
        when(clientRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateClientRequest request = new UpdateClientRequest();
        // all fields null — nothing should change

        Client result = clientService.updateClient("user@example.com", client.getId(), request);

        assertThat(result.getName()).isEqualTo("Acme Corp");
        assertThat(result.getEmail()).isEqualTo("acme@example.com");
    }

    @Test
    void updateClient_notFound_throwsResourceNotFoundException() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(clientRepository.findByIdAndUser(any(), eq(user))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.updateClient("user@example.com", UUID.randomUUID(), new UpdateClientRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateClient_duplicateEmail_throwsIllegalArgumentException() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(clientRepository.findByIdAndUser(client.getId(), user)).thenReturn(Optional.of(client));
        when(clientRepository.existsByIdNotAndUserAndEmailAndIsActiveTrue(client.getId(), user, "taken@example.com")).thenReturn(true);

        UpdateClientRequest request = new UpdateClientRequest();
        request.setEmail("taken@example.com");

        assertThatThrownBy(() -> clientService.updateClient("user@example.com", client.getId(), request))
                .isInstanceOf(IllegalArgumentException.class);

        verify(clientRepository, never()).save(any());
    }

    // --- deleteClient ---

    @Test
    void deleteClient_softDeletesClient() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(clientRepository.findByIdAndUser(client.getId(), user)).thenReturn(Optional.of(client));
        when(clientRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        clientService.deleteClient("user@example.com", client.getId());

        verify(clientRepository).save(argThat(c -> !c.isActive()));
    }

    @Test
    void deleteClient_notFound_throwsResourceNotFoundException() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(clientRepository.findByIdAndUser(any(), eq(user))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.deleteClient("user@example.com", UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
