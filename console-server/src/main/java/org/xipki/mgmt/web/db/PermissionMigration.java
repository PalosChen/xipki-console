package org.xipki.mgmt.web.db;

import org.xipki.mgmt.web.core.*;

import org.xipki.mgmt.web.security.*;
import java.sql.*;
import java.util.*;

/** Once-only v3→v4 conversion under the schema lock. DDL is resumable, all grant changes atomic. */
final class PermissionMigration {
    private record Role(long id,String name,String scope,boolean enabled,boolean deleted,Set<String> old) { }
    private static final List<String> RESOURCES=List.of("SIGNER","REQUESTOR","PUBLISHER","PROFILE","KEYPAIRGEN");
    private static final Set<String> LEGACY=BuiltinRoles.permissions("SUPER_ADMIN");

    static void migrate(Connection c,boolean fresh) throws Exception {
        WebDatabaseHealth.verifyV3(c);
        var roles=new ArrayList<Role>();
        try(var s=c.prepareStatement("SELECT id,name,role_type,enabled,deleted FROM web_role WHERE role_type IS NOT NULL ORDER BY id");var r=s.executeQuery()) {
            while(r.next()) {
                var grants=new TreeSet<String>();
                try(var ps=c.prepareStatement("SELECT p.code FROM web_role_permission rp JOIN web_permission p ON p.id=rp.permission_id WHERE rp.role_id=?")) {
                    ps.setLong(1,r.getLong(1));try(var g=ps.executeQuery()) {while(g.next()) grants.add(g.getString(1));}
                }
                if(!LEGACY.containsAll(grants)) throw new IllegalStateException("Unknown v3 role permission; migration stopped");
                if(!Set.of("CA","PLATFORM").contains(r.getString(3))) throw new IllegalStateException("Unknown v3 role scope");
                roles.add(new Role(r.getLong(1),r.getString(2),r.getString(3),r.getBoolean(4),r.getBoolean(5),Set.copyOf(grants)));
            }
        }
        try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM web_role_binding b LEFT JOIN web_role r ON r.name=b.role_name WHERE r.id IS NULL OR r.role_type IS NULL OR r.deleted=TRUE OR (r.role_type='PLATFORM' AND b.scope_type<>'PLATFORM') OR (r.role_type='CA' AND b.scope_type NOT IN ('NAMED_CA','ALL_CAS'))")) {
            r.next();if(r.getLong(1)!=0) throw new IllegalStateException("Invalid legacy role binding; migration stopped");
        }
        execute(c,"INSERT IGNORE INTO web_schema_migration(version,stage) VALUES (4,?)",fresh?1:0);
        c.commit();
        try(var s=c.createStatement();var r=s.executeQuery("SELECT stage FROM web_schema_migration WHERE version=4")) {
            if(!r.next() || !Set.of(0,1).contains(r.getInt(1))) throw new IllegalStateException("Unsupported permission migration stage");
            // Persist installation mode across implicit DDL commits and process restarts.
            fresh=r.getInt(1)==1;
        }
        SqlScripts.populate(c,"db/web-schema-v4.sql");
        for(String code:PermissionCatalog.codes()) execute(c,"INSERT IGNORE INTO web_permission(code,description) VALUES (?,?)",code,code);
        if(fresh) {
            for(var entry:DefaultRoles.roles().entrySet()) {
                execute(c,"INSERT IGNORE INTO web_role(name,display_name,description,role_type,builtin) VALUES (?,?,?,?,TRUE)",entry.getKey(),entry.getKey(),entry.getKey(),DefaultRoles.PLATFORM_ROLES.contains(entry.getKey())?"PLATFORM":"CA");
                replace(c,entry.getKey(),entry.getValue());
            }
        } else for(Role role:roles) {
            if(role.name().equals("SUPER_ADMIN")) {replace(c,role.name(),PermissionCatalog.codes());continue;}
            var ca=map(role.old(),"CA",false);
            if(role.scope().equals("PLATFORM")) {
                if(!ca.isEmpty()) throw new IllegalStateException("Legacy platform role contains CA grants");
                replace(c,role.name(),map(role.old(),"PLATFORM",true));
            } else {
                var reads=map(role.old(),"PLATFORM",false);
                var all=map(role.old(),"PLATFORM",true);
                replace(c,role.name(),ca);
                String named=compatibility(c,role,"NAMED",reads);
                String allName=all.equals(reads)?named:compatibility(c,role,"ALL",all);
                bind(c,role.name(),"NAMED_CA",named);
                bind(c,role.name(),"ALL_CAS",allName);
            }
            var snapshot=new LinkedHashMap<String,Object>();
            snapshot.put("oldPermissions",role.old());snapshot.put("caPermissions",ca);
            snapshot.put("platformNamed",map(role.old(),"PLATFORM",false));
            snapshot.put("platformAll",map(role.old(),"PLATFORM",true));
            String json=Json.write(snapshot);
            execute(c,"INSERT INTO web_audit_event(actor_username,resource_type,resource_id,action,result,details_json) VALUES ('schema-migration','role',?,'PERMISSION_MIGRATE','SUCCESS',?)",role.name(),json);
        }
        execute(c,"UPDATE web_user SET auth_version=auth_version+1");
        execute(c,"DELETE FROM SPRING_SESSION");
        execute(c,"UPDATE web_schema_migration SET stage=2 WHERE version=4");
        execute(c,"UPDATE web_schema_version SET version=4 WHERE version=3");
    }

    private static Set<String> map(Set<String> old,String scope,boolean allCas) {
        var out=new TreeSet<String>();
        for(String p:old) {
            switch(p) {
                case "SHARED_READ" -> RESOURCES.forEach(r -> out.add(r+"_READ"));
                case "SHARED_CONFIG" -> {
                    RESOURCES.forEach(r -> out.add(r+"_READ"));
                    if(allCas) {
                        for(String r:RESOURCES) for(String a:List.of("CREATE","UPDATE","DELETE")) out.add(r+"_"+a);
                        out.add("PROFILE_CONFIG_READ");
                    }
                }
                case "CA_CREATE" -> {if(allCas) out.addAll(Set.of("CA_CREATE_ROOT","CA_IMPORT"));}
                case "CA_CONFIG" -> PermissionCatalog.entries().stream().filter(e -> e.scope().equals("CA") && e.code().startsWith("CA_") && !Set.of("CA_DELETE","CA_ISSUE_SUBCA").contains(e.code())).forEach(e -> out.add(e.code()));
                case "CA_CONFIG_READ" -> out.add("CA_DETAIL_READ");
                case "CA_READ" -> out.addAll(Set.of("CA_READ","CRL_READ"));
                case "USER_MANAGE" -> out.addAll(Set.of("USER_READ","USER_CREATE","USER_UPDATE","USER_STATUS_UPDATE","USER_PASSWORD_RESET","USER_SESSION_REVOKE"));
                case "SYSTEM_ADMIN" -> out.addAll(Set.of("SYSTEM_READ","SYSTEM_RESTART","SYSTEM_UNLOCK","SYSTEM_NOTIFY_CHANGE","TOKEN_READ","DBSCHEMA_READ","DBSCHEMA_CREATE","DBSCHEMA_UPDATE","DBSCHEMA_DELETE"));
                case "CERT_REVOKE" -> out.addAll(Set.of("CERT_REVOKE","CERT_SUSPEND","CERT_UNSUSPEND"));
                case "CRL_MANAGE" -> out.add("CRL_GENERATE");
                case "ROLE_MANAGE" -> out.addAll(Set.of("ROLE_MANAGE","USER_ROLE_ASSIGN"));
                default -> {PermissionCatalog.get(p);out.add(p);}
            }
        }
        out.removeIf(p -> !PermissionCatalog.get(p).scope().equals(scope));
        return PermissionCatalog.closure(out);
    }
    private static String compatibility(Connection c,Role role,String variant,Set<String> grants) throws Exception {
        if(grants.isEmpty()) return null;
        String name="MIG_"+role.id()+"_"+variant;
        // Never overwrite an administrator's existing code on a deterministic-name collision.
        try(var s=c.prepareStatement("SELECT COUNT(*) FROM web_role WHERE name=?")) {
            s.setString(1,name);try(var r=s.executeQuery()) {r.next();if(r.getInt(1)>0) throw new IllegalStateException("Migration role code collision");}
        }
        execute(c,"INSERT INTO web_role(name,display_name,description,role_type,enabled,deleted,migration_source,migration_generated) VALUES (?,?,?,'PLATFORM',?,?,?,TRUE)",
                name,"Migrated: "+role.name()+" ("+variant+")","Platform grants split from "+role.name(),role.enabled(),role.deleted(),role.name());
        replace(c,name,grants);
        return name;
    }
    private static void bind(Connection c,String original,String scope,String variant) throws Exception {
        if(variant!=null) execute(c,"INSERT INTO web_role_binding(user_id,role_name,scope_type) SELECT DISTINCT user_id,?,'PLATFORM' FROM web_role_binding b WHERE role_name=? AND scope_type=? AND NOT EXISTS (SELECT 1 FROM web_role_binding x WHERE x.user_id=b.user_id AND x.role_name=? AND x.scope_type='PLATFORM')",variant,original,scope,variant);
    }
    private static void replace(Connection c,String name,Set<String> grants) throws Exception {
        execute(c,"DELETE rp FROM web_role_permission rp JOIN web_role r ON r.id=rp.role_id WHERE r.name=?",name);
        for(String p:grants) execute(c,"INSERT INTO web_role_permission(role_id,permission_id) SELECT r.id,p.id FROM web_role r,web_permission p WHERE r.name=? AND p.code=?",name,p);
    }
    private static void execute(Connection c,String sql,Object... args) throws SQLException {
        try(var s=c.prepareStatement(sql)) {for(int i=0;i<args.length;i++) s.setObject(i+1,args[i]);s.executeUpdate();}
    }
    private PermissionMigration() { }
}
