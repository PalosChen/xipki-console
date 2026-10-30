package org.xipki.mgmt.web;

import org.junit.jupiter.api.Test;
import org.xipki.mgmt.web.core.Routes;
import org.xipki.mgmt.web.security.ManagementApiPolicy;
import org.xipki.mgmt.web.service.CaManagementService;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RoutesCoverageTest {
    @Test void registeredEndpointsAndFailClosedPermissionPolicyHaveExactTwoWayCoverage() {
        var routes = new Routes();
        // Registration constructs domain controllers but must not contact the CA engine or DB.
        CaRoutes.register(routes, new CaManagementService(null), null, null, null, null, null, null, null);
        new IdentityRoutes(null, null, null, null, null).register(routes);
        routes.add("GET", "/health/live", request -> Map.of("status", "UP"));
        routes.add("GET", "/health/ready", request -> Map.of("status", "UP"));

        Set<Routes.Endpoint> actual = new HashSet<>(routes.entries());
        Set<Routes.Endpoint> expected = new HashSet<>();
        for (var rule : ManagementApiPolicy.rules()) {
            assertTrue(expected.add(new Routes.Endpoint(rule.method(), rule.path())),
                    () -> "Duplicate policy endpoint: " + rule.method() + " " + rule.path());
        }
        assertEquals(routes.entries().size(), actual.size(), "Duplicate route registration");
        var missing = new HashSet<>(expected); missing.removeAll(actual);
        var unprotected = new HashSet<>(actual); unprotected.removeAll(expected);
        assertAll(
                () -> assertTrue(missing.isEmpty(), () -> "Policy endpoints without handlers: " + missing),
                () -> assertTrue(unprotected.isEmpty(), () -> "Handlers without exact permission policy: " + unprotected));
    }
}
