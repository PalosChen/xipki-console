package org.xipki.mgmt.web.controller;

import org.xipki.mgmt.web.core.*;

import org.xipki.mgmt.web.ca.CaDomain.*;
import org.xipki.mgmt.web.ca.CaManager;
import org.xipki.mgmt.web.dto.ApiResponse;
import org.xipki.mgmt.web.service.CaManagementService;

import java.util.*;

public class DashboardController {

    private final CaManagementService service;
    private final org.xipki.mgmt.web.security.AccessControl access;

    public DashboardController(CaManagementService service,org.xipki.mgmt.web.security.AccessControl access) {
        this.service = service; this.access=access;
    }

    public ApiResponse<Map<String, Object>> dashboard() throws CaMgmtException {
        CaManager mgr = service.getCaManager();
        Map<String, Object> data = new LinkedHashMap<>();
        var visible=access.visibleCaNames();
        data.put("systemStatus", access.has("SYSTEM_READ")?mgr.getCaSystemStatus().name():"SCOPED_VIEW");
        data.put("caNames", visible);
        data.put("successfulCaNames", mgr.getSuccessfulCaNames().stream().filter(visible::contains).toList());
        data.put("failedCaNames", mgr.getFailedCaNames().stream().filter(visible::contains).toList());
        data.put("inactiveCaNames", mgr.getInactiveCaNames().stream().filter(visible::contains).toList());
        if(access.has("PROFILE_READ")) data.put("certprofileCount", mgr.getCertprofileNames().size());
        if(access.has("SIGNER_READ")) data.put("signerCount", mgr.getSignerNames().size());
        if(access.has("REQUESTOR_READ")) data.put("requestorCount", mgr.getRequestorNames().size());
        if(access.has("PUBLISHER_READ")) data.put("publisherCount", mgr.getPublisherNames().size());
        return ApiResponse.ok(data);
    }
}
