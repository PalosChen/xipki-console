package org.xipki.mgmt.web.service;

import org.xipki.mgmt.web.core.*;

import org.xipki.mgmt.web.ca.CaDomain.*;
import org.xipki.mgmt.web.ca.CaManager;
import org.xipki.mgmt.web.ca.CaMgmtClient;

/**
 * Service layer wrapping CaMgmtClient.
 * CaMgmtClient implements CaManager, so we expose it as CaManager for controllers.
 */
public class CaManagementService {

    private final CaMgmtClient caMgmtClient;

    public CaManagementService(CaMgmtClient caMgmtClient) {
        this.caMgmtClient = caMgmtClient;
    }

    public CaManager getCaManager() {
        return caMgmtClient;
    }

    public boolean isReady() {
        try {
            return caMgmtClient.getCaSystemStatus() == org.xipki.mgmt.web.ca.CaDomain.CaSystemStatus.STARTED_AS_MASTER
                    && caMgmtClient.getDbSchemas() != null
                    && caMgmtClient.getCaNames() != null;
        } catch (Exception ex) { return false; }
    }

    /** Configured/inactive CAs are not present in XiPKI's runtime instance map. */
    public void requireStarted(String name) throws org.xipki.mgmt.web.ca.CaDomain.CaMgmtException {
        if(!caMgmtClient.getSuccessfulCaNames().contains(name))throw new org.xipki.mgmt.web.core.ApiException(409,"CA is not running; refresh the CA list and select a running CA");
    }
}
