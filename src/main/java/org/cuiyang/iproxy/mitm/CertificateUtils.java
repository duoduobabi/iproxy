package org.cuiyang.iproxy.mitm;

import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.bc.BcX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.regex.Pattern;

public class CertificateUtils {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private static final Pattern TAGS_PATTERN = Pattern.compile("[012345678]");
    private static final String PROVIDER_NAME = BouncyCastleProvider.PROVIDER_NAME;
    private static final String KEYGEN_ALGORITHM = "RSA";
    private static final String SECURE_RANDOM_ALGORITHM = "SHA1PRNG";
    private static final int ROOT_KEY_SIZE = 2048;
    private static final int FAKE_KEY_SIZE = 2048;
    private static final long ONE_DAY = 86400000L;
    private static final Date NOT_BEFORE = new Date(System.currentTimeMillis() - ONE_DAY * 365);
    private static final Date NOT_AFTER = new Date(System.currentTimeMillis() + ONE_DAY * 365 * 100);

    /**
     * The signature algorithm starting with the message digest to use when
     * signing certificates. On 64-bit systems this should be set to SHA512, on
     * 32-bit systems this is SHA256. On 64-bit systems, SHA512 generally
     * performs better than SHA256; see this question for details:
     * http://crypto.stackexchange.com/questions/26336/sha512-faster-than-sha256
     */
    private static final String SIGNATURE_ALGORITHM = (is32BitJvm() ? "SHA256" : "SHA512") + "WithRSAEncryption";

    public static KeyPair generateKeyPair(int keySize) throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(KEYGEN_ALGORITHM);
        SecureRandom secureRandom = SecureRandom.getInstance(SECURE_RANDOM_ALGORITHM);
        generator.initialize(keySize, secureRandom);
        return generator.generateKeyPair();
    }

    public static KeyStore createRootCertificate(Authority authority,
                                                 String keyStoreType) throws NoSuchAlgorithmException, IOException,
            OperatorCreationException, CertificateException, KeyStoreException {

        KeyPair keyPair = generateKeyPair(ROOT_KEY_SIZE);

        X500NameBuilder nameBuilder = new X500NameBuilder(BCStyle.INSTANCE);
        nameBuilder.addRDN(BCStyle.CN, authority.getCommonName());
        nameBuilder.addRDN(BCStyle.O, authority.getOrganization());
        nameBuilder.addRDN(BCStyle.OU, authority.getOrganizationalUnitName());

        X500Name issuer = nameBuilder.build();
        BigInteger serial = BigInteger.valueOf(initRandomSerial());
        PublicKey pubKey = keyPair.getPublic();

        X509v3CertificateBuilder generator = new JcaX509v3CertificateBuilder(
                issuer, serial, NOT_BEFORE, NOT_AFTER, issuer, pubKey);

        generator.addExtension(Extension.subjectKeyIdentifier, false, createSubjectKeyIdentifier(pubKey));
        generator.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));

        KeyUsage usage = new KeyUsage(KeyUsage.keyCertSign
                | KeyUsage.digitalSignature | KeyUsage.keyEncipherment
                | KeyUsage.dataEncipherment | KeyUsage.cRLSign);
        generator.addExtension(Extension.keyUsage, false, usage);

        ASN1EncodableVector purposes = new ASN1EncodableVector();
        purposes.add(KeyPurposeId.id_kp_serverAuth);
        purposes.add(KeyPurposeId.id_kp_clientAuth);
        purposes.add(KeyPurposeId.anyExtendedKeyUsage);
        generator.addExtension(Extension.extendedKeyUsage, false, new DERSequence(purposes));

        X509Certificate cert = signCertificate(generator, keyPair.getPrivate());

        KeyStore result = KeyStore.getInstance(keyStoreType);
        result.load(null, null);
        result.setKeyEntry(authority.getAlias(), keyPair.getPrivate(), authority.getPassword(), new Certificate[]{cert});
        return result;
    }

    public static KeyStore createServerCertificate(String commonName,
                                                   Collection<List<?>> subjectAlternativeNames,
                                                   Authority authority, Certificate caCert, PrivateKey caPrivateKey)
            throws NoSuchAlgorithmException, NoSuchProviderException,
            IOException, OperatorCreationException, CertificateException,
            InvalidKeyException, SignatureException, KeyStoreException {

        KeyPair keyPair = generateKeyPair(FAKE_KEY_SIZE);

        X500Name issuer = new X509CertificateHolder(caCert.getEncoded()).getSubject();
        BigInteger serial = BigInteger.valueOf(initRandomSerial());

        X500NameBuilder name = new X500NameBuilder(BCStyle.INSTANCE);
        name.addRDN(BCStyle.CN, commonName);
        name.addRDN(BCStyle.O, authority.getCertOrganization());
        name.addRDN(BCStyle.OU, authority.getCertOrganizationalUnitName());
        X500Name subject = name.build();

        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(issuer, serial, NOT_BEFORE,
                new Date(System.currentTimeMillis() + ONE_DAY), subject, keyPair.getPublic());

        builder.addExtension(Extension.subjectKeyIdentifier, false, createSubjectKeyIdentifier(keyPair.getPublic()));
        builder.addExtension(Extension.basicConstraints, false, new BasicConstraints(false));

        List<ASN1Encodable> sans = new ArrayList<>();
        if (subjectAlternativeNames != null) {
            for (List<?> each : subjectAlternativeNames) {
                if (isValidNameEntry(each)) {
                    int tag = Integer.parseInt(String.valueOf(each.get(0)));
                    sans.add(new GeneralName(tag, String.valueOf(each.get(1))));
                }
            }
        }
        if (!sans.isEmpty()) {
            builder.addExtension(Extension.subjectAlternativeName, false,
                    new DERSequence(sans.toArray(new ASN1Encodable[0])));
        }

        X509Certificate cert = signCertificate(builder, caPrivateKey);

        cert.checkValidity(new Date());
        cert.verify(caCert.getPublicKey());

        KeyStore result = KeyStore.getInstance(KeyStore.getDefaultType());
        result.load(null, null);
        Certificate[] chain = { cert, caCert };
        result.setKeyEntry(authority.getAlias(), keyPair.getPrivate(), authority.getPassword(), chain);
        return result;
    }

    private static SubjectKeyIdentifier createSubjectKeyIdentifier(Key key) throws IOException {
        try (ASN1InputStream in = new ASN1InputStream(new ByteArrayInputStream(key.getEncoded()))) {
            ASN1Sequence seq = (ASN1Sequence) in.readObject();
            SubjectPublicKeyInfo info = SubjectPublicKeyInfo.getInstance(seq);
            return new BcX509ExtensionUtils().createSubjectKeyIdentifier(info);
        }
    }

    private static X509Certificate signCertificate(X509v3CertificateBuilder certificateBuilder,
                                                   PrivateKey signedWithPrivateKey) throws OperatorCreationException,
            CertificateException {
        ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
                .setProvider(PROVIDER_NAME).build(signedWithPrivateKey);
        return new JcaX509CertificateConverter().setProvider(
                PROVIDER_NAME).getCertificate(certificateBuilder.build(signer));
    }

    private static boolean is32BitJvm() {
        Integer bits = Integer.getInteger("sun.arch.data.model");
        return bits != null && bits == 32;
    }

    private static boolean isValidNameEntry(List<?> nameEntry) {
        if (nameEntry == null || nameEntry.size() != 2) {
            return false;
        }
        String tag = String.valueOf(nameEntry.get(0));
        return TAGS_PATTERN.matcher(tag).matches();
    }

    private static long initRandomSerial() {
        Random rnd = new Random();
        long sl = ((long) rnd.nextInt()) << 32 | (rnd.nextInt() & 0xFFFFFFFFL);
        sl = sl & 0x0000FFFFFFFFFFFFL;
        return sl;
    }

}
