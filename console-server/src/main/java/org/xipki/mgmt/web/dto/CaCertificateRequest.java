package org.xipki.mgmt.web.dto;

import org.xipki.mgmt.web.core.*;
import java.util.List;
public class CaCertificateRequest extends StrictRequest {
    public String certificate;
    public List<String> certificateChain;
}
