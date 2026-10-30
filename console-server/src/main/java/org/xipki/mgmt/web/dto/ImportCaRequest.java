package org.xipki.mgmt.web.dto;

import org.xipki.mgmt.web.core.*;

import java.util.List;

public class ImportCaRequest extends StrictRequest {
    public String name;
    public String certificate;
    public List<String> certificateChain;
    public Long nextCrlNo;
    public CaConfigurationRequest config;
    public CaKeyRequest key;
    public Boolean acceptUnknownRevocation;
}
