package org.xipki.mgmt.web.db;

import java.sql.Connection;
import java.nio.charset.StandardCharsets;

/** Runs only the bundled, reviewed schema scripts (no user SQL input). */
final class SqlScripts {
    static void populate(Connection connection,String resource) throws Exception {
        String sql;
        try(var stream=SqlScripts.class.getClassLoader().getResourceAsStream(resource)) {
            if(stream==null) throw new IllegalStateException("Missing schema resource: "+resource);
            sql=new String(stream.readAllBytes(),StandardCharsets.UTF_8);
        }
        sql=sql.replaceAll("(?m)^\\s*--[^\\r\\n]*","");
        // Bundled scripts contain no procedures or semicolons inside string literals.
        for(String part:sql.split(";")) if(!part.isBlank()) try(var statement=connection.createStatement()) {
            statement.setQueryTimeout(30);statement.execute(part.strip());
        }
    }
    private SqlScripts() { }
}
