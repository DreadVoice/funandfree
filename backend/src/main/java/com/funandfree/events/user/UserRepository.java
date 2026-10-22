package com.funandfree.events.user;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private static final String COLUMNS = "id, firebase_uid, email, display_name, role";

    private final JdbcClient db;

    UserRepository(JdbcClient db) {
        this.db = db;
    }

    public Optional<AppUser> findByFirebaseUid(String firebaseUid) {
        return db.sql("select " + COLUMNS + " from app_user where firebase_uid = :uid")
                .param("uid", firebaseUid)
                .query(UserRepository::mapUser)
                .optional();
    }

    public Optional<AppUser> findById(long id) {
        return db.sql("select " + COLUMNS + " from app_user where id = :id")
                .param("id", id)
                .query(UserRepository::mapUser)
                .optional();
    }

    public long upsert(String firebaseUid, String email, String displayName, Role role) {
        return db.sql("""
                insert into app_user (firebase_uid, email, display_name, role)
                values (:uid, :email, :displayName, :role)
                on conflict (firebase_uid) do update
                   set email = excluded.email,
                       display_name = excluded.display_name
                returning id
                """)
                .param("uid", firebaseUid)
                .param("email", email)
                .param("displayName", displayName)
                .param("role", role.name())
                .query(Long.class)
                .single();
    }

    private static AppUser mapUser(ResultSet rs, int rowNum) throws SQLException {
        return new AppUser(
                rs.getLong("id"),
                rs.getString("firebase_uid"),
                rs.getString("email"),
                rs.getString("display_name"),
                Role.valueOf(rs.getString("role")));
    }
}
