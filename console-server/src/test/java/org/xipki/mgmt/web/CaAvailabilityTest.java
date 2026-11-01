package org.xipki.mgmt.web;

import org.junit.jupiter.api.Test;
import org.xipki.mgmt.web.ca.CaDomain.*;
import org.xipki.mgmt.web.ca.CaMgmtClient;
import org.xipki.mgmt.web.controller.*;
import org.xipki.mgmt.web.core.ApiException;
import org.xipki.mgmt.web.dto.KeyCertDownloadRequest;
import org.xipki.mgmt.web.security.AccessControl;
import org.xipki.mgmt.web.service.CaManagementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.security.auth.x500.X500Principal;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CaAvailabilityTest {
    @Test void inactiveCaIsRejectedBeforeRemoteCertificateQuery() throws Exception {
        var client=new Client();var service=new CaManagementService(client);
        var controller=new CertController(service,authorized(service),null);
        var error=assertThrows(ApiException.class,()->controller.listCerts(null,"inactive",null,null,null,"notBefore",50,false,null));
        assertEquals(409,error.status());assertTrue(error.getMessage().contains("not running"));assertEquals(0,client.queries);
    }
    @Test void runningCaStillUsesCertificateQuery() throws Exception {
        var client=new Client();var service=new CaManagementService(client);
        assertTrue(new CertController(service,authorized(service),null).listCerts(null,"running",null,null,null,"notBefore",50,false,null).data.isEmpty());
        assertEquals(1,client.queries);
    }
    @Test void inactiveCaIsRejectedBeforeCrlOperations() throws Exception {
        var service=new CaManagementService(new Client());var controller=new CrlController(service,authorized(service));
        assertEquals(409,assertThrows(ApiException.class,()->controller.generateCrl("inactive")).status());
        assertEquals(409,assertThrows(ApiException.class,()->controller.downloadCurrentCrl("inactive")).status());
        assertEquals(409,assertThrows(ApiException.class,()->controller.downloadCrl("inactive","1")).status());
    }
    @Test void missingKeypairGeneratorRejectsBothKeyGenerationRoutesBeforeRemoteIssuance() throws Exception {
        var client = new Client();
        var service = new CaManagementService(client);
        var access = new AccessControl(service) {
            @Override public Issuance requireIssue(String permission, String ca, String profile) {
                return new Issuance(new CaIdentity(ca, "fingerprint"), profile);
            }
        };
        var controller = new CertController(service, access, null);
        var request = (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{HttpServletRequest.class}, (proxy, method, args) -> 0L);
        var response = (HttpServletResponse) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{HttpServletResponse.class}, (proxy, method, args) -> null);
        var direct = assertThrows(ApiException.class,
            () -> controller.enrollKeyCert(request, response, "child", "leaf", "CN=leaf", null, null));
        var download = new KeyCertDownloadRequest();
        download.ca = "child"; download.profile = "leaf"; download.subject = "CN=leaf";
        download.downloadPassword = "long-test-password";
        var packaged = assertThrows(ApiException.class, () -> controller.downloadKeyCert(request, download));
        assertEquals(409, direct.status());
        assertEquals(direct.getMessage(), packaged.getMessage());
        assertEquals(0, client.keyGenerations);
    }
    private static AccessControl authorized(CaManagementService service) {
        return new AccessControl(service) {
            @Override public CaIdentity require(String permission,String ca,String profile){return new CaIdentity(ca,"fingerprint");}
            @Override public boolean allows(String permission,CaIdentity ca,String profile){return true;}
        };
    }
    private static class Client extends CaMgmtClient {
        int queries;
        int keyGenerations;
        Client() throws Exception {super(null);}
        @Override public Set<String> getSuccessfulCaNames(){return Set.of("running");}
        @Override public CaEntry getCa(String ca) {
            return new CaEntry(new BaseCaInfo("PKCS12", null), new NameId(null, ca), "");
        }
        @Override public KeyCertBytesPair generateKeyCert(String ca, String profile, String subject, Instant from, Instant to) {
            keyGenerations++; throw new AssertionError("Remote key generation must not be called");
        }
        @Override public List<CertListInfo> listCertificates(String ca,X500Principal subject,Instant from,Instant to,CertListOrderBy order,int limit)throws CaMgmtException {
            queries++;if(!ca.equals("running"))throw new CaMgmtException("unknown CA "+ca);return List.of();
        }
    }
}
