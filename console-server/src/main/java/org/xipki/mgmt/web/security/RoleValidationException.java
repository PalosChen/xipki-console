package org.xipki.mgmt.web.security;

import org.xipki.mgmt.web.core.*;

/** Safe catalog/role validation feedback; never contains credentials or submitted material. */
public class RoleValidationException extends IllegalArgumentException {
    public RoleValidationException(String message) { super(message); }
}
