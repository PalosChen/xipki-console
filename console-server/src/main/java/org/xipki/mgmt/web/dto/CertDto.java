package org.xipki.mgmt.web.dto;

import org.xipki.mgmt.web.core.*;

import org.xipki.mgmt.web.ca.CaDomain.*;
import org.xipki.mgmt.web.ca.Crypto.*;
import org.xipki.util.extra.misc.PemEncoder;

import java.nio.charset.StandardCharsets;

public class CertDto {

    public String serialNumber;
    public String subject;
    public String issuer;
    public String notBefore;
    public String notAfter;
    public String certPem;
    public String profile;
    // revocation
    public boolean revoked;
    public String revocationTime;
    public String revocationReason;
    public String invalidityTime;

    public static CertDto fromCertWithRevocation(CertWithRevocationInfo info) {
        CertDto dto = new CertDto();
        X509Cert cert = info.getCert().getCert();
        dto.subject = cert.getSubjectText();
        dto.issuer = cert.getIssuerText();
        dto.serialNumber = cert.getSerialNumber().toString(16);
        dto.notBefore = cert.getNotBefore().toString();
        dto.notAfter = cert.getNotAfter().toString();
        dto.profile = info.getCertprofile();

        try {
            dto.certPem = new String(
                    PemEncoder.encode(cert.getEncoded(), PemEncoder.PemLabel.CERTIFICATE),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            // ignore
        }

        CertRevocationInfo rev = info.getRevInfo();
        if (rev != null) {
            dto.revoked = true;
            dto.revocationTime = rev.getRevocationTime() != null ? rev.getRevocationTime().toString() : null;
            dto.revocationReason = rev.getReason() != null ? rev.getReason().getDescription() : null;
            dto.invalidityTime = rev.getInvalidityTime() != null ? rev.getInvalidityTime().toString() : null;
        }

        return dto;
    }

    public static CertDto fromCertListInfo(CertListInfo info) {
        CertDto dto = new CertDto();
        dto.serialNumber = info.getSerialNumber().toString(16);
        dto.subject = info.getSubject().toString();
        dto.notBefore = info.getNotBefore().toString();
        dto.notAfter = info.getNotAfter().toString();
        return dto;
    }

    // Getters and setters
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    public String getNotBefore() { return notBefore; }
    public void setNotBefore(String notBefore) { this.notBefore = notBefore; }
    public String getNotAfter() { return notAfter; }
    public void setNotAfter(String notAfter) { this.notAfter = notAfter; }
    public String getCertPem() { return certPem; }
    public void setCertPem(String certPem) { this.certPem = certPem; }
    public String getProfile() { return profile; }
    public void setProfile(String profile) { this.profile = profile; }
    public boolean isRevoked() { return revoked; }
    public void setRevoked(boolean revoked) { this.revoked = revoked; }
    public String getRevocationTime() { return revocationTime; }
    public void setRevocationTime(String revocationTime) { this.revocationTime = revocationTime; }
    public String getRevocationReason() { return revocationReason; }
    public void setRevocationReason(String revocationReason) { this.revocationReason = revocationReason; }
    public String getInvalidityTime() { return invalidityTime; }
    public void setInvalidityTime(String invalidityTime) { this.invalidityTime = invalidityTime; }
}
