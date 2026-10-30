package org.xipki.mgmt.web.db;

import org.xipki.mgmt.web.core.*;


import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Locale;
import org.xipki.mgmt.web.security.BuiltinRoles;

public class WebSchemaInitializer {
    private final DataSource dataSource;
    private final String username, password;

    public WebSchemaInitializer(DataSource dataSource, String username, String password) {
        this.dataSource = dataSource;
        this.username = username; this.password = password;
    }

    public void ensureCurrent() {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement lock = connection.prepareStatement("SELECT GET_LOCK('xipki_web_schema', 30)")) {
                try (ResultSet result = lock.executeQuery()) {
                    if (!result.next() || result.getInt(1) != 1) throw new IllegalStateException("Could not lock Web schema");
                }
            }
            try {
                Set<String> tables = new HashSet<>();
                try (ResultSet result = connection.getMetaData().getTables(connection.getCatalog(), null, "%",
                    new String[]{"TABLE", "BASE TABLE", "VIEW"})) {
                    while (result.next()) tables.add(result.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
                }
                tables.remove("lite_session");
                boolean fresh = tables.isEmpty();
                if (fresh) {
                    SqlScripts.populate(connection,"db/web-schema.sql");
                    // Persist the bootstrap policy with the seed transaction. A restart
                    // after v1 is committed must not lose the forced-password requirement.
                    execute(connection, "ALTER TABLE web_user ADD COLUMN password_change_required BOOLEAN NOT NULL DEFAULT FALSE");
                    execute(connection, "CREATE TABLE web_schema_migration (version INT NOT NULL PRIMARY KEY, stage INT NOT NULL)");
                    // MariaDB DDL commits implicitly; seed and version are committed together afterwards.
                    seed(connection);
                    execute(connection,"INSERT INTO web_schema_migration(version,stage) VALUES (2,0),(4,1)");
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO web_schema_version(version) VALUES (1)")) { statement.executeUpdate(); }
                }
                connection.commit();
                if (fresh) {tables = new HashSet<>(WebDatabaseHealth.V1_TABLES);tables.add("web_schema_migration");}
                if (!tables.contains("web_schema_version")) throw new IllegalStateException("Unknown Web database");
                int version;
                try (var statement = connection.prepareStatement("SELECT version FROM web_schema_version");
                     var result = statement.executeQuery()) {
                    if (!result.next()) throw new IllegalStateException("Missing schema version");
                    version = result.getInt(1);
                    if (result.next()) throw new IllegalStateException("Multiple schema versions");
                }
                if (version == 1) {
                    migrateV2(connection, tables);
                    connection.commit();
                    version=2;
                    tables=WebDatabaseHealth.V4_TABLES;
                }
                var expectedTables=version==5?WebDatabaseHealth.TABLES:WebDatabaseHealth.V4_TABLES;
                var resumingV5=new HashSet<>(WebDatabaseHealth.V4_TABLES); resumingV5.add("web_ca_enrollment");
                if ((version<2 || version>5) || (!tables.equals(expectedTables) && !(version==4 && tables.equals(resumingV5))))
                    throw new IllegalStateException("Unknown or incomplete Web schema");
                if (version==2) {migrateV3(connection);connection.commit();version=3;}
                if (version==3) {PermissionMigration.migrate(connection,fresh);connection.commit();version=4;}
                if (version==4) {migrateV5(connection);connection.commit();}
                if (fresh) execute(connection, "UPDATE web_user SET password_change_required=TRUE");
                SqlScripts.populate(connection,"db/lite-session.sql");
                WebDatabaseHealth.verify(connection);
                connection.commit();
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                try (PreparedStatement unlock = connection.prepareStatement("SELECT RELEASE_LOCK('xipki_web_schema')")) {
                    unlock.execute();
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Web database initialization failed", ex);
        }
    }

    private void migrateV5(Connection connection) throws Exception {
        WebDatabaseHealth.verifyV4(connection);
        execute(connection,"INSERT IGNORE INTO web_schema_migration(version,stage) VALUES (5,0)");
        connection.commit();
        SqlScripts.populate(connection,"db/web-schema-v5.sql");
        execute(connection,"INSERT IGNORE INTO web_permission(code,description) VALUES ('CA_ISSUE_SUBCA','CA_ISSUE_SUBCA')");
        execute(connection,"INSERT IGNORE INTO web_role_permission(role_id,permission_id) SELECT r.id,p.id FROM web_role r,web_permission p WHERE r.name='SUPER_ADMIN' AND p.code='CA_ISSUE_SUBCA'");
        execute(connection,"UPDATE web_user u SET auth_version=auth_version+1 WHERE EXISTS (SELECT 1 FROM web_role_binding b WHERE b.user_id=u.id AND b.role_name='SUPER_ADMIN')");
        execute(connection,"UPDATE web_schema_migration SET stage=2 WHERE version=5");
        execute(connection,"UPDATE web_schema_version SET version=5 WHERE version=4");
    }

    private void migrateV3(Connection connection) throws Exception {
        WebDatabaseHealth.verifyV2(connection);
        execute(connection,"INSERT IGNORE INTO web_schema_migration(version,stage) VALUES (3,0)");
        connection.commit();
        try(var statement=connection.prepareStatement("SELECT stage FROM web_schema_migration WHERE version=3");var result=statement.executeQuery()) {
            if(!result.next() || result.getInt(1)!=0) throw new IllegalStateException("Unsupported role migration stage");
        }
        SqlScripts.populate(connection,"db/web-schema-v3.sql");
        for(var role:BuiltinRoles.roles().entrySet()) {
            execute(connection,"UPDATE web_role SET display_name=name,role_type=?,builtin=TRUE,enabled=TRUE,deleted=FALSE,revision=1 WHERE name=?",
                    BuiltinRoles.PLATFORM_ROLES.contains(role.getKey())?"PLATFORM":"CA",role.getKey());
            // v2 runtime ignored this table: initialize exactly its actual effective grants once.
            execute(connection,"DELETE rp FROM web_role_permission rp JOIN web_role r ON r.id=rp.role_id WHERE r.name=?",role.getKey());
            for(String permission:role.getValue()) {
                execute(connection,"INSERT IGNORE INTO web_permission(code,description) VALUES (?,?)",permission,permission);
                execute(connection,"INSERT INTO web_role_permission(role_id,permission_id) SELECT r.id,p.id FROM web_role r,web_permission p WHERE r.name=? AND p.code=?",role.getKey(),permission);
            }
        }
        execute(connection,"UPDATE web_user SET auth_version=auth_version+1");
        execute(connection,"DELETE FROM SPRING_SESSION");
        execute(connection,"UPDATE web_schema_migration SET stage=2 WHERE version=3");
        execute(connection,"UPDATE web_schema_version SET version=3 WHERE version=2");
    }

    private void migrateV2(Connection connection, Set<String> tables) throws Exception {
        boolean resuming = tables.contains("web_schema_migration");
        if (!resuming && !tables.equals(WebDatabaseHealth.V1_TABLES))
            throw new IllegalStateException("Unknown or incomplete v1 schema");
        if (resuming && (!WebDatabaseHealth.TABLES.containsAll(tables) || !tables.containsAll(WebDatabaseHealth.V1_TABLES)))
            throw new IllegalStateException("Unknown migration schema");
        WebDatabaseHealth.verifyV1(connection);
        if (!resuming) {
            execute(connection, "CREATE TABLE web_schema_migration (version INT NOT NULL PRIMARY KEY, stage INT NOT NULL)");
            execute(connection, "INSERT INTO web_schema_migration(version,stage) VALUES (2,0)");
            connection.commit();
        } else {
            try (var statement = connection.prepareStatement("SELECT version,stage FROM web_schema_migration WHERE version<>4 OR stage<>1");
                 var result = statement.executeQuery()) {
                if (!result.next()) {
                    // CREATE TABLE commits implicitly. A crash before the marker INSERT
                    // leaves exactly v1 plus this empty table; no v2 DDL has run yet.
                    var markerOnly = new HashSet<>(WebDatabaseHealth.V1_TABLES);
                    markerOnly.add("web_schema_migration");
                    if (!tables.equals(markerOnly)) throw new IllegalStateException("Missing migration stage");
                    execute(connection, "INSERT INTO web_schema_migration(version,stage) VALUES (2,0)");
                    connection.commit();
                } else if (result.getInt(1) != 2 || result.getInt(2) != 0 || result.next())
                    throw new IllegalStateException("Unsupported migration stage");
            }
        }
        SqlScripts.populate(connection,"db/web-schema-v2.sql");
        for (var role : BuiltinRoles.roles().entrySet()) {
            execute(connection, "INSERT IGNORE INTO web_role(name,description) VALUES (?,?)", role.getKey(), role.getKey());
            for (String permission : role.getValue()) {
                execute(connection, "INSERT IGNORE INTO web_permission(code,description) VALUES (?,?)", permission, permission);
                execute(connection, "INSERT IGNORE INTO web_role_permission(role_id,permission_id) SELECT r.id,p.id FROM web_role r,web_permission p WHERE r.name=? AND p.code=?", role.getKey(), permission);
            }
        }
        execute(connection, """
                INSERT INTO web_role_binding(user_id,role_name,scope_type)
                SELECT DISTINCT u.id,'SUPER_ADMIN','PLATFORM' FROM web_user u
                JOIN web_user_role ur ON ur.user_id=u.id JOIN web_role r ON r.id=ur.role_id
                WHERE r.name='ADMIN' AND NOT EXISTS
                  (SELECT 1 FROM web_role_binding b WHERE b.user_id=u.id AND b.role_name='SUPER_ADMIN')
                """);
        execute(connection, "DELETE FROM web_user_role");
        execute(connection, "UPDATE web_user SET auth_version=auth_version+1");
        execute(connection, "DELETE FROM SPRING_SESSION");
        execute(connection, "UPDATE web_schema_migration SET stage=2 WHERE version=2");
        execute(connection, "UPDATE web_schema_version SET version=2 WHERE version=1");
    }

    private void seed(Connection connection) throws Exception {
                Password encoder = new Password();
        execute(connection, "INSERT IGNORE INTO web_role(name, description) VALUES (?, ?)", "ADMIN", "Platform administrator");
        execute(connection, "INSERT IGNORE INTO web_role(name, description) VALUES (?, ?)", "OPERATOR", "CA operator");
        List<String> permissions = List.of("AUDIT_READ", "CA_READ", "CERT_READ", "CERT_ISSUE", "CERT_REVOKE", "CA_CONFIG", "PLATFORM_ADMIN");
        for (String permission : permissions) execute(connection,
                "INSERT IGNORE INTO web_permission(code, description) VALUES (?, ?)", permission, permission);
        execute(connection, "INSERT IGNORE INTO web_user(username, password_hash, display_name,password_change_required) VALUES (?, ?, ?,TRUE)",
                username, encoder.encode(password), username);
        execute(connection, "INSERT IGNORE INTO web_user_role(user_id, role_id) SELECT u.id, r.id FROM web_user u, web_role r WHERE u.username=? AND r.name='ADMIN'", username);
        execute(connection, "INSERT IGNORE INTO web_role_permission(role_id, permission_id) SELECT r.id, p.id FROM web_role r, web_permission p WHERE r.name='ADMIN'");
        execute(connection, "INSERT IGNORE INTO web_role_permission(role_id, permission_id) SELECT r.id, p.id FROM web_role r, web_permission p WHERE r.name='OPERATOR' AND p.code IN ('AUDIT_READ','CA_READ','CERT_READ','CERT_ISSUE','CERT_REVOKE')");
    }

    private static void execute(Connection connection, String sql, Object... args) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (args != null) for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            statement.executeUpdate();
        }
    }
}
