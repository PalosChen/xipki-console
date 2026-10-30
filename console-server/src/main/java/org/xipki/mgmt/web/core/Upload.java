package org.xipki.mgmt.web.core;

import java.io.IOException;
import java.io.InputStream;

public interface Upload {
    byte[] getBytes() throws IOException;
    long getSize();
    boolean isEmpty();
    String getOriginalFilename();
    String getContentType();
    default InputStream getInputStream() throws IOException { return new java.io.ByteArrayInputStream(getBytes()); }
}
