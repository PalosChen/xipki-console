package org.xipki.mgmt.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xipki.mgmt.web.core.Config;
import java.nio.file.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class StartupContractTest {
    @TempDir Path directory;
    @Test void distinguishesCaRejectionFromUncertainTransportOutcome() {
        assertEquals(400,Main.LiteServlet.error(new org.xipki.mgmt.web.ca.CaDomain.CaMgmtException("rejected"),null).status());
        assertEquals(503,Main.LiteServlet.error(new org.xipki.mgmt.web.ca.CaDomain.CaMgmtException(new java.io.IOException("unavailable")),null).status());
    }
    @Test void exposesValidatedSubjectErrorsButHidesUnexpectedArguments() {
        var error = Main.LiteServlet.error(new org.xipki.mgmt.web.core.ApiException(400,
                "CSR subject order does not match the certificate profile"), null);
        assertEquals(400, error.status());
        assertEquals(Map.of("error", "CSR subject order does not match the certificate profile"), error.body());
        assertEquals(Map.of("error", "Invalid request fields or format"),
                Main.LiteServlet.error(new IllegalArgumentException("secret"), null).body());
    }
    @Test void acceptsSingleTerminalNewlineAndRejectsAmbiguousSecrets() throws Exception {
        Path file=directory.resolve("password");Files.writeString(file,"test password\r\n");
        assertEquals("test password",Main.secret(new Config(Map.of("file",file.toString())),"value","file"));
        assertThrows(IllegalArgumentException.class,()->Main.secret(new Config(Map.of("file",file.toString(),"value","x")),"value","file"));
        Files.writeString(file,"one\ntwo\n");
        assertThrows(IllegalArgumentException.class,()->Main.secret(new Config(Map.of("file",file.toString())),"value","file"));
    }
    @Test void preservesLegacySettingsButRejectsConflicts() {
        assertEquals("legacy",Main.setting(new Config(Map.of("old","legacy")),"new","old","default"));
        assertThrows(IllegalArgumentException.class,()->Main.setting(new Config(Map.of("new","a","old","b")),"new","old","default"));
    }
    @Test void bundlesExistingFrontendAndEveryReferencedAsset() throws Exception {
        try(var index=Main.class.getResourceAsStream("/static/index.html")) {
            assertNotNull(index,"Build the existing frontend before packaging the lite backend");
            var matcher=java.util.regex.Pattern.compile("(?:src|href)=\"(/assets/[^\"]+)\"").matcher(new String(index.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
            int count=0;while(matcher.find()){assertNotNull(Main.class.getResource("/static"+matcher.group(1)));count++;}
            assertTrue(count>0);
        }
    }

    @Test void multipartParserHasExplicitPartAndHeaderBudgets() throws Exception {
        var tomcat=Main.server(0,directory,new Main.LiteServlet(null,null,null));
        try {
            var connector=tomcat.getConnector();
            assertEquals("17",connector.getProperty("maxPartCount"));
            assertEquals("1024",connector.getProperty("maxPartHeaderSize"));
            assertEquals(256,connector.getProperty("maxConnections"));
        } finally { tomcat.destroy(); }
    }
}
