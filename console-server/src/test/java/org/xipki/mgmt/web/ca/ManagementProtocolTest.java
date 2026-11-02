// Copyright (c) 2026 trustasia-com. All rights reserved.
// License Apache License 2.0

package org.xipki.mgmt.web.ca;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import javax.security.auth.x500.X500Principal;
import org.junit.jupiter.api.Test;
import org.xipki.codec.json.*;
import org.xipki.mgmt.web.ca.CaDomain.*;
import org.xipki.mgmt.web.service.CaConfigurationMapper;

/**
 * Management protocol contract tests.
 *
 * @author palos
 */
class ManagementProtocolTest {
  private static class Exchange implements ManagementProtocol.Transport {
    String action;
    JsonMap request;
    String response = "{}";
    int calls;

    @Override
    public byte[] send(String action, byte[] request) {
      this.action = action;
      calls++;
      try {
        this.request = request.length == 0 ? new JsonMap() : JsonParser.parseMap(request, false);
      } catch (Exception ex) {
        throw new IllegalArgumentException(ex);
      }
      return response.getBytes(StandardCharsets.UTF_8);
    }

    CaMgmtClient client() {
      return new CaMgmtClient(new ManagementProtocol(this));
    }
  }

  @Test
  void serialsAreSignedByteHexAndTimesRemainUtcStrings() throws Exception {
    var wire = new Exchange();
    var client = wire.client();
    var serial = new BigInteger("fedcba98765432100123456789abcdef", 16);
    var instant = Instant.parse("2026-10-29T00:00:00Z");
    client.revokeCertificate("ca", serial, CrlReason.CERTIFICATE_HOLD, instant);
    assertEquals("revokeCertificate", wire.action);
    assertEquals("00fedcba98765432100123456789abcdef", wire.request.getString("serialNumber"));
    assertEquals("CERTIFICATE_HOLD", wire.request.getString("reason"));
    assertEquals(instant, wire.request.getInstant("invalidityTime"));
    wire.response =
        "{\"result\":[{\"serialNumber\":\"00fedcba98765432100123456789abcdef\","
            + "\"subject\":\"CN=Fixture\",\"notBefore\":\"2026-10-29T00:00:00Z\","
            + "\"notAfter\":\"2027-10-29T00:00:00Z\"}]}";
    var subject = new X500Principal("CN=Fixture");
    var list =
        client.listCertificates("ca", subject, instant, null, CertListOrderBy.SUBJECT_DESC, 5);
    assertEquals(serial, list.get(0).getSerialNumber());
    assertEquals("subject-desc", wire.request.getString("orderBy"));
    assertArrayEquals(subject.getEncoded(), wire.request.getBytes("encodedSubjectDnPattern"));
    assertEquals(5, wire.request.getInt("numEntries"));
  }

  @Test
  void caCreationIsFlattenedAndChangeKeepsExplicitWrapper() throws Exception {
    var wire = new Exchange();
    var client = wire.client();
    var base = new CaConfigurationMapper().create("PKCS12", null, null);
    base.setCaUris(new CaUris(List.of("https://example/cert"), null, null, null));
    var entry = new CaEntry(base, new NameId(null, "fixture"), "algo=SHA256withRSA");
    client.addCa(entry);
    JsonMap ca = wire.request.getNnMap("caEntry");
    assertEquals("fixture", ca.getString("name"));
    assertNull(ca.getMap("base"));
    assertEquals("inactive", ca.getString("status"));
    assertEquals("STRICT", ca.getString("validityMode"));
    assertEquals(List.of("all"), ca.getList("permissions").toStringList());
    assertEquals(
        "https://example/cert", ca.getNnMap("caUris").getList("cacertUris").toStringList().get(0));
    wire.response = new JsonBuilder(false, null, false).doToJson(new JsonMap().put("result", ca));
    var read = client.getCa("fixture");
    assertEquals("fixture", read.getIdent().getName());
    assertEquals(20, read.getBase().getSnSize());
    assertEquals(CaStatus.inactive, read.getBase().getStatus());
    wire.response = "{}";
    var change = new ChangeCaEntry(new NameId(null, "fixture"));
    change.setSaveKeypair(false);
    change.setCrlControl("NULL");
    client.changeCa(change);
    JsonMap c = wire.request.getNnMap("changeCaEntry");
    assertEquals("fixture", c.getNnMap("ident").getString("name"));
    assertEquals(false, c.getBool("saveKeypair"));
    assertEquals("NULL", c.getString("crlControl"));
    assertNull(c.getString("signerConf"));
  }

  @Test
  void certificatesRevocationChainsAndKeyPairsUseExistingEnvelopes() throws Exception {
    var generator = java.security.KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    var pair = generator.generateKeyPair();
    var cert = Crypto.placeholder(pair);
    String encoded = Base64.getEncoder().encodeToString(cert.getEncoded());
    var wire = new Exchange();
    var client = wire.client();
    wire.response =
        "{\"cert\":\""
            + encoded
            + "\",\"certprofile\":\"tls\",\"revInfo\":{\"reason\":\"keyCompromise\","
            + "\"revocationTime\":\"2026-10-29T00:00:00Z\"}}";
    var result = client.getCert("ca", BigInteger.ONE);
    assertEquals(cert, result.getCert().getCert());
    assertTrue(result.isRevoked());
    assertEquals(CrlReason.KEY_COMPROMISE, result.getRevInfo().getReason());
    assertEquals("tls", result.getCertprofile());
    wire.response = "{\"result\":\"" + encoded + "\"}";
    assertEquals(cert, client.generateCertificate("ca", "tls", new byte[] {1, 2}, null, null));
    assertArrayEquals(new byte[] {1, 2}, wire.request.getBytes("encodedCsr"));
    wire.response = "{\"key\":\"AQID\",\"cert\":\"" + encoded + "\"}";
    assertArrayEquals(
        new byte[] {1, 2, 3}, client.generateKeyCert("ca", "tls", "CN=Key", null, null).getKey());
    wire.response = "{\"result\":null}";
    assertNull(client.getCurrentCrl("ca"));
  }

  @Test
  void crlLookupUsesTheLatestLongNumberField() throws Exception {
    var wire = new Exchange();
    var client = wire.client();
    wire.response = "{\"result\":null}";
    assertNull(client.getCrl("ca", 42L));
    assertEquals("getCrl", wire.action);
    assertEquals(42L, wire.request.getLong("crlNumber"));
  }

  @Test
  void permissionsAssociationsAndNamesUseOriginalFields() throws Exception {
    var wire = new Exchange();
    var client = wire.client();
    client.addRequestorToCa(
        new CaHasRequestorEntry(
            new NameId(9, "requestor"), new Permissions(List.of("enroll_cert")), List.of("tls")),
        "ca");
    var req = wire.request.getNnMap("requestor");
    assertEquals("ca", wire.request.getString("caName"));
    assertEquals(9, req.getNnMap("requestorIdent").getInt("id"));
    assertEquals(List.of("enroll_cert"), req.getList("permissions").toStringList());
    client.removeRequestorFromCa("requestor", "ca");
    assertEquals("requestor", wire.request.getString("entityName"));
    client.addCertprofileToCa("tls:server,client", "ca");
    assertEquals("tls:server,client", wire.request.getString("profileName"));
    wire.response = "{\"result\":[\"tls:server,client\"]}";
    assertTrue(client.getCertprofilesForCa("ca").iterator().next().containsNameOrAlias("server"));
  }

  @Test
  void rejectsMalformedDuplicateAndOversizedResponsesWithoutRetry() {
    var wire = new Exchange();
    var client = wire.client();
    wire.response = "{\"result\":\"STARTED_AS_MASTER\",\"result\":\"ERROR\"}";
    assertThrows(CaMgmtException.class, client::getCaSystemStatus);
    assertEquals(1, wire.calls);
    wire.response = "broken";
    assertThrows(CaMgmtException.class, client::getCaNames);
    var unavailable =
        new CaMgmtClient(
            new ManagementProtocol(
                (action, body) -> {
                  throw new java.io.IOException("unavailable");
                }));
    assertThrows(CaMgmtException.class, () -> unavailable.removeCa("ca"));
    var large =
        new CaMgmtClient(
            new ManagementProtocol(
                (action, body) -> new byte[ManagementProtocol.RESPONSE_LIMIT + 1]));
    assertThrows(CaMgmtException.class, large::getCaNames);
  }

  @Test
  void bouncyCastleIsAbsentFromRuntimeAndTestClasspath() {
    assertThrows(
        ClassNotFoundException.class,
        () -> Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider"));
    assertThrows(ClassNotFoundException.class, () -> Class.forName("org.xipki.security.X509Cert"));
    assertNull(java.security.Security.getProvider("BC"));
  }
}
