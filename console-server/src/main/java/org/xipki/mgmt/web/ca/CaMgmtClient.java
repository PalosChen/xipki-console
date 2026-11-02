// Copyright (c) 2026 trustasia-com. All rights reserved.
// License Apache License 2.0

package org.xipki.mgmt.web.ca;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import javax.security.auth.x500.X500Principal;
import org.xipki.codec.json.JsonMap;
import org.xipki.mgmt.web.ca.CaDomain.*;
import org.xipki.mgmt.web.ca.Crypto.*;

/**
 * Explicit operation-to-wire mapping, with no retry of mutations.
 *
 * @author palos
 */
public class CaMgmtClient implements CaManager {
  private final ManagementProtocol protocol;

  public CaMgmtClient(ManagementProtocol protocol) {
    this.protocol = protocol;
  }

  public CaMgmtClient(String url, Path keyStore, char[] password, Path trust) throws Exception {
    this(ManagementProtocol.https(url, keyStore, password, trust));
  }

  private <T> T call(String action, Class<T> type, Object... pairs) throws CaMgmtException {
    return protocol.call(action, type, pairs);
  }

  @Override
  public CaSystemStatus getCaSystemStatus() throws CaMgmtException {
    return call("getCaSystemStatus", CaSystemStatus.class);
  }

  @Override
  public void unlockCa() throws CaMgmtException {
    call("unlockCa", Void.class);
  }

  @Override
  public void notifyCaChange() throws CaMgmtException {
    call("notifyCaChange", Void.class);
  }

  @Override
  public void addDbSchema(String name, String value) throws CaMgmtException {
    call("addDbSchema", Void.class, "name", name, "value", value);
  }

  @Override
  public void changeDbSchema(String name, String value) throws CaMgmtException {
    call("changeDbSchema", Void.class, "name", name, "value", value);
  }

  @Override
  public void removeDbSchema(String name) throws CaMgmtException {
    call("removeDbSchema", Void.class, "name", name);
  }

  @Override
  public Map<String, String> getDbSchemas() throws CaMgmtException {
    return ManagementProtocol.stringMap(call("getDbSchemas", JsonMap.class));
  }

  @Override
  public void republishCertificates(String caName, List<String> publisherNames, int numThreads)
      throws CaMgmtException {
    call(
        "republishCertificates",
        Void.class,
        "caName",
        caName,
        "publisherNames",
        publisherNames,
        "numThreads",
        numThreads);
  }

  @Override
  public void removeCa(String caName) throws CaMgmtException {
    call("removeCa", Void.class, "name", caName);
  }

  @Override
  public void restartCa(String caName) throws CaMgmtException {
    call("restartCa", Void.class, "name", caName);
  }

  @Override
  public void restartCaSystem() throws CaMgmtException {
    call("restartCaSystem", Void.class);
  }

  @Override
  public void addCaAlias(String aliasName, String caName) throws CaMgmtException {
    call("addCaAlias", Void.class, "caName", caName, "aliasName", aliasName);
  }

  @Override
  public void removeCaAlias(String aliasName) throws CaMgmtException {
    call("removeCaAlias", Void.class, "name", aliasName);
  }

  @Override
  public Set<String> getAliasesForCa(String caName) throws CaMgmtException {
    return ManagementProtocol.set(call("getAliasesForCa", JsonMap.class, "name", caName), "String");
  }

  @Override
  public String getCaNameForAlias(String aliasName) throws CaMgmtException {
    return call("getCaNameForAlias", String.class, "name", aliasName);
  }

  @Override
  public Set<String> getCaAliasNames() throws CaMgmtException {
    return ManagementProtocol.set(call("getCaAliasNames", JsonMap.class), "String");
  }

  @Override
  public Set<String> getCertprofileNames() throws CaMgmtException {
    return ManagementProtocol.set(call("getCertprofileNames", JsonMap.class), "String");
  }

  @Override
  public Set<String> getKeypairGenNames() throws CaMgmtException {
    return ManagementProtocol.set(call("getKeypairGenNames", JsonMap.class), "String");
  }

  @Override
  public Set<String> getPublisherNames() throws CaMgmtException {
    return ManagementProtocol.set(call("getPublisherNames", JsonMap.class), "String");
  }

  @Override
  public Set<String> getRequestorNames() throws CaMgmtException {
    return ManagementProtocol.set(call("getRequestorNames", JsonMap.class), "String");
  }

  @Override
  public Set<String> getSignerNames() throws CaMgmtException {
    return ManagementProtocol.set(call("getSignerNames", JsonMap.class), "String");
  }

  @Override
  public Set<String> getCaNames() throws CaMgmtException {
    return ManagementProtocol.set(call("getCaNames", JsonMap.class), "String");
  }

  @Override
  public Set<String> getSuccessfulCaNames() throws CaMgmtException {
    return ManagementProtocol.set(call("getSuccessfulCaNames", JsonMap.class), "String");
  }

  @Override
  public Set<String> getFailedCaNames() throws CaMgmtException {
    return ManagementProtocol.set(call("getFailedCaNames", JsonMap.class), "String");
  }

  @Override
  public Set<String> getInactiveCaNames() throws CaMgmtException {
    return ManagementProtocol.set(call("getInactiveCaNames", JsonMap.class), "String");
  }

  @Override
  public void addCa(CaEntry caEntry) throws CaMgmtException {
    call("addCa", Void.class, "caEntry", caEntry);
  }

  @Override
  public CaEntry getCa(String caName) throws CaMgmtException {
    return call("getCa", CaEntry.class, "name", caName);
  }

  @Override
  public void changeCa(ChangeCaEntry changeCaEntry) throws CaMgmtException {
    call("changeCa", Void.class, "changeCaEntry", changeCaEntry);
  }

  @Override
  public void removeCertprofileFromCa(String profileName, String caName) throws CaMgmtException {
    call("removeCertprofileFromCa", Void.class, "caName", caName, "entityName", profileName);
  }

  @Override
  public void addCertprofileToCa(String profileNameAliases, String caName) throws CaMgmtException {
    call("addCertprofileToCa", Void.class, "caName", caName, "profileName", profileNameAliases);
  }

  @Override
  public void removePublisherFromCa(String publisherName, String caName) throws CaMgmtException {
    call("removePublisherFromCa", Void.class, "caName", caName, "entityName", publisherName);
  }

  @Override
  public void addPublisherToCa(String publisherName, String caName) throws CaMgmtException {
    call("addPublisherToCa", Void.class, "caName", caName, "publisherName", publisherName);
  }

  @Override
  public Set<CaProfileEntry> getCertprofilesForCa(String caName) throws CaMgmtException {
    return ManagementProtocol.set(
        call("getCertprofilesForCa", JsonMap.class, "name", caName), "CaProfileEntry");
  }

  @Override
  public Set<CaHasRequestorEntry> getRequestorsForCa(String caName) throws CaMgmtException {
    return ManagementProtocol.set(
        call("getRequestorsForCa", JsonMap.class, "name", caName), "CaHasRequestorEntry");
  }

  @Override
  public RequestorEntry getRequestor(String name) throws CaMgmtException {
    return call("getRequestor", RequestorEntry.class, "name", name);
  }

  @Override
  public void addRequestor(RequestorEntry requestorEntry) throws CaMgmtException {
    call("addRequestor", Void.class, "requestorEntry", requestorEntry);
  }

  @Override
  public void removeRequestor(String requestorName) throws CaMgmtException {
    call("removeRequestor", Void.class, "name", requestorName);
  }

  @Override
  public void changeRequestor(String name, String type, String conf) throws CaMgmtException {
    call("changeRequestor", Void.class, "name", name, "type", type, "conf", conf);
  }

  @Override
  public void removeRequestorFromCa(String requestorName, String caName) throws CaMgmtException {
    call("removeRequestorFromCa", Void.class, "caName", caName, "entityName", requestorName);
  }

  @Override
  public void addRequestorToCa(CaHasRequestorEntry requestor, String caName)
      throws CaMgmtException {
    call("addRequestorToCa", Void.class, "caName", caName, "requestor", requestor);
  }

  @Override
  public CertprofileEntry getCertprofile(String profileName) throws CaMgmtException {
    return call("getCertprofile", CertprofileEntry.class, "name", profileName);
  }

  @Override
  public void removeCertprofile(String profileName) throws CaMgmtException {
    call("removeCertprofile", Void.class, "name", profileName);
  }

  @Override
  public void changeCertprofile(String name, String type, String conf) throws CaMgmtException {
    call("changeCertprofile", Void.class, "name", name, "type", type, "conf", conf);
  }

  @Override
  public void addCertprofile(CertprofileEntry certprofileEntry) throws CaMgmtException {
    call("addCertprofile", Void.class, "certprofileEntry", certprofileEntry);
  }

  @Override
  public KeypairGenEntry getKeypairGen(String name) throws CaMgmtException {
    return call("getKeypairGen", KeypairGenEntry.class, "name", name);
  }

  @Override
  public void removeKeypairGen(String name) throws CaMgmtException {
    call("removeKeypairGen", Void.class, "name", name);
  }

  @Override
  public void changeKeypairGen(String name, String type, String conf) throws CaMgmtException {
    call("changeKeypairGen", Void.class, "name", name, "type", type, "conf", conf);
  }

  @Override
  public void addKeypairGen(KeypairGenEntry keypairGenEntry) throws CaMgmtException {
    call("addKeypairGen", Void.class, "entry", keypairGenEntry);
  }

  @Override
  public void addSigner(SignerEntry signerEntry) throws CaMgmtException {
    call("addSigner", Void.class, "signerEntry", signerEntry);
  }

  @Override
  public void removeSigner(String name) throws CaMgmtException {
    call("removeSigner", Void.class, "name", name);
  }

  @Override
  public SignerEntry getSigner(String name) throws CaMgmtException {
    return call("getSigner", SignerEntry.class, "name", name);
  }

  @Override
  public void changeSigner(String name, String type, String conf, String base64Cert)
      throws CaMgmtException {
    call(
        "changeSigner",
        Void.class,
        "name",
        name,
        "type",
        type,
        "conf",
        conf,
        "base64Cert",
        base64Cert);
  }

  @Override
  public void addPublisher(PublisherEntry entry) throws CaMgmtException {
    call("addPublisher", Void.class, "publisherEntry", entry);
  }

  @Override
  public Set<String> getPublisherNamesForCa(String caName) throws CaMgmtException {
    return ManagementProtocol.set(
        call("getPublisherNamesForCa", JsonMap.class, "name", caName), "String");
  }

  @Override
  public PublisherEntry getPublisher(String publisherName) throws CaMgmtException {
    return call("getPublisher", PublisherEntry.class, "name", publisherName);
  }

  @Override
  public void removePublisher(String publisherName) throws CaMgmtException {
    call("removePublisher", Void.class, "name", publisherName);
  }

  @Override
  public void changePublisher(String name, String type, String conf) throws CaMgmtException {
    call("changePublisher", Void.class, "name", name, "type", type, "conf", conf);
  }

  @Override
  public void revokeCa(String caName, CertRevocationInfo revocationInfo) throws CaMgmtException {
    call("revokeCa", Void.class, "caName", caName, "revocationInfo", revocationInfo);
  }

  @Override
  public void unrevokeCa(String caName) throws CaMgmtException {
    call("unrevokeCa", Void.class, "name", caName);
  }

  @Override
  public void revokeCertificate(
      String caName, BigInteger serialNumber, CrlReason reason, Instant invalidityTime)
      throws CaMgmtException {
    call(
        "revokeCertificate",
        Void.class,
        "caName",
        caName,
        "serialNumber",
        serialNumber,
        "reason",
        reason,
        "invalidityTime",
        invalidityTime);
  }

  @Override
  public void unsuspendCertificate(String caName, BigInteger serialNumber) throws CaMgmtException {
    call("unsuspendCertificate", Void.class, "caName", caName, "serialNumber", serialNumber);
  }

  @Override
  public void removeCertificate(String caName, BigInteger serialNumber) throws CaMgmtException {
    call("removeCertificate", Void.class, "caName", caName, "serialNumber", serialNumber);
  }

  @Override
  public X509Cert generateCertificate(
      String caName, String profileName, byte[] encodedCsr, Instant notBefore, Instant notAfter)
      throws CaMgmtException {
    return call(
        "generateCertificate",
        X509Cert.class,
        "caName",
        caName,
        "profileName",
        profileName,
        "encodedCsr",
        encodedCsr,
        "notBefore",
        notBefore,
        "notAfter",
        notAfter);
  }

  @Override
  public KeyCertBytesPair generateKeyCert(
      String caName, String profileName, String subject, Instant notBefore, Instant notAfter)
      throws CaMgmtException {
    return call(
        "generateKeyCert",
        KeyCertBytesPair.class,
        "caName",
        caName,
        "profileName",
        profileName,
        "subject",
        subject,
        "notBefore",
        notBefore,
        "notAfter",
        notAfter);
  }

  @Override
  public X509Cert generateCrossCertificate(
      String caName,
      String profileName,
      byte[] encodedCsr,
      byte[] encodedTargetCert,
      Instant notBefore,
      Instant notAfter)
      throws CaMgmtException {
    return call(
        "generateCrossCertificate",
        X509Cert.class,
        "caName",
        caName,
        "profileName",
        profileName,
        "encodedCsr",
        encodedCsr,
        "encodedTargetCert",
        encodedTargetCert,
        "notBefore",
        notBefore,
        "notAfter",
        notAfter);
  }

  @Override
  public X509Cert generateRootCa(
      CaEntry caEntry,
      String certprofileName,
      String subject,
      String serialNumber,
      Instant notBefore,
      Instant notAfter)
      throws CaMgmtException {
    return call(
        "generateRootCa",
        X509Cert.class,
        "caEntry",
        caEntry,
        "profileName",
        certprofileName,
        "subject",
        subject,
        "serialNumber",
        serialNumber,
        "notBefore",
        notBefore,
        "notAfter",
        notAfter);
  }

  @Override
  public X509Crl generateCrlOnDemand(String caName) throws CaMgmtException {
    return call("generateCrlOnDemand", X509Crl.class, "name", caName);
  }

  @Override
  public X509Crl getCrl(String caName, long crlNumber) throws CaMgmtException {
    return call("getCrl", X509Crl.class, "caName", caName, "crlNumber", crlNumber);
  }

  @Override
  public X509Crl getCurrentCrl(String caName) throws CaMgmtException {
    return call("getCurrentCrl", X509Crl.class, "name", caName);
  }

  @Override
  public List<X509Cert> getCaCerts(String caName) throws CaMgmtException {
    return ManagementProtocol.list(call("getCaCerts", JsonMap.class, "name", caName), "X509Cert");
  }

  @Override
  public CertWithRevocationInfo getCert(String caName, BigInteger serialNumber)
      throws CaMgmtException {
    return call(
        "getCert", CertWithRevocationInfo.class, "caName", caName, "serialNumber", serialNumber);
  }

  @Override
  public void loadConfAndClose(InputStream zippedConfStream) throws CaMgmtException, IOException {
    try (zippedConfStream) {
      call(
          "loadConf",
          Void.class,
          "confBytes",
          zippedConfStream.readNBytes(ManagementProtocol.RESPONSE_LIMIT + 1));
    }
  }

  @Override
  public InputStream exportConf(List<String> caNames) throws CaMgmtException, IOException {
    return new ByteArrayInputStream(call("exportConf", byte[].class, "caNames", caNames));
  }

  @Override
  public List<CertListInfo> listCertificates(
      String caName,
      X500Principal subjectPattern,
      Instant validFrom,
      Instant validTo,
      CertListOrderBy orderBy,
      int numEntries)
      throws CaMgmtException {
    return ManagementProtocol.list(
        call(
            "listCertificates",
            JsonMap.class,
            "caName",
            caName,
            "encodedSubjectDnPattern",
            subjectPattern == null ? null : subjectPattern.getEncoded(),
            "validFrom",
            validFrom,
            "validTo",
            validTo,
            "orderBy",
            orderBy == null ? null : orderBy.getText(),
            "numEntries",
            numEntries),
        "CertListInfo");
  }

  @Override
  public Set<String> getSupportedSignerTypes() throws CaMgmtException {
    return ManagementProtocol.set(call("getSupportedSignerTypes", JsonMap.class), "String");
  }

  @Override
  public Set<String> getSupportedCertprofileTypes() throws CaMgmtException {
    return ManagementProtocol.set(call("getSupportedCertprofileTypes", JsonMap.class), "String");
  }

  @Override
  public Set<String> getSupportedPublisherTypes() throws CaMgmtException {
    return ManagementProtocol.set(call("getSupportedPublisherTypes", JsonMap.class), "String");
  }

  @Override
  public String getTokenInfoP11(String moduleName, Integer slotIndex, boolean verbose)
      throws CaMgmtException {
    return call(
        "getTokenInfoP11",
        String.class,
        "moduleName",
        moduleName,
        "slotIndex",
        slotIndex,
        "verbose",
        verbose);
  }
}
