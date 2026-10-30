package org.xipki.mgmt.web.service;

import org.xipki.mgmt.web.core.*;

import java.util.Set;

/** Public, non-secret capabilities needed before a profile can issue a CA certificate. */
public record ProfileCapabilities(CertLevel level, Integer pathLenConstraint, Set<String> keyAlgorithms,
                                  String diagnostic) {
    public enum CertLevel { ROOT_CA, SUB_CA, END_ENTITY, CROSS, UNKNOWN }

    public boolean supportsSubCa() {
        return level == CertLevel.SUB_CA;
    }
}
