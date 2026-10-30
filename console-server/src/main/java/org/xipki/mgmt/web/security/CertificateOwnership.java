package org.xipki.mgmt.web.security;

import org.xipki.mgmt.web.core.*;

import java.util.List;

public class CertificateOwnership {
    private final Jdbc jdbc;
    public CertificateOwnership(Jdbc jdbc) { this.jdbc=jdbc; }
    public void record(AccessControl.CaIdentity ca,String serial,String profile) {
        jdbc.update("INSERT INTO web_certificate_owner(ca_name,ca_fingerprint,serial_number,user_id,profile_name) VALUES (?,?,?,?,?)",
                ca.name(),ca.fingerprint(),serial,AccountService.principal().id(),profile);
    }
    public boolean owns(AccessControl.CaIdentity ca,String serial) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM web_certificate_owner WHERE ca_name=? AND ca_fingerprint=? AND serial_number=? AND user_id=?",
                Integer.class,ca.name(),ca.fingerprint(),serial,AccountService.principal().id())==1;
    }
    public List<String> serials(AccessControl.CaIdentity ca,int limit) {
        if(limit<1 || limit>1000) throw new IllegalArgumentException("Invalid limit");
        return jdbc.queryForList("SELECT serial_number FROM web_certificate_owner WHERE ca_name=? AND ca_fingerprint=? AND user_id=? ORDER BY issued_at DESC,serial_number LIMIT ?",
                String.class,ca.name(),ca.fingerprint(),AccountService.principal().id(),limit);
    }
}
