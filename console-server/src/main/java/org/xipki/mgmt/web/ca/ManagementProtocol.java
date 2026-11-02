// Copyright (c) 2026 trustasia-com. All rights reserved.
// License Apache License 2.0

package org.xipki.mgmt.web.ca;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.cert.*;
import java.time.Instant;
import java.util.*;
import javax.net.ssl.*;
import org.xipki.codec.ConfPairs;
import org.xipki.codec.json.*;
import org.xipki.mgmt.web.ca.CaDomain.*;
import org.xipki.mgmt.web.ca.CaDomain.Permissions;
import org.xipki.mgmt.web.ca.Crypto.*;
import org.xipki.pkix.util.conf.Validity;

/**
 * XiPKI 7.0 JSON protocol, bounded HTTPS transport with standard hostname verification.
 *
 * @author palos
 */
public final class ManagementProtocol {
  private static final class WireMap extends JsonMap {
    @Override
    public JsonMap putObject(String key, Object value) {
      return super.putObject(key, value);
    }
  }

  public static final int RESPONSE_LIMIT = 16 * 1024 * 1024;
  public static final String CONTENT_TYPE = "application/json";

  @FunctionalInterface
  public interface Transport {
    byte[] send(String action, byte[] request) throws IOException, CaMgmtException;
  }

  private final Transport transport;

  public ManagementProtocol(Transport transport) {
    this.transport = Objects.requireNonNull(transport);
  }

  public static ManagementProtocol https(
      String address, Path keyStore, char[] password, Path trustFile) throws Exception {
    URI uri = URI.create(address.endsWith("/") ? address : address + "/");
    if (!"https".equalsIgnoreCase(uri.getScheme())
        || uri.getHost() == null
        || uri.getRawUserInfo() != null
        || uri.getRawQuery() != null
        || uri.getRawFragment() != null)
      throw new IllegalArgumentException("Invalid HTTPS management URL");
    KeyStore identity = KeyStore.getInstance("PKCS12");
    try (var in = Files.newInputStream(keyStore)) {
      identity.load(in, password);
    }
    var km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    km.init(identity, password);
    KeyStore trust = KeyStore.getInstance(KeyStore.getDefaultType());
    trust.load(null, null);
    byte[] anchors = Files.readAllBytes(trustFile);
    int index = 0;
    for (var cert : X509Util.parseCerts(anchors))
      trust.setCertificateEntry("anchor-" + index++, cert.toJceCert());
    if (index == 0) throw new IllegalArgumentException("Trust anchors required");
    var tm = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    tm.init(trust);
    SSLContext ssl = SSLContext.getInstance("TLS");
    ssl.init(km.getKeyManagers(), tm.getTrustManagers(), new SecureRandom());
    return new ManagementProtocol(
        (action, request) -> {
          var connection = (HttpsURLConnection) uri.resolve(action).toURL().openConnection();
          connection.setSSLSocketFactory(ssl.getSocketFactory());
          connection.setInstanceFollowRedirects(false);
          connection.setConnectTimeout(15000);
          connection.setReadTimeout(60000);
          connection.setUseCaches(false);
          connection.setRequestMethod("POST");
          connection.setDoOutput(true);
          connection.setRequestProperty("Content-Type", CONTENT_TYPE);
          connection.setFixedLengthStreamingMode(request.length);
          try {
            try (var out = connection.getOutputStream()) {
              out.write(request);
            }
            int status = connection.getResponseCode();
            if (status != 200)
              throw new CaMgmtException("Management server returned HTTP " + status);
            String type = connection.getContentType();
            if (type == null || !CONTENT_TYPE.equalsIgnoreCase(type.split(";", 2)[0].strip()))
              throw new CaMgmtException("Invalid management response content type");
            if (connection.getContentLengthLong() > RESPONSE_LIMIT)
              throw new CaMgmtException("Oversized management response");
            try (var in = connection.getInputStream()) {
              byte[] bytes = in.readNBytes(RESPONSE_LIMIT + 1);
              if (bytes.length > RESPONSE_LIMIT)
                throw new CaMgmtException("Oversized management response");
              return bytes;
            }
          } finally {
            connection.disconnect();
          }
        });
  }

  public <T> T call(String action, Class<T> type, Object... pairs) throws CaMgmtException {
    try {
      WireMap request = new WireMap();
      for (int i = 0; i < pairs.length; i += 2)
        request.putObject((String) pairs[i], wire(pairs[i + 1]));
      byte[] encoded =
          pairs.length == 0
              ? new byte[0]
              : new JsonBuilder(false, null, false)
                  .doToJson(request)
                  .getBytes(StandardCharsets.UTF_8);
      if (encoded.length > RESPONSE_LIMIT)
        throw new CaMgmtException("Oversized management request");
      byte[] bytes = transport.send(action, encoded);
      if (type == Void.class) return null;
      if (bytes == null || bytes.length == 0 || bytes.length > RESPONSE_LIMIT)
        throw new CaMgmtException("Invalid management response size");
      org.xipki.mgmt.web.core.Json.validateSyntax(new String(bytes, StandardCharsets.UTF_8));
      JsonMap response = JsonParser.parseMap(bytes, false);
      Object value = type == JsonMap.class ? response : decode(response, type);
      return type.cast(value);
    } catch (CaMgmtException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new CaMgmtException("Management transport or protocol failure", ex);
    }
  }

  static Object wire(Object value) throws Exception {
    if (value == null) return null;
    if (value instanceof java.math.BigInteger n) return HexFormat.of().formatHex(n.toByteArray());
    if (value instanceof byte[] b) return Base64.getEncoder().encodeToString(b);
    if (value instanceof Instant || value instanceof Validity) return value.toString();
    if (value instanceof Enum<?> e) return e.name();
    if (value instanceof X509Cert c) return wire(c.getEncoded());
    if (value instanceof CertRevocationInfo rev)
      return new JsonMap()
          .put("reason", rev.reason().getDescription())
          .put("revocationTime", rev.revocationTime())
          .put("invalidityTime", rev.invalidityTime());
    if (value instanceof Permissions p) return wire(p.toPermissionTexts());
    if (value instanceof ConfPairs p) return p.toCodec();
    if (value instanceof CrlControl p) return new ConfPairs(p.getConf()).toCodec();
    if (value instanceof CtlogControl p) return new ConfPairs(p.getConf()).toCodec();
    if (value instanceof RevokeSuspendedControl p) return new ConfPairs(p.getConf()).toCodec();
    if (value instanceof Iterable<?> values) {
      var result = new JsonList();
      for (var v : values) result.addObject(wire(v));
      return result;
    }
    if (value instanceof CaEntry ca) {
      WireMap m = (WireMap) wire(ca.getBase());
      m.put("id", ca.getIdent().getId())
          .put("name", ca.getIdent().getName())
          .put("signerConf", ca.getSignerConf());
      m.putObject("cert", wire(ca.getCert()));
      m.putObject("certchain", wire(ca.getCertchain()));
      m.put("pathLenConstraint", ca.getCert() == null ? 0 : ca.getCert().getBasicConstraints());
      return m;
    }
    if (value instanceof SignerEntry signer)
      return new JsonMap()
          .put("name", signer.getName())
          .put("type", signer.getType())
          .put("conf", signer.getConf())
          .put("certificate", signer.getBase64Cert());
    if (value instanceof String || value instanceof Number || value instanceof Boolean)
      return value;
    if (!value.getClass().getName().startsWith(CaDomain.class.getName() + "$"))
      throw new IllegalArgumentException("Unsupported protocol value");
    var m = new WireMap();
    if (value.getClass().isRecord()) {
      for (var c : value.getClass().getRecordComponents())
        m.putObject(c.getName(), wire(c.getAccessor().invoke(value)));
    } else
      for (var f : value.getClass().getDeclaredFields()) {
        if (java.lang.reflect.Modifier.isStatic(f.getModifiers())
            || java.lang.reflect.Modifier.isTransient(f.getModifiers())) continue;
        String suffix = Character.toUpperCase(f.getName().charAt(0)) + f.getName().substring(1);
        var method =
            value.getClass().getMethod((f.getType() == boolean.class ? "is" : "get") + suffix);
        m.putObject(f.getName(), wire(method.invoke(value)));
      }
    return m;
  }

  private static Object decode(JsonMap response, Class<?> type) throws Exception {
    if (type == byte[].class) return response.getBytes("result");
    if (type == String.class) return response.getString("result");
    if (type == CaSystemStatus.class) return CaSystemStatus.valueOf(response.getNnString("result"));
    if (type == X509Cert.class) {
      byte[] b = response.getBytes("result");
      return b == null ? null : X509Util.parseCert(b);
    }
    if (type == X509Crl.class) {
      byte[] b = response.getBytes("result");
      return b == null ? null : new X509Crl(b);
    }
    if (type == KeyCertBytesPair.class)
      return new KeyCertBytesPair(response.getNnBytes("key"), response.getNnBytes("cert"));
    if (type == CertWithRevocationInfo.class) {
      byte[] b = response.getBytes("cert");
      return b == null
          ? null
          : new CertWithRevocationInfo(
              new CertWithDbId(X509Util.parseCert(b)),
              response.getString("certprofile"),
              revocation(response.getMap("revInfo")));
    }
    JsonMap map = response.getMap("result");
    if (map == null) return null;
    return entity(map, type);
  }

  private static NameId ident(JsonMap m) throws Exception {
    return new NameId(m.getInt("id"), m.getNnString("name"));
  }

  private static Object entity(JsonMap m, Class<?> type) throws Exception {
    if (type == CertprofileEntry.class)
      return new CertprofileEntry(
          ident(m.getNnMap("ident")), m.getNnString("type"), m.getString("conf"));
    if (type == PublisherEntry.class)
      return new PublisherEntry(
          ident(m.getNnMap("ident")), m.getNnString("type"), m.getString("conf"));
    if (type == RequestorEntry.class)
      return new RequestorEntry(
          ident(m.getNnMap("ident")), m.getNnString("type"), m.getString("conf"));
    if (type == KeypairGenEntry.class)
      return new KeypairGenEntry(m.getNnString("name"), m.getNnString("type"), m.getString("conf"));
    if (type == SignerEntry.class)
      return new SignerEntry(
          m.getNnString("name"),
          m.getNnString("type"),
          m.getString("conf"),
          m.getString("certificate"));
    if (type == CaEntry.class) {
      var base =
          new BaseCaInfo(m.getNnString("signerType"), permissions(m.getObject("permissions")));
      populateBase(base, m);
      var ca =
          new CaEntry(
              base, new NameId(m.getInt("id"), m.getNnString("name")), m.getNnString("signerConf"));
      byte[] cert = m.getBytes("cert");
      if (cert != null) ca.setCert(X509Util.parseCert(cert));
      var chain = m.getBytesList("certchain");
      if (chain != null) {
        var list = new ArrayList<X509Cert>();
        for (var b : chain) list.add(X509Util.parseCert(b));
        ca.setCertchain(list);
      }
      return ca;
    }
    throw new IllegalArgumentException("Unsupported response type");
  }

  private static Permissions permissions(Object value) throws Exception {
    if (value instanceof JsonList list) return new Permissions(list.toStringList());
    if (value instanceof Number n) return new Permissions(n.intValue());
    throw new IllegalArgumentException("Permissions required");
  }

  private static void populateBase(BaseCaInfo b, JsonMap m) throws Exception {
    // Optional fields retain the XiPKI 7.0 defaults represented by BaseCaInfo.
    for (String name : List.of("expirationPeriod", "keepExpiredCertDays", "numCrls", "snSize")) {
      Integer v = m.getInt(name);
      if (v != null)
        b.getClass()
            .getMethod("set" + Character.toUpperCase(name.charAt(0)) + name.substring(1), int.class)
            .invoke(b, v);
    }
    if (m.getLong("nextCrlNo") != null) b.setNextCrlNo(m.getLong("nextCrlNo"));
    if (m.getBool("saveCert") != null) b.setSaveCert(m.getBool("saveCert"));
    if (m.getBool("saveKeypair") != null) b.setSaveKeypair(m.getBool("saveKeypair"));
    b.setMaxValidity(Validity.getInstance(m.getNnString("maxValidity")));
    if (m.getString("status") != null) b.setStatus(CaStatus.forName(m.getString("status")));
    if (m.getString("validityMode") != null)
      b.setValidityMode(ValidityMode.forName(m.getString("validityMode")));
    b.setKeypairGenNames(m.getStringList("keypairGenNames"));
    b.setCrlSignerName(m.getString("crlSignerName"));
    JsonMap uris = m.getMap("caUris");
    if (uris != null)
      b.setCaUris(
          new CaUris(
              uris.getStringList("cacertUris"),
              uris.getStringList("ocspUris"),
              uris.getStringList("crlUris"),
              uris.getStringList("deltaCrlUris")));
    if (m.getMap("crlControl") != null)
      b.setCrlControl(new CrlControl(ConfPairs.parse(m.getMap("crlControl")).getEncoded()));
    if (m.getMap("ctlogControl") != null)
      b.setCtlogControl(new CtlogControl(ConfPairs.parse(m.getMap("ctlogControl")).getEncoded()));
    if (m.getMap("revokeSuspendedControl") != null)
      b.setRevokeSuspendedControl(
          new RevokeSuspendedControl(
              ConfPairs.parse(m.getMap("revokeSuspendedControl")).getEncoded()));
    b.setExtraControl(ConfPairs.parse(m.getMap("extraControl")));
    Object rev = m.getObject("revocationInfo");
    if (rev instanceof JsonMap map) b.setRevocationInfo(revocation(map));
    else if (rev instanceof String text) {
      var p = new ConfPairs(text);
      b.setRevocationInfo(
          new CertRevocationInfo(
              CrlReason.forNameOrText(p.value("reason")),
              Instant.ofEpochSecond(Long.parseLong(p.value("revocationTime"))),
              p.value("invalidityTime") == null
                  ? null
                  : Instant.ofEpochSecond(Long.parseLong(p.value("invalidityTime")))));
    }
  }

  private static CertRevocationInfo revocation(JsonMap m) throws Exception {
    return m == null
        ? null
        : new CertRevocationInfo(
            CrlReason.forNameOrText(m.getNnString("reason")),
            m.getNnInstant("revocationTime"),
            m.getInstant("invalidityTime"));
  }

  public static Map<String, String> stringMap(JsonMap response) throws CaMgmtException {
    try {
      return response.getStringMap("result");
    } catch (Exception ex) {
      throw new CaMgmtException("Invalid management string-map response", ex);
    }
  }

  public static <T> List<T> list(JsonMap response, String type) throws CaMgmtException {
    try {
      return decodeList(response, type);
    } catch (Exception ex) {
      throw new CaMgmtException("Invalid management list response", ex);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> List<T> decodeList(JsonMap response, String type) throws Exception {
    if (type.equals("X509Cert")) {
      String pem = response.getString("result");
      return pem == null
          ? List.of()
          : (List<T>) X509Util.parseCerts(pem.getBytes(StandardCharsets.US_ASCII));
    }
    var result = new ArrayList<T>();
    JsonList list = response.getList("result");
    if (list == null) return result;
    for (int i = 0; i < list.size(); i++) {
      Object item = list.getAt(i), value;
      if (type.equals("String")) value = (String) item;
      else if (type.equals("CaProfileEntry")) value = CaProfileEntry.decode((String) item);
      else {
        JsonMap m = (JsonMap) item;
        value =
            switch (type) {
              case "CertListInfo" ->
                  new CertListInfo(
                      m.getNnBigInteger("serialNumber"),
                      m.getNnString("subject"),
                      m.getNnInstant("notBefore"),
                      m.getNnInstant("notAfter"));
              case "CaHasRequestorEntry" ->
                  new CaHasRequestorEntry(
                      ident(m.getNnMap("requestorIdent")),
                      permissions(m.getObject("permissions")),
                      m.getStringList("profiles"));
              default -> throw new IllegalArgumentException("Unsupported list response");
            };
      }
      result.add((T) value);
    }
    return result;
  }

  public static <T> Set<T> set(JsonMap response, String type) throws CaMgmtException {
    return new LinkedHashSet<>(ManagementProtocol.<T>list(response, type));
  }
}
