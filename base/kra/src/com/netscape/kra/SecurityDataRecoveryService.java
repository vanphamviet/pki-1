// --- BEGIN COPYRIGHT BLOCK ---
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; version 2 of the License.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, write to the Free Software Foundation, Inc.,
// 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
//
// (C) 2007 Red Hat, Inc.
// All rights reserved.
// --- END COPYRIGHT BLOCK ---
package com.netscape.kra;

import java.io.ByteArrayOutputStream;
import java.io.CharConversionException;
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Random;

import javax.crypto.spec.RC2ParameterSpec;

import netscape.security.util.DerValue;
import netscape.security.x509.X509Key;

import org.dogtagpki.server.kra.rest.KeyRequestService;
import org.mozilla.jss.CryptoManager;
import org.mozilla.jss.asn1.OCTET_STRING;
import org.mozilla.jss.crypto.Cipher;
import org.mozilla.jss.crypto.CryptoToken;
import org.mozilla.jss.crypto.EncryptionAlgorithm;
import org.mozilla.jss.crypto.IVParameterSpec;
import org.mozilla.jss.crypto.KeyGenerator;
import org.mozilla.jss.crypto.KeyWrapAlgorithm;
import org.mozilla.jss.crypto.KeyWrapper;
import org.mozilla.jss.crypto.PBEAlgorithm;
import org.mozilla.jss.crypto.PBEKeyGenParams;
import org.mozilla.jss.crypto.PrivateKey;
import org.mozilla.jss.crypto.SymmetricKey;
import org.mozilla.jss.crypto.TokenException;
import org.mozilla.jss.pkcs12.PasswordConverter;
import org.mozilla.jss.pkcs7.ContentInfo;
import org.mozilla.jss.pkcs7.EncryptedContentInfo;
import org.mozilla.jss.pkix.primitive.AlgorithmIdentifier;
import org.mozilla.jss.pkix.primitive.PBEParameter;
import org.mozilla.jss.util.Password;

import com.netscape.certsrv.apps.CMS;
import com.netscape.certsrv.base.EBaseException;
import com.netscape.certsrv.base.IConfigStore;
import com.netscape.certsrv.dbs.keydb.IKeyRecord;
import com.netscape.certsrv.dbs.keydb.IKeyRepository;
import com.netscape.certsrv.key.KeyRequestResource;
import com.netscape.certsrv.kra.EKRAException;
import com.netscape.certsrv.kra.IKeyRecoveryAuthority;
import com.netscape.certsrv.logging.ILogger;
import com.netscape.certsrv.request.IRequest;
import com.netscape.certsrv.request.IService;
import com.netscape.certsrv.request.RequestId;
import com.netscape.certsrv.security.IStorageKeyUnit;
import com.netscape.certsrv.security.ITransportKeyUnit;
import com.netscape.cmscore.dbs.KeyRecord;
import com.netscape.cmsutil.util.Utils;

/**
 * This implementation services SecurityData Recovery requests.
 * <p>
 *
 * @version $Revision$, $Date$
 */
@SuppressWarnings("deprecation")
public class SecurityDataRecoveryService implements IService {

    private IKeyRecoveryAuthority mKRA = null;

    private IKeyRepository mStorage = null;
    private IStorageKeyUnit mStorageUnit = null;
    private ITransportKeyUnit mTransportUnit = null;
    private ILogger signedAuditLogger = CMS.getSignedAuditLogger();

    private final static String LOGGING_SIGNED_AUDIT_SECURITY_DATA_RECOVERY_REQUEST_PROCESSED =
            "LOGGING_SIGNED_AUDIT_SECURITY_DATA_RECOVERY_REQUEST_PROCESSED_5";
    public static final String ATTR_SERIALNO = "serialNumber";
    public static final String ATTR_KEY_RECORD = "keyRecord";
    private static boolean allowEncDecrypt_recovery = false;

    public SecurityDataRecoveryService(IKeyRecoveryAuthority kra) {
        mKRA = kra;
        mStorage = mKRA.getKeyRepository();
        mStorageUnit = mKRA.getStorageKeyUnit();
        mTransportUnit = mKRA.getTransportKeyUnit();

    }

    /**
     * Performs the service (such as certificate generation)
     * represented by this request.
     * <p>
     *
     * @param request
     *            The SecurityData recovery request that needs service. The service may use
     *            attributes stored in the request, and may update the
     *            values, or store new ones.
     * @return
     *         an indication of whether this request is still pending.
     *         'false' means the request will wait for further notification.
     * @exception EBaseException indicates major processing failure.
     */
    public boolean serviceRequest(IRequest request)
            throws EBaseException {

        CMS.debug("SecurityDataRecoveryService.serviceRequest()");

        //Pave the way for allowing generated IV vector
        byte iv[]= null;
        byte iv_default[] = { 0x1, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1 };
        byte iv_in[] = null;

        IConfigStore config = null;

        try {
            config = CMS.getConfigStore();
            allowEncDecrypt_recovery = config.getBoolean("kra.allowEncDecrypt.recovery", false);
        } catch (Exception e) {
            throw new EBaseException(CMS.getUserMessage("CMS_BASE_CERT_ERROR", e.toString()));
        }

        String requestor = request.getExtDataInString(IRequest.ATTR_REQUEST_OWNER);
        String auditSubjectID = requestor;

        Hashtable<String, Object> params = mKRA.getVolatileRequest(
                request.getRequestId());
        BigInteger serialno = request.getExtDataInBigInteger(ATTR_SERIALNO);
        request.setExtData(ATTR_KEY_RECORD, serialno);
        RequestId requestID = request.getRequestId();

        if (params == null) {
            CMS.debug("SecurityDataRecoveryService: Can't get volatile params.");
            auditRecoveryRequestProcessed(auditSubjectID, ILogger.FAILURE, requestID, serialno.toString(),
                    "cannot get volatile params");
            throw new EBaseException("Can't obtain volatile params!");
        }

        String transWrappedSessKeyStr = (String) params.get(IRequest.SECURITY_DATA_TRANS_SESS_KEY);
        byte[] wrappedSessKey = null;
        if (transWrappedSessKeyStr != null) {
            wrappedSessKey = Utils.base64decode(transWrappedSessKeyStr);
        }

        String sessWrappedPassPhraseStr = (String) params.get(IRequest.SECURITY_DATA_SESS_PASS_PHRASE);
        byte[] wrappedPassPhrase = null;
        if (sessWrappedPassPhraseStr != null) {
            wrappedPassPhrase = Utils.base64decode(sessWrappedPassPhraseStr);
        }

        String ivInStr = (String) params.get(IRequest.SECURITY_DATA_IV_STRING_IN);
        if (ivInStr != null) {
            iv_in = Utils.base64decode(ivInStr);
        }

        if (transWrappedSessKeyStr == null && sessWrappedPassPhraseStr == null) {
            //We may be in recovery case where no params were initially submitted.
            CMS.debug("SecurityDataRecoveryService: No params provided.");
            return false;
        }

        //Create the return IV if needed.
        iv = new byte[8];

        try {
            Random rnd = new Random();
            rnd.nextBytes(iv);
        } catch (Exception e) {
            iv = iv_default;
        }
        String ivStr = Utils.base64encode(iv);

        KeyRecord keyRecord = (KeyRecord) mStorage.readKeyRecord(serialno);

        String dataType = (String) keyRecord.get(IKeyRecord.ATTR_DATA_TYPE);
        if (dataType == null) dataType = KeyRequestResource.ASYMMETRIC_KEY_TYPE;

        SymmetricKey unwrappedSess = null;
        SymmetricKey symKey = null;
        byte[] unwrappedSecData = null;
        PrivateKey privateKey = null;

        if (dataType.equals(KeyRequestResource.SYMMETRIC_KEY_TYPE)) {
            if (allowEncDecrypt_recovery == true) {
                CMS.debug("Recover symmetric key by decrypting as per allowEncDecrypt_recovery: true.");
                unwrappedSecData = recoverSecurityData(keyRecord);
            } else {
                symKey = recoverSymKey(keyRecord);
            }

        } else if (dataType.equals(KeyRequestResource.PASS_PHRASE_TYPE)) {
            unwrappedSecData = recoverSecurityData(keyRecord);
        } else if (dataType.equals(KeyRequestResource.ASYMMETRIC_KEY_TYPE)) {
            try {
                if (allowEncDecrypt_recovery == true) {
                    CMS.debug("Recover asymmetric key by decrypting as per allowEncDecrypt_recovery: true.");
                    unwrappedSecData = recoverSecurityData(keyRecord);
                } else {
                    privateKey = mStorageUnit.unwrap(keyRecord.getPrivateKeyData(),
                            X509Key.parsePublicKey(new DerValue(keyRecord.getPublicKeyData())));
                }

            } catch (IOException e) {
                throw new EBaseException("Cannot fetch the private key from the database.", e);
            }

        } else {
            throw new EBaseException("Invalid data type stored in the database.");
        }

        CryptoToken ct = mTransportUnit.getToken();

        byte[] key_data = null;
        String pbeWrappedData = null;

        if (sessWrappedPassPhraseStr != null) {
            CMS.debug("SecurityDataRecoveryService: secure retrieved data with tranport passphrase");
            byte[] unwrappedPass = null;
            Password pass = null;

            try {
                unwrappedSess = mTransportUnit.unwrap_sym(wrappedSessKey, SymmetricKey.Usage.DECRYPT);
                Cipher decryptor = ct.getCipherContext(EncryptionAlgorithm.DES3_CBC_PAD);
                decryptor.initDecrypt(unwrappedSess, new IVParameterSpec(iv_in));
                unwrappedPass = decryptor.doFinal(wrappedPassPhrase);
                String passStr = new String(unwrappedPass, "UTF-8");
                pass = new Password(passStr.toCharArray());
                passStr = null;

                if (dataType.equals(KeyRequestResource.SYMMETRIC_KEY_TYPE)) {
                    CMS.debug("SecurityDataRecoveryService: wrap or encrypt stored symmetric key with transport passphrase");
                    if (allowEncDecrypt_recovery == true) {
                        CMS.debug("SecurityDataRecoveryServic: allowEncDecyypt_recovery: true, symmetric key:  create blob with unwrapped key.");
                        pbeWrappedData = createEncryptedContentInfo(ct, null, unwrappedSecData, null, pass);
                    } else {
                        pbeWrappedData = createEncryptedContentInfo(ct, symKey, null, null,
                                pass);
                    }
                } else if (dataType.equals(KeyRequestResource.PASS_PHRASE_TYPE)) {
                    CMS.debug("SecurityDataRecoveryService: encrypt stored passphrase with transport passphrase");
                    pbeWrappedData = createEncryptedContentInfo(ct, null, unwrappedSecData, null,
                            pass);
                } else if (dataType.equals(KeyRequestResource.ASYMMETRIC_KEY_TYPE)) {
                    if (allowEncDecrypt_recovery == true) {
                        CMS.debug("SecurityDataRecoveryService: allowEncDecyypt_recovery: true, asymmetric key:  create blob with unwrapped key.");
                        pbeWrappedData = createEncryptedContentInfo(ct, null, unwrappedSecData, null, pass);
                    } else {
                        CMS.debug("SecurityDataRecoveryService: wrap stored private key with transport passphrase");
                        pbeWrappedData = createEncryptedContentInfo(ct, null, null, privateKey,
                                pass);
                    }
                }

                params.put(IRequest.SECURITY_DATA_PASS_WRAPPED_DATA, pbeWrappedData);

            } catch (Exception e) {
                auditRecoveryRequestProcessed(auditSubjectID, ILogger.FAILURE, requestID, serialno.toString(),
                        "Cannot unwrap passphrase");
                throw new EBaseException("Cannot unwrap passphrase: " + e, e);

            } finally {
                if (pass != null) {
                    pass.clear();
                }

                if (unwrappedPass != null) {
                    java.util.Arrays.fill(unwrappedPass, (byte) 0);
                }
            }

        } else {
            CMS.debug("SecurityDataRecoveryService: secure retrieved data with session key");

            if (dataType.equals(KeyRequestResource.SYMMETRIC_KEY_TYPE)) {
                CMS.debug("SecurityDataRecoveryService: wrap or encrypt stored symmetric key with session key");
                try {
                    if (allowEncDecrypt_recovery == true) {
                        CMS.debug("SecurityDataRecoveryService: encrypt symmetric key with session key as per allowEncDecrypt_recovery: true.");
                        unwrappedSess = mTransportUnit.unwrap_sym(wrappedSessKey, SymmetricKey.Usage.ENCRYPT);
                        Cipher encryptor = ct.getCipherContext(EncryptionAlgorithm.DES3_CBC_PAD);
                        if (encryptor != null) {
                            encryptor.initEncrypt(unwrappedSess, new IVParameterSpec(iv));
                            key_data = encryptor.doFinal(unwrappedSecData);
                        } else {
                            auditRecoveryRequestProcessed(auditSubjectID, ILogger.FAILURE, requestID,
                                    serialno.toString(), "Failed to create cipher encrypting symmetric key");
                            throw new IOException("Failed to create cipher encryping symmetric key");
                        }

                    } else {
                        unwrappedSess = mTransportUnit.unwrap_sym(wrappedSessKey, SymmetricKey.Usage.WRAP);
                        KeyWrapper wrapper = ct.getKeyWrapper(KeyWrapAlgorithm.DES3_CBC_PAD);
                        wrapper.initWrap(unwrappedSess, new IVParameterSpec(iv));
                        key_data = wrapper.wrap(symKey);
                    }

                } catch (Exception e) {
                    auditRecoveryRequestProcessed(auditSubjectID, ILogger.FAILURE, requestID, serialno.toString(),
                            "Cannot wrap symmetric key");
                    throw new EBaseException("Cannot wrap symmetric key: " + e, e);
                }

            } else if (dataType.equals(KeyRequestResource.PASS_PHRASE_TYPE)) {
                CMS.debug("SecurityDataRecoveryService: encrypt stored passphrase with session key");
                try {
                    unwrappedSess = mTransportUnit.unwrap_sym(wrappedSessKey, SymmetricKey.Usage.ENCRYPT);
                    Cipher encryptor = ct.getCipherContext(EncryptionAlgorithm.DES3_CBC_PAD);
                    if (encryptor != null) {
                        encryptor.initEncrypt(unwrappedSess, new IVParameterSpec(iv));
                        key_data = encryptor.doFinal(unwrappedSecData);

                    } else {
                        auditRecoveryRequestProcessed(auditSubjectID, ILogger.FAILURE, requestID,
                                serialno.toString(), "Failed to create cipher");
                        throw new IOException("Failed to create cipher");
                    }

                } catch (Exception e) {
                    auditRecoveryRequestProcessed(auditSubjectID, ILogger.FAILURE, requestID,
                            serialno.toString(), "Cannot encrypt passphrase");
                    throw new EBaseException("Cannot encrypt passphrase: " + e, e);
                }

            } else if (dataType.equals(KeyRequestResource.ASYMMETRIC_KEY_TYPE)) {
                CMS.debug("SecurityDataRecoveryService: wrap or encrypt stored private key with session key");
                try {
                    if (allowEncDecrypt_recovery == true) {
                        CMS.debug("SecurityDataRecoveryService: encrypt symmetric key with session key as per allowEncDecrypt_recovery: true.");
                        unwrappedSess = mTransportUnit.unwrap_sym(wrappedSessKey, SymmetricKey.Usage.ENCRYPT);
                        Cipher encryptor = ct.getCipherContext(EncryptionAlgorithm.DES3_CBC_PAD);
                        if (encryptor != null) {
                            encryptor.initEncrypt(unwrappedSess, new IVParameterSpec(iv));
                            key_data = encryptor.doFinal(unwrappedSecData);
                        } else {
                            auditRecoveryRequestProcessed(auditSubjectID, ILogger.FAILURE, requestID,
                                    serialno.toString(), "Failed to create cipher encrypting asymmetric key");
                            throw new IOException("Failed to create cipher encrypting asymmetric key");
                        }

                    } else {
                        unwrappedSess = mTransportUnit.unwrap_sym(wrappedSessKey, SymmetricKey.Usage.WRAP);
                        KeyWrapper wrapper = ct.getKeyWrapper(KeyWrapAlgorithm.DES3_CBC_PAD);
                        wrapper.initWrap(unwrappedSess, new IVParameterSpec(iv));
                        key_data = wrapper.wrap(privateKey);
                    }

                } catch (Exception e) {
                    auditRecoveryRequestProcessed(auditSubjectID, ILogger.FAILURE, requestID, serialno.toString(),
                            "Cannot wrap private key");
                    throw new EBaseException("Cannot wrap private key: " + e, e);
                }
            }

            String wrappedKeyData = Utils.base64encode(key_data);
            params.put(IRequest.SECURITY_DATA_SESS_WRAPPED_DATA, wrappedKeyData);
            params.put(IRequest.SECURITY_DATA_IV_STRING_OUT, ivStr);
        }

        if(unwrappedSecData != null && unwrappedSecData.length > 0) {
            Arrays.fill(unwrappedSecData, (byte)0);
        }

        auditRecoveryRequestProcessed(auditSubjectID, ILogger.SUCCESS, requestID, serialno.toString(),
                "None");
        request.setExtData(IRequest.RESULT, IRequest.RES_SUCCESS);
        mKRA.getRequestQueue().updateRequest(request);

        return false; //return true ? TODO
    }

    public SymmetricKey recoverSymKey(KeyRecord keyRecord)
            throws EBaseException {

        try {
            SymmetricKey symKey =
                    mStorageUnit.unwrap(
                            keyRecord.getPrivateKeyData(),
                            KeyRequestService.SYMKEY_TYPES.get(keyRecord.getAlgorithm()),
                            keyRecord.getKeySize());

            if (symKey == null) {
                throw new EKRAException(CMS.getUserMessage("CMS_KRA_RECOVERY_FAILED_1",
                        "symmetric key unwrapping failure"));
            }

            return symKey;
        } catch (Exception e) {

            throw new EKRAException(CMS.getUserMessage("CMS_KRA_RECOVERY_FAILED_1",
                    "recoverSymKey() " + e.toString()));
        }
    }

    public byte[] recoverSecurityData(KeyRecord keyRecord)
            throws EBaseException {

        byte[] decodedData = null;

        try {
            decodedData = mStorageUnit.decryptInternalPrivate(
                    keyRecord.getPrivateKeyData());

            if (decodedData == null) {
                throw new EKRAException(CMS.getUserMessage("CMS_KRA_RECOVERY_FAILED_1",
                        "security data unwrapping failure"));
            }

            return decodedData;
        } catch (Exception e) {

            throw new EKRAException(CMS.getUserMessage("CMS_KRA_RECOVERY_FAILED_1",
                    "recoverSecurityData() " + e.toString()));
        }
    }

    //ToDo: This might fit in JSS.
    private static EncryptedContentInfo
            createEncryptedContentInfoPBEOfKey(PBEAlgorithm keyGenAlg, Password password, byte[] salt,
                    int iterationCount,
                    KeyGenerator.CharToByteConverter charToByteConverter,
                    SymmetricKey symKey, PrivateKey privateKey, CryptoToken token)
                    throws CryptoManager.NotInitializedException, NoSuchAlgorithmException,
                    InvalidKeyException, InvalidAlgorithmParameterException, TokenException,
                    CharConversionException {

        if (keyGenAlg == null) {
            throw new NoSuchAlgorithmException("Key generation algorithm  is NULL");
        }
        PBEAlgorithm pbeAlg = keyGenAlg;

        KeyGenerator kg = token.getKeyGenerator(keyGenAlg);
        PBEKeyGenParams pbekgParams = new PBEKeyGenParams(
                password, salt, iterationCount);
        if (charToByteConverter != null) {
            kg.setCharToByteConverter(charToByteConverter);
        }
        kg.initialize(pbekgParams);
        SymmetricKey key = kg.generate();

        EncryptionAlgorithm encAlg = pbeAlg.getEncryptionAlg();
        AlgorithmParameterSpec params = null;
        if (encAlg.getParameterClass().equals(IVParameterSpec.class)) {
            params = new IVParameterSpec(kg.generatePBE_IV());
        } else if (encAlg.getParameterClass().equals(
                RC2ParameterSpec.class)) {
            params = new RC2ParameterSpec(key.getStrength(),
                                kg.generatePBE_IV());
        }

        KeyWrapper wrapper = token.getKeyWrapper(
                KeyWrapAlgorithm.DES3_CBC_PAD);
        wrapper.initWrap(key, params);
        byte[] encrypted = null;
        if (symKey != null) {
            encrypted = wrapper.wrap(symKey);
        } else if (privateKey != null) {
            encrypted = wrapper.wrap(privateKey);
        }
        if (encrypted == null) {
            //TODO - think about the exception to be thrown
        }
        PBEParameter pbeParam = new PBEParameter(salt, iterationCount);
        AlgorithmIdentifier encAlgID = new AlgorithmIdentifier(
                keyGenAlg.toOID(), pbeParam);

        EncryptedContentInfo encCI = new EncryptedContentInfo(
                ContentInfo.DATA,
                encAlgID,
                new OCTET_STRING(encrypted));

        return encCI;

    }

    private static String createEncryptedContentInfo(CryptoToken ct, SymmetricKey symKey, byte[] securityData, PrivateKey privateKey,
            Password password)
            throws EBaseException {

        EncryptedContentInfo cInfo = null;
        String retData = null;
        PBEAlgorithm keyGenAlg = PBEAlgorithm.PBE_SHA1_DES3_CBC;

        byte[] encoded = null;
        try {
            PasswordConverter passConverter = new
                    PasswordConverter();
            byte salt[] = { 0x01, 0x01, 0x01, 0x01 };
            if (symKey != null) {

                cInfo = createEncryptedContentInfoPBEOfKey(keyGenAlg, password, salt,
                        1,
                        passConverter,
                        symKey, null, ct);

            } else if (securityData != null) {

                cInfo = EncryptedContentInfo.createPBE(keyGenAlg, password, salt, 1, passConverter, securityData);
            } else if (privateKey != null) {
                cInfo = createEncryptedContentInfoPBEOfKey(keyGenAlg, password, salt,
                        1,
                        passConverter,
                        null, privateKey, ct);
            }

            if(cInfo == null) {
                throw new EBaseException("Can't create a PBE wrapped EncryptedContentInfo!");
            }

            ByteArrayOutputStream oStream = new ByteArrayOutputStream();
            cInfo.encode(oStream);
            encoded = oStream.toByteArray();
            retData = Utils.base64encode(encoded);

        } catch (Exception e) {
            throw new EBaseException("Can't create a PBE wrapped EncryptedContentInfo! " + e.toString());
        }

        return retData;
    }

    private void audit(String msg) {
        if (signedAuditLogger == null)
            return;

        signedAuditLogger.log(ILogger.EV_SIGNED_AUDIT,
                null,
                ILogger.S_SIGNED_AUDIT,
                ILogger.LL_SECURITY,
                msg);
    }

    private void auditRecoveryRequestProcessed(String subjectID, String status, RequestId requestID,
            String keyID, String reason) {
        String auditMessage = CMS.getLogMessage(
                LOGGING_SIGNED_AUDIT_SECURITY_DATA_RECOVERY_REQUEST_PROCESSED,
                subjectID,
                status,
                requestID.toString(),
                keyID,
                reason);
        audit(auditMessage);
    }

}
