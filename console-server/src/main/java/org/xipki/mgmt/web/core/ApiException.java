package org.xipki.mgmt.web.core;

/** An intentional HTTP rejection, with a public, non-secret message. */
public class ApiException extends RuntimeException {
    private final int status;
    public ApiException(int status) { this(status,"Invalid request"); }
    public ApiException(int status,String message) { super(message); this.status=status; }
    public int status() { return status; }
}
