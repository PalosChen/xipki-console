package org.xipki.mgmt.web.db;

import org.xipki.mgmt.web.core.*;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import java.util.Set;
import java.util.Map;

public final class WebDatabaseHealth {
    static final Set<String> V1_TABLES = Set.of("web_schema_version", "web_user", "web_role", "web_permission",
            "web_user_role", "web_role_permission", "web_audit_event", "spring_session", "spring_session_attributes");
    static final Set<String> V4_TABLES;
    static final Set<String> TABLES;
    static {
        var tables = new java.util.HashSet<>(V1_TABLES);
        tables.addAll(Set.of("web_schema_migration", "web_authorization_lock", "web_role_binding", "web_binding_profile",
                "web_certificate_owner", "web_login_throttle"));
        V4_TABLES = Set.copyOf(tables);
        tables.add("web_ca_enrollment");
        TABLES = Set.copyOf(tables);
    }
    private final Jdbc jdbcTemplate;
    public WebDatabaseHealth(Jdbc jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }
    public boolean isReady() {
        try {
            return Boolean.TRUE.equals(jdbcTemplate.connection(connection -> {
                verify(connection);
                return true;
            }));
        } catch (Exception ex) {
            return false;
        }
    }

    static void verify(Connection connection) throws SQLException {
        verify(connection, 5);
        try(var statement=connection.createStatement()) {
            statement.setQueryTimeout(5);
            try(var ignored=statement.executeQuery("SELECT token_hash,user_id,auth_version,csrf_token,created_ms,last_access_ms,recent_auth_ms FROM lite_session WHERE 1=0")) { }
        }
    }
    static void verifyV4(Connection connection) throws SQLException { verify(connection,4); }
    static void verifyV3(Connection connection) throws SQLException { verify(connection,3); }
    static void verifyV2(Connection connection) throws SQLException { verify(connection,2); }
    static void verifyV1(Connection connection) throws SQLException { verify(connection, 1); }
    private static void verify(Connection connection, int version) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(5);
            try (ResultSet result = statement.executeQuery("SELECT COUNT(*), MIN(version), MAX(version) FROM web_schema_version")) {
                if (!result.next() || result.getInt(1) != 1 || result.getInt(2) != version || result.getInt(3) != version) {
                    throw new SQLException("Unsupported Web schema version");
                }
            }
            Map<String, String> columns = Map.of(
                    "web_user", "id,username,password_hash,display_name,enabled,created_at,updated_at,last_login_at",
                    "web_role", "id,name,description", "web_permission", "id,code,description",
                    "web_user_role", "user_id,role_id", "web_role_permission", "role_id,permission_id",
                    "web_audit_event", "id,event_time,actor_user_id,actor_username,source_ip,request_id,http_method,request_path,resource_type,resource_id,action,result,failure_category,duration_ms",
                    "SPRING_SESSION", "PRIMARY_ID,SESSION_ID,CREATION_TIME,LAST_ACCESS_TIME,MAX_INACTIVE_INTERVAL,EXPIRY_TIME,PRINCIPAL_NAME",
                    "SPRING_SESSION_ATTRIBUTES", "SESSION_PRIMARY_ID,ATTRIBUTE_NAME,ATTRIBUTE_BYTES");
            for (var entry : columns.entrySet()) {
                try (ResultSet ignored = statement.executeQuery("SELECT " + entry.getValue() + " FROM " + entry.getKey() + " WHERE 1=0")) {
                    // Validate all columns consumed by this application without changing existing data.
                }
            }
            if (version >= 2) {
                Map<String,String> extra = Map.of(
                        "web_user", "auth_version,password_change_required,reset_allowed_at",
                        "web_schema_migration", "version,stage", "web_authorization_lock", "id",
                        "web_role_binding", "id,user_id,role_name,scope_type,ca_name,ca_fingerprint,all_profiles",
                        "web_binding_profile", "binding_id,profile_name",
                        "web_certificate_owner", "ca_name,ca_fingerprint,serial_number,user_id,profile_name,issued_at",
                        "web_login_throttle", "throttle_key,attempts,window_start", "web_audit_event", "details_json");
                for (var entry : extra.entrySet()) {
                    try (ResultSet ignored = statement.executeQuery("SELECT " + entry.getValue() + " FROM " + entry.getKey() + " WHERE 1=0")) { }
                }
                try (var result = statement.executeQuery("SELECT stage FROM web_schema_migration WHERE version=2")) {
                    if (!result.next() || result.getInt(1) != 2) throw new SQLException("Incomplete migration");
                }
            }
            if(version>=3) {
                try(var ignored=statement.executeQuery("SELECT display_name,role_type,builtin,enabled,deleted,revision FROM web_role WHERE 1=0")) { }
                try(var result=statement.executeQuery("SELECT stage FROM web_schema_migration WHERE version=3")) {
                    if(!result.next() || result.getInt(1)!=2) throw new SQLException("Incomplete role migration");
                }
            }
            if(version>=4) {
                try(var ignored=statement.executeQuery("SELECT migration_source,migration_generated FROM web_role WHERE 1=0")) { }
                try(var ignored=statement.executeQuery("SELECT permission_codes FROM web_audit_event WHERE 1=0")) { }
                try(var result=statement.executeQuery("SELECT stage FROM web_schema_migration WHERE version=4")) {
                    if(!result.next() || result.getInt(1)!=2) throw new SQLException("Incomplete permission migration");
                }
            }
            if(version>=5) {
                try(var ignored=statement.executeQuery("SELECT operation_id,owner_id,idempotency_key,request_hash,purpose,status,version,public_json,created_at,updated_at FROM web_ca_enrollment WHERE 1=0")) { }
                try(var result=statement.executeQuery("SELECT stage FROM web_schema_migration WHERE version=5")) {
                    if(!result.next() || result.getInt(1)!=2) throw new SQLException("Incomplete CA enrollment migration");
                }
            }
        }
    }
}
