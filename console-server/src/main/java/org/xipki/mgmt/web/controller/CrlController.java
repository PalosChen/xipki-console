package org.xipki.mgmt.web.controller;

import org.xipki.mgmt.web.core.*;

import org.xipki.mgmt.web.ca.CaDomain.*;
import org.xipki.mgmt.web.ca.CaManager;
import org.xipki.mgmt.web.dto.ApiResponse;
import org.xipki.mgmt.web.service.CaManagementService;
import org.xipki.mgmt.web.ca.Crypto.*;

import java.math.BigInteger;

public class CrlController {

    private final CaManagementService service;
    private final org.xipki.mgmt.web.security.AccessControl access;

    public CrlController(CaManagementService service,org.xipki.mgmt.web.security.AccessControl access) {
        this.service = service; this.access=access;
    }

    public ApiResponse<String> generateCrl(String ca) throws CaMgmtException {
        ca=access.require("CRL_GENERATE",ca,null).name();
        service.requireStarted(ca);
        X509Crl crl = service.getCaManager().generateCrlOnDemand(ca);
        return ApiResponse.ok("CRL generated, number: " + crl.getCrlNumber());
    }

    public Reply<byte[]> downloadCurrentCrl(String ca) throws Exception {
        ca=access.require("CRL_READ",ca,null).name();
        service.requireStarted(ca);
        X509Crl crl = service.getCaManager().getCurrentCrl(ca);
        if (crl == null) {
            return Reply.status(404).build();
        }

        return Reply.ok()
                .contentType("application/octet-stream")
                .header("Content-Disposition",
                        "attachment; filename=\"" + ca + "-crl.crl\"")
                .body(crl.getEncoded());
    }

    public Reply<byte[]> downloadCrl(String ca,
                                               String number) throws Exception {
        ca=access.require("CRL_READ",ca,null).name();
        service.requireStarted(ca);
        if (!number.matches("[0-9]+")) throw new IllegalArgumentException("Invalid CRL number");
        BigInteger crlNumber = new BigInteger(number);
        if (crlNumber.signum() <= 0) throw new IllegalArgumentException("Invalid CRL number");
        X509Crl crl = service.getCaManager().getCrl(ca, crlNumber);
        if (crl == null) {
            return Reply.status(404).build();
        }

        return Reply.ok()
                .contentType("application/octet-stream")
                .header("Content-Disposition",
                        "attachment; filename=\"" + ca + "-crl-" + number + ".crl\"")
                .body(crl.getEncoded());
    }
}
