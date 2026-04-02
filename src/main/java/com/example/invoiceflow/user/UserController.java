package com.example.invoiceflow.user;

import com.example.invoiceflow.user.dto.CreateUserRequest;
import com.example.invoiceflow.user.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        User created = userService.createUser(request);
        UserResponse response = new UserResponse();
        response.setId(created.getId());
        response.setEmail(created.getEmail());
        response.setFirstName(created.getFirstName());
        response.setLastName(created.getLastName());
        response.setCreatedAt(created.getCreatedAt());
        return ResponseEntity.status(201).body(response);
    }
}
