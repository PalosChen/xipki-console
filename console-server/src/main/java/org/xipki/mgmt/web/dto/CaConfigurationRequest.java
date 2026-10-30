package org.xipki.mgmt.web.dto;

import org.xipki.mgmt.web.core.*;

import java.util.List;

/** Null means unchanged; destructive material replacement requires explicit consent. */
public class CaConfigurationRequest extends StrictRequest {
    public String status;
    public Boolean acceptUnknownRevocation;
    public String maxValidity;
    public String signerType;
    public String signerConf;
    public CaKeyRequest key;
    public Boolean replaceSigner;
    public Boolean replaceCertificates;
    public String crlControl;
    public String ctlogControl;
    public String revokeSuspendedControl;
    public String crlSignerName;
    public List<String> keypairGenNames;
    public Boolean saveCert;
    public Boolean saveKeypair;
    public String validityMode;
    public List<String> permissions;
    public Integer keepExpiredCertDays;
    public Integer expirationPeriod;
    public String extraControl;
    public CaUrisRequest caUris;
    public String encodedCert;
    public List<String> encodedCertchain;
    public Integer numCrls;
    public Integer serialNoLen;

    public static class CaUrisRequest extends StrictRequest {
        public List<String> cacertUris;
        public List<String> ocspUris;
        public List<String> crlUris;
        public List<String> deltaCrlUris;
    }
}
