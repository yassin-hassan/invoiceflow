package com.example.invoiceflow.user;

import com.example.invoiceflow.auth.dto.Disable2faRequest;
import com.example.invoiceflow.auth.dto.Enable2faRequest;
import com.example.invoiceflow.user.dto.ChangeLanguageRequest;
import com.example.invoiceflow.user.dto.ChangePasswordRequest;
import com.example.invoiceflow.user.dto.CreateUserRequest;
import com.example.invoiceflow.user.dto.UpdateProfileRequest;
import com.example.invoiceflow.user.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        User created = userService.createUser(request);
        return ResponseEntity.status(201).body(userMapper.toResponse(created));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(@AuthenticationPrincipal UserDetails principal) {
        User user = userService.getByEmail(principal.getUsername());
        return ResponseEntity.ok(userMapper.toResponse(user));
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateMe(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        User updated = userService.updateProfile(principal.getUsername(), request);
        return ResponseEntity.ok(userMapper.toResponse(updated));
    }

    @PatchMapping("/me/language")
    public ResponseEntity<Void> changeLanguage(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody ChangeLanguageRequest request) {
        userService.changeLanguage(principal.getUsername(), request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(principal.getUsername(), request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/me/2fa/enable")
    public ResponseEntity<Void> enable2fa(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody Enable2faRequest request) {
        userService.enable2fa(principal.getUsername(), request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me/2fa")
    public ResponseEntity<Void> disable2fa(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody Disable2faRequest request) {
        userService.disable2fa(principal.getUsername(), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/me/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserResponse> uploadLogo(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam("file") MultipartFile file) {
        User updated = userService.updateLogo(principal.getUsername(), file);
        return ResponseEntity.ok(userMapper.toResponse(updated));
    }
}
