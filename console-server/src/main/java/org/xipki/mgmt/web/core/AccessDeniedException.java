package org.xipki.mgmt.web.core;

public final class AccessDeniedException extends ApiException {
    public AccessDeniedException(String message) { super(403,message); }
}
