
package com.example.nail_salon_appt_scheduler;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UserAccount> findByUsername(String username) {

        String sql = """
                SELECT user_id, name, username, password_hash, role
                FROM users
                WHERE username = ?
                """;

        List<UserAccount> users = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new UserAccount(
                        rs.getLong("user_id"),
                        rs.getString("name"),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("role")
                ),
                username
        );

        return users.stream().findFirst();
    }


    public Optional<UserAccount> findById(Long userId) {

        String sql = """
                SELECT user_id, name, username, password_hash, role
                FROM users
                WHERE user_id = ?
                """;

        List<UserAccount> users = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new UserAccount(
                        rs.getLong("user_id"),
                        rs.getString("name"),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("role")
                ),
                userId
        );

        return users.stream().findFirst();
    }

}

