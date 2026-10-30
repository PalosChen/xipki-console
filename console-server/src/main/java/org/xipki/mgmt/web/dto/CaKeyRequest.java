package org.xipki.mgmt.web.dto;

import org.xipki.mgmt.web.core.*;


/** Write-only signer materials. Never serialize this DTO in a response or log. */
public class CaKeyRequest extends StrictRequest {
    public String mode;
    public String algorithm;
    public String pkcs12;
    public String password;
    public String keyAlias;
    public String signatureAlgorithm;
    public String module;
    public Integer slotIndex;
    public String keyId;
    public String keyLabel;
}
