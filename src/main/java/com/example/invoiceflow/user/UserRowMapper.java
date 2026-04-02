package com.example.invoiceflow.user;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class UserRowMapper implements RowMapper<User> {

    @Override
    public User mapRow(ResultSet rs, int rowNum) throws SQLException {
        User user = new User();
        user.setId(rs.getObject("id", UUID.class));
        user.setEmail(rs.getString("email"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setFirstName(rs.getString("first_name"));
        user.setLastName(rs.getString("last_name"));
        user.setCompanyName(rs.getString("company_name"));
        user.setPhone(rs.getString("phone"));
        user.setVatNumber(rs.getString("vat_number"));
        user.setLogoUrl(rs.getString("logo_url"));
        user.setPreferredLanguage(rs.getString("preferred_language"));
        user.setActive(rs.getBoolean("is_active"));
        user.setEmailVerified(rs.getBoolean("is_email_verified"));
        user.set2faEnabled(rs.getBoolean("is_2fa_enabled"));
        user.setTwoFaPhone(rs.getString("two_fa_phone"));
        user.setFailedAttempts(rs.getInt("failed_attempts"));
        user.setLockedUntil(rs.getTimestamp("locked_until") != null ? rs.getTimestamp("locked_until").toLocalDateTime() : null);
        user.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        user.setLastLoginAt(rs.getTimestamp("last_login_at") != null ? rs.getTimestamp("last_login_at").toLocalDateTime() : null);
        return user;
    }
}
