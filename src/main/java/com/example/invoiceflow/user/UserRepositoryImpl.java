package com.example.invoiceflow.user;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private static final String INSERT_USER =
            "INSERT INTO users (id, email, password_hash, first_name, last_name, preferred_language, created_at) " +
            "VALUES (:id, :email, :passwordHash, :firstName, :lastName, :preferredLanguage, :createdAt)";

    private static final String COUNT_BY_EMAIL =
            "SELECT COUNT(*) FROM users WHERE email = :email";

    private static final String SELECT_BY_EMAIL =
            "SELECT * FROM users WHERE email = :email";

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public User create(User user) {
        user.setId(UUID.randomUUID());
        user.setCreatedAt(LocalDateTime.now());
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", user.getId())
                .addValue("email", user.getEmail())
                .addValue("passwordHash", user.getPasswordHash())
                .addValue("firstName", user.getFirstName())
                .addValue("lastName", user.getLastName())
                .addValue("preferredLanguage", user.getPreferredLanguage())
                .addValue("createdAt", user.getCreatedAt());
        jdbc.update(INSERT_USER, params);
        return user;
    }

    @Override
    public boolean existsByEmail(String email) {
        Integer count = jdbc.queryForObject(COUNT_BY_EMAIL, new MapSqlParameterSource("email", email), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public Optional<User> findByEmail(String email) {
        try {
            User user = jdbc.queryForObject(SELECT_BY_EMAIL, new MapSqlParameterSource("email", email), new UserRowMapper());
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
