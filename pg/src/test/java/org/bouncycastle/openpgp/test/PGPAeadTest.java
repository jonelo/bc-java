package org.bouncycastle.openpgp.test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.Security;

import org.bouncycastle.bcpg.AEADAlgorithmTags;
import org.bouncycastle.bcpg.ArmoredInputStream;
import org.bouncycastle.bcpg.BCPGOutputStream;
import org.bouncycastle.bcpg.PaddingPacket;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPEncryptedData;
import org.bouncycastle.openpgp.PGPEncryptedDataList;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPPBEEncryptedData;
import org.bouncycastle.openpgp.PGPPadding;
import org.bouncycastle.openpgp.bc.BcPGPObjectFactory;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;
import org.bouncycastle.openpgp.operator.PBEDataDecryptorFactory;
import org.bouncycastle.openpgp.operator.PGPDigestCalculatorProvider;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.bc.BcPBEDataDecryptorFactory;
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePBEDataDecryptorFactoryBuilder;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.encoders.Hex;
import org.bouncycastle.util.io.Streams;
import org.bouncycastle.util.test.SimpleTest;

/**
 * Test OpenPGP v6 style AEAD decryption of password-protected test vectors from the cryptographic refresh of rfc4880.
 *
 * @see <a href="https://gitlab.com/openpgp-wg/rfc4880bis/-/tree/main/test-vectors">Sample test vectors</a>
 */
public class PGPAeadTest
    extends SimpleTest
{

    private static final byte[] PLAINTEXT = "Hello, world!".getBytes(StandardCharsets.UTF_8);
    private static final char[] PASSWORD = "password".toCharArray();

    // Official Test Vectors
    private static final String V6_EAX_PACKET_SEQUENCE = "-----BEGIN PGP MESSAGE-----\n" +
        "\n" +
        "w0AGHgcBCwMIpa5XnR/F2Cv/aSJPkZmTs1Bvo7WaanPP+MXvxfQcV/tU4cImgV14\n" +
        "KPX5LEVOtl6+AKtZhsaObnxV0mkCBwEGn/kOOzIZZPOkKRPI3MZhkyUBUifvt+rq\n" +
        "pJ8EwuZ0F11KPSJu1q/LnKmsEiwUcOEcY9TAqyQcapOK1Iv5mlqZuQu6gyXeYQR1\n" +
        "QCWKt5Wala0FHdqW6xVDHf719eIlXKeCYVRuM5o=\n" +
        "-----END PGP MESSAGE-----\n";
    private static final String V6_OCB_PACKET_SEQUENCE = "-----BEGIN PGP MESSAGE-----\n" +
        "\n" +
        "wz8GHQcCCwMIVqKY0vXjZFP/z8xcEWZO2520JZDX3EawckG2EsOBLP/76gDyNHsl\n" +
        "ZBEj+IeuYNT9YU4IN9gZ02zSaQIHAgYgpmH3MfyaMDK1YjMmAn46XY21dI6+/wsM\n" +
        "WRDQns3WQf+f04VidYA1vEl1TOG/P/+n2tCjuBBPUTPPQqQQCoPu9MobSAGohGv0\n" +
        "K82nyM6dZeIS8wHLzZj9yt5pSod61CRzI/boVw==\n" +
        "-----END PGP MESSAGE-----\n";
    private static final String V6_GCM_PACKET_SEQUENCE = "-----BEGIN PGP MESSAGE-----\n" +
        "\n" +
        "wzwGGgcDCwMI6dOXhbIHAAj/tC58SD70iERXyzcmubPbn/d25fTZpAlS4kRymIUa\n" +
        "v/91Jt8t1VRBdXmneZ/SaQIHAwb8uUSQvLmLvcnRBsYJAmaUD3LontwhtVlrFXax\n" +
        "Ae0Pn/xvxtZbv9JNzQeQlm5tHoWjAFN4TLHYtqBpnvEhVaeyrWJYUxtXZR/Xd3kS\n" +
        "+pXjXZtAIW9ppMJI2yj/QzHxYykHOZ5v+Q==\n" +
        "-----END PGP MESSAGE-----\n";
    // Homemade test vectors from BC 1.72 to ensure backwards compat with pre-patch BC
    private static final String V5_EAX_PACKET_SEQUENCE = "-----BEGIN PGP MESSAGE-----\n" +
        "Comment: Generated using BC 1.72\n" +
        "\n" +
        "jB4EBwMIoM1rJmpJ+PVglCacbCVQOmFcJcAt84821mfUSQEHAQDGp5sLS9Ttznvd\n" +
        "gWNKeAVoVfZWtk/HxGkAgKnJBViUbWBhYW0mXA8Mwf29Maz34nnaixFTc5PC/O56\n" +
        "6fW7DLzJ9J09nkE=\n" +
        "=vApM\n" +
        "-----END PGP MESSAGE-----";
    private static final String V5_OCB_PACKET_SEQUENCE = "-----BEGIN PGP MESSAGE-----\n" +
        "Comment: Generated using BC 1.72\n" +
        "\n" +
        "jB4EBwMIZRVl03fl3ABgzCip0L+8pQMZ2AjYcmuAHbnUSAEHAgDCFxZXdafRqogj\n" +
        "PoUaOyuF3JsQs62nBTeEMaFY0TP8yLLMtina9q7V1OcFgvLvtSugd5PSY2ipAgwT\n" +
        "NCh2fmu6AA9Q6A==\n" +
        "=iJEV\n" +
        "-----END PGP MESSAGE-----";
    private static final String V5_GCM_PACKET_SEQUENCE = "-----BEGIN PGP MESSAGE-----\n" +
        "Comment: Generated using BC 1.72\n" +
        "\n" +
        "jB4EBwMIoEl85k52SodgLetOxLRv2QjBLfVzPa9zhErURQEHAwA/UJOYkH5rHr1Z\n" +
        "BWN8oNhV/mcw45J+1+IfabaDFVlUVjVnBIIKCUYY+BEprJ8r/rtYCiVgw9+QJVfe\n" +
        "eI3EuSfMqQ==\n" +
        "=bgWx\n" +
        "-----END PGP MESSAGE-----";

    @Override
    public String getName()
    {
        return getClass().getSimpleName();
    }

    @Override
    public void performTest()
        throws Exception
    {
        knownV6TestVectorDecryptionTests();
        knownV5TestVectorDecryptionTests();

        roundTripEncryptionDecryptionTests();

        paddingPacketTests();
    }

    private void roundTripEncryptionDecryptionTests()
        throws PGPException, IOException
    {
        int[] aeadAlgs = new int[]{
            AEADAlgorithmTags.EAX,
            AEADAlgorithmTags.OCB,
            AEADAlgorithmTags.GCM
        };
        int[] symAlgs = new int[]{
            SymmetricKeyAlgorithmTags.AES_128,
            SymmetricKeyAlgorithmTags.AES_192,
            SymmetricKeyAlgorithmTags.AES_256
        };
//        PGPAEADFlavour[] flavours = new PGPAEADFlavour[] {
//                PGPAEADFlavour.OPENPGP_V6,
//                PGPAEADFlavour.OPENPGP_V5,
//        };

        // Test round-trip encryption
//        for (PGPAEADFlavour flavour : flavours) {
//            for (int aeadAlg : aeadAlgs) {
//                for (int symAlg : symAlgs) {
//                    testBcRoundTrip(flavour, aeadAlg, symAlg, PLAINTEXT, PASSWORD);
//                    testJceRoundTrip(flavour, aeadAlg, symAlg, PLAINTEXT, PASSWORD);
//                    testBcJceRoundTrip(flavour, aeadAlg, symAlg, PLAINTEXT, PASSWORD);
//                    testJceBcRoundTrip(flavour, aeadAlg, symAlg, PLAINTEXT, PASSWORD);
//                }
//            }
//        }
    }

    private void knownV5TestVectorDecryptionTests()
        throws IOException, PGPException
    {
        // test known-good V5 test vectors
        System.out.println("Test V5 BC Decryption");
        testBcDecryption(V5_EAX_PACKET_SEQUENCE, PASSWORD, PLAINTEXT);
        testBcDecryption(V5_OCB_PACKET_SEQUENCE, PASSWORD, PLAINTEXT);
        testBcDecryption(V5_GCM_PACKET_SEQUENCE, PASSWORD, PLAINTEXT);
        System.out.println("Test V5 JCA Decryption");
        testJceDecryption(V5_EAX_PACKET_SEQUENCE, PASSWORD, PLAINTEXT);
        testJceDecryption(V5_OCB_PACKET_SEQUENCE, PASSWORD, PLAINTEXT);
        testJceDecryption(V5_GCM_PACKET_SEQUENCE, PASSWORD, PLAINTEXT);
    }

    private void knownV6TestVectorDecryptionTests()
        throws IOException, PGPException
    {
        // Test known-good V6 test vectors
        System.out.println("Test V6 BC Decryption");
        testBcDecryption(V6_EAX_PACKET_SEQUENCE, PASSWORD, PLAINTEXT);
        testBcDecryption(V6_OCB_PACKET_SEQUENCE, PASSWORD, PLAINTEXT);
        testBcDecryption(V6_GCM_PACKET_SEQUENCE, PASSWORD, PLAINTEXT);
        System.out.println("Test V6 JCA Decryption");
        testJceDecryption(V6_EAX_PACKET_SEQUENCE, PASSWORD, PLAINTEXT);
        testJceDecryption(V6_OCB_PACKET_SEQUENCE, PASSWORD, PLAINTEXT);
        testJceDecryption(V6_GCM_PACKET_SEQUENCE, PASSWORD, PLAINTEXT);
    }

//    private void testBcRoundTrip(PGPAEADFlavour flavour, int aeadAlg, int symAlg, byte[] plaintext, char[] password) throws PGPException, IOException {
//        System.out.println("Test BC RoundTrip " + flavour + " " + algNames(aeadAlg, symAlg));
//        String armored = testBcEncryption(flavour, aeadAlg, symAlg, plaintext, password);
//        System.out.println(armored);
//        testBcDecryption(armored, password, plaintext);
//    }

//    private void testJceRoundTrip(PGPAEADFlavour flavour, int aeadAlg, int symAlg, byte[] plaintext, char[] password) throws PGPException, IOException {
//        System.out.println("Test JCE RoundTrip " + flavour + " " + algNames(aeadAlg, symAlg));
//        String armored = testJceEncryption(flavour, aeadAlg, symAlg, plaintext, password);
//        System.out.println(armored);
//        testJceDecryption(armored, password, plaintext);
//    }

//    private void testBcJceRoundTrip(PGPAEADFlavour flavour, int aeadAlg, int symAlg, byte[] plaintext, char[] password)
//            throws PGPException, IOException {
//        System.out.println("Test BC encrypt, JCE decrypt " + flavour + " " + algNames(aeadAlg, symAlg));
//        String armored = testBcEncryption(flavour, aeadAlg, symAlg, plaintext, password);
//        System.out.println(armored);
//        testJceDecryption(armored, password, plaintext);
//    }
//
//    private void testJceBcRoundTrip(PGPAEADFlavour flavour, int aeadAlg, int symAlg, byte[] plaintext, char[] password) throws PGPException, IOException {
//        System.out.println("Test JCE encrypt, BC decrypt " + flavour + " " + algNames(aeadAlg, symAlg));
//        String armored = testJceEncryption(flavour, aeadAlg, symAlg, plaintext, password);
//        System.out.println(armored);
//        testBcDecryption(armored, password, plaintext);
//    }
//
//    private String testBcEncryption(PGPAEADFlavour flavour, int aeadAlg, int symAlg, byte[] plaintext, char[] password) throws PGPException, IOException {
//        ByteArrayOutputStream ciphertextOut = new ByteArrayOutputStream();
//        PGPDigestCalculatorProvider digestCalculatorProvider = new BcPGPDigestCalculatorProvider();
//        PGPDataEncryptorBuilder encBuilder = new BcPGPDataEncryptorBuilder(symAlg);
//        encBuilder.setWithAEAD(flavour, aeadAlg, 6);
//        PGPEncryptedDataGenerator encGen = new PGPEncryptedDataGenerator(encBuilder, false);
//        encGen.setForceSessionKey(true);
//        PBEKeyEncryptionMethodGenerator encMethodGen = new BcPBEKeyEncryptionMethodGenerator(password,
//                digestCalculatorProvider.get(HashAlgorithmTags.SHA256));
//        encGen.addMethod(encMethodGen);
//        OutputStream encOut = encGen.open(ciphertextOut, new byte[1 << 9]);
//        encOut.flush();
//        PGPLiteralDataGenerator litGen = new PGPLiteralDataGenerator();
//        OutputStream litOut = litGen.open(encOut, PGPLiteralData.UTF8, "", new Date(), new byte[1 << 9]);
//
//        litOut.write(plaintext);
//        litOut.flush();
//        litOut.close();
//
//        encOut.flush();
//        encOut.close();
//
//        ByteArrayOutputStream armoredMsg = new ByteArrayOutputStream();
//        ArmoredOutputStream armorOut = new ArmoredOutputStream(armoredMsg);
//        armorOut.write(ciphertextOut.toByteArray());
//        armorOut.flush();
//        armorOut.close();
//
//        printHex(ciphertextOut.toByteArray());
//
//        String armored = armoredMsg.toString();
//        return armored;
//    }
//
//    private String testJceEncryption(PGPAEADFlavour flavour, int aeadAlg, int symAlg, byte[] plaintext, char[] password) throws PGPException, IOException {
//        BouncyCastleProvider provider = new BouncyCastleProvider();
//        Security.addProvider(provider);
//        ByteArrayOutputStream ciphertextOut = new ByteArrayOutputStream();
//        PGPDigestCalculatorProvider digestCalculatorProvider = new JcaPGPDigestCalculatorProviderBuilder()
//                .setProvider(provider).build();
//        PGPDataEncryptorBuilder encBuilder = new JcePGPDataEncryptorBuilder(symAlg);
//        encBuilder.setWithAEAD(flavour, aeadAlg, 6);
//        PGPEncryptedDataGenerator encGen = new PGPEncryptedDataGenerator(encBuilder, false);
//        encGen.setForceSessionKey(true);
//        PBEKeyEncryptionMethodGenerator encMethodGen = new JcePBEKeyEncryptionMethodGenerator(password,
//                digestCalculatorProvider.get(HashAlgorithmTags.SHA256));
//        encGen.addMethod(encMethodGen);
//        OutputStream encOut = encGen.open(ciphertextOut, new byte[1 << 9]);
//        encOut.flush();
//        PGPLiteralDataGenerator litGen = new PGPLiteralDataGenerator();
//        OutputStream litOut = litGen.open(encOut, PGPLiteralData.UTF8, "", new Date(), new byte[1 << 9]);
//
//        litOut.write(plaintext);
//        litOut.flush();
//        litOut.close();
//
//        encOut.flush();
//        encOut.close();
//
//        ByteArrayOutputStream armoredMsg = new ByteArrayOutputStream();
//        ArmoredOutputStream armorOut = new ArmoredOutputStream(armoredMsg);
//        armorOut.write(ciphertextOut.toByteArray());
//        armorOut.flush();
//        armorOut.close();
//
//        printHex(ciphertextOut.toByteArray());
//
//        String armored = armoredMsg.toString();
//        return armored;
//    }

    private void testBcDecryption(String armoredMessage, char[] password, byte[] expectedPlaintext)
        throws IOException
    {
        ByteArrayInputStream messageIn = new ByteArrayInputStream(armoredMessage.getBytes(StandardCharsets.UTF_8));
        ArmoredInputStream armorIn = new ArmoredInputStream(messageIn);
        PGPObjectFactory objectFactory = new PGPObjectFactory(armorIn, new BcKeyFingerprintCalculator());

        Object o = objectFactory.nextObject();
        PGPEncryptedDataList encryptedDataList = (PGPEncryptedDataList)o;
        for (int i = 0; i < encryptedDataList.size(); i++)
        {
            PGPEncryptedData encryptedData = encryptedDataList.get(i);

            if (encryptedData instanceof PGPPBEEncryptedData)
            {
                PGPPBEEncryptedData symEncData = (PGPPBEEncryptedData)encryptedData;

                BcPGPDigestCalculatorProvider digestCalculatorProvider = new BcPGPDigestCalculatorProvider();
                PBEDataDecryptorFactory decryptorFactory = new BcPBEDataDecryptorFactory(password, digestCalculatorProvider);
                try
                {
                    InputStream decryptedIn = symEncData.getDataStream(decryptorFactory);
                    objectFactory = new PGPObjectFactory(decryptedIn, new BcKeyFingerprintCalculator());

                    o = objectFactory.nextObject();
                    PGPLiteralData literalData = (PGPLiteralData)o;

                    ByteArrayOutputStream plaintextOut = new ByteArrayOutputStream();
                    Streams.pipeAll(literalData.getDataStream(), plaintextOut);
                    isTrue(Arrays.areEqual(expectedPlaintext, plaintextOut.toByteArray()));

                    o = objectFactory.nextObject();
                    if (o instanceof PGPPadding)
                    {
                        System.err.println("hrer");
                        o = objectFactory.nextObject();
                    }

                    if (o != null)
                    {
                        System.out.println("Unexpected trailing packet.");
                        System.out.println(o);
                    }
                }
                catch (PGPException e)
                {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void testJceDecryption(String armoredMessage, char[] password, byte[] expectedPlaintext)
        throws IOException, PGPException
    {
        BouncyCastleProvider provider = new BouncyCastleProvider();
        Security.addProvider(provider);
        ByteArrayInputStream messageIn = new ByteArrayInputStream(armoredMessage.getBytes(StandardCharsets.UTF_8));
        ArmoredInputStream armorIn = new ArmoredInputStream(messageIn);
        PGPObjectFactory objectFactory = new JcaPGPObjectFactory(armorIn);

        Object o = objectFactory.nextObject();
        PGPEncryptedDataList encryptedDataList = (PGPEncryptedDataList)o;
        for (int i = 0; i < encryptedDataList.size(); i++)
        {
            PGPEncryptedData encryptedData = encryptedDataList.get(i);

            if (encryptedData instanceof PGPPBEEncryptedData)
            {
                PGPPBEEncryptedData symEncData = (PGPPBEEncryptedData)encryptedData;

                JcaPGPDigestCalculatorProviderBuilder digestCalculatorProviderBuilder = new JcaPGPDigestCalculatorProviderBuilder()
                    .setProvider(provider);
                PGPDigestCalculatorProvider digestCalculatorProvider = digestCalculatorProviderBuilder.build();

                PBEDataDecryptorFactory decryptorFactory = new JcePBEDataDecryptorFactoryBuilder(digestCalculatorProvider).build(password);
                try
                {
                    InputStream decryptedIn = symEncData.getDataStream(decryptorFactory);
                    objectFactory = new JcaPGPObjectFactory(decryptedIn);

                    o = objectFactory.nextObject();
                    PGPLiteralData literalData = (PGPLiteralData)o;

                    ByteArrayOutputStream plaintextOut = new ByteArrayOutputStream();
                    Streams.pipeAll(literalData.getDataStream(), plaintextOut);
                    isTrue(Arrays.areEqual(expectedPlaintext, plaintextOut.toByteArray()));

                    o = objectFactory.nextObject();
                    if (o instanceof PGPPadding)
                    {
                        o = objectFactory.nextObject();
                    }

                    if (o != null)
                    {
                        System.out.println("Unexpected trailing packet.");
                        System.out.println(o);
                    }
                }
                catch (PGPException e)
                {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public static void printHex(byte[] bytes)
    {
        boolean separate = true;
        boolean prefix = true;
        String hex = Hex.toHexString(bytes);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hex.length() / 2; i++)
        {
            if (prefix && i % 8 == 0)
            {
                sb.append("0x").append(String.format("%04X", i & 0xFFFFF)).append("   ");
            }
            sb.append(hex.substring(i * 2, i * 2 + 2));
            if (separate)
            {
                if ((i + 1) % 8 == 0)
                {
                    sb.append('\n');
                }
                else
                {
                    sb.append(' ');
                }
            }
        }
        System.out.println(sb);
    }

    private static String algNames(int aeadAlg, int symAlg)
    {
        String out = "";
        switch (aeadAlg)
        {
        case AEADAlgorithmTags.EAX:
            out = "EAX";
            break;
        case AEADAlgorithmTags.OCB:
            out = "OCB";
            break;
        case AEADAlgorithmTags.GCM:
            out = "GCM";
            break;
        default:
            out = "UNKNOWN(" + aeadAlg + ")";
        }

        switch (symAlg)
        {
        case SymmetricKeyAlgorithmTags.AES_128:
            out += " AES-128";
            break;
        case SymmetricKeyAlgorithmTags.AES_192:
            out += " AES-192";
            break;
        case SymmetricKeyAlgorithmTags.AES_256:
            out += " AES-256";
            break;
        default:
            out += " UNKNOWN(" + symAlg + ")";
        }
        return out;
    }

    private void paddingPacketTests()
        throws IOException
    {
        testRandom50Bytes();
        testKnownPaddingBytes();
        testPaddingPacketEncoding();
    }

    private void testKnownPaddingBytes()
    {
        byte[] known = "thisIsKnownPadding".getBytes(Charset.forName("UTF8"));
        PaddingPacket packet = new PaddingPacket(known);
        isTrue(Arrays.areEqual(known, packet.getPadding()));
    }

    private void testRandom50Bytes()
    {
        SecureRandom random = new SecureRandom();
        PaddingPacket packet = new PaddingPacket(50, random);
        isEquals(50, packet.getPadding().length);
    }

    private void testPaddingPacketEncoding()
        throws IOException
    {
        SecureRandom random = new SecureRandom();
        PaddingPacket packet = new PaddingPacket(32, random);

        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        BCPGOutputStream bcOut = new BCPGOutputStream(bOut);

        packet.encode(bcOut);
        bcOut.close();

        ByteArrayInputStream bIn = new ByteArrayInputStream(bOut.toByteArray());
        PGPObjectFactory factory = new BcPGPObjectFactory(bIn);

        PGPPadding padding = (PGPPadding)factory.nextObject();
        isTrue(Arrays.areEqual(packet.getPadding(), padding.getPadding()));
    }

    public static void main(String[] args)
    {
        runTest(new PGPAeadTest());
    }
}
