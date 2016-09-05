package mozilla.cd.proximityble;

import android.util.Base64;

import org.spongycastle.asn1.cms.KEKIdentifier;
import org.spongycastle.crypto.engines.AESFastEngine;
import org.spongycastle.crypto.modes.CBCBlockCipher;
import org.spongycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.spongycastle.crypto.params.KeyParameter;
import org.spongycastle.crypto.params.ParametersWithIV;
import org.spongycastle.jce.provider.BouncyCastleProvider;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.X509EncodedKeySpec;
import java.util.Random;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.SecretKeySpec;

/**
 * Created by jgomez on 29/08/16.
 * TODO
 * Verify Signature
 * AES-256
 */
public class CryptoHelper {

    private PublicKey mPeerPublicKey;
    byte [] mKeyValue = new byte[16]; // AES-256 bits
    //byte [] mKeyValue = { '0', '1', '0', '1', '0', '1', '0', '1', '0', '1', '0', '1', '0', '1', '0', '1' };
    private static char[] VALID_CHARACTERS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456879".toCharArray();

    CryptoHelper(String peerPublicKey) throws NoSuchAlgorithmException, GeneralSecurityException, UnsupportedEncodingException {
        Security.insertProviderAt(new BouncyCastleProvider(),1);
        mPeerPublicKey = loadPublicKey(peerPublicKey);
        //KeyGenerator kgen = KeyGenerator.getInstance("AES");
        SecureRandom srand = new SecureRandom();
        //kgen.init(256, random);
        Random rand = new Random();
        for (int i = 0; i < mKeyValue.length; ++i) {
            if ((i % 10) == 0) {
                rand.setSeed(srand.nextLong());
            }
            mKeyValue[i] = (byte)VALID_CHARACTERS[rand.nextInt(VALID_CHARACTERS.length)];
        }
    }

    private PublicKey loadPublicKey(String key64) throws GeneralSecurityException, UnsupportedEncodingException {

        String publicPem = key64.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "");
        byte [] publicPemDecoded = Base64.decode(publicPem, Base64.DEFAULT);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicPemDecoded);
        KeyFactory fact = KeyFactory.getInstance("RSA", "SC");
        return fact.generatePublic(keySpec);
    }

    public byte [] getEncryptedBase64AesSecret(){
        try {
            byte [] encryptedAesKey = encryptWithRsa(mKeyValue);
            return Base64.encode(encryptedAesKey, Base64.NO_WRAP);
        }catch (Exception ex){
            return null;
        }
    }

    private static String byteArrayToHexString( byte [] raw ) {
        final String HEXES = "0123456789ABCDEF";
        if ( raw == null ) {
            return null;
        }
        final StringBuilder hex = new StringBuilder( 2 * raw.length );
        for ( final byte b : raw ) {
            hex.append(HEXES.charAt((b & 0xF0) >> 4))
                    .append(HEXES.charAt((b & 0x0F)));
        }
        return hex.toString();
    }

    public byte[] encryptWithAes(byte[] data) throws
            NoSuchAlgorithmException,
            NoSuchPaddingException,
            InvalidKeyException,
            BadPaddingException,
            IllegalBlockSizeException,
            ShortBufferException {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] thedigest = md.digest(mKeyValue);
        SecretKeySpec skc = new SecretKeySpec(thedigest, "AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, skc);
        byte[] cipherText = new byte[cipher.getOutputSize(data.length)];
        int ctLength = cipher.update(data, 0, data.length, cipherText, 0);
        cipher.doFinal(cipherText, ctLength);
        return cipherText;
    }

    public byte[] encryptWithRsa(byte[] data) throws
            NoSuchAlgorithmException,
            NoSuchPaddingException,
            NoSuchProviderException,
            InvalidKeyException,
            IllegalBlockSizeException,
            BadPaddingException {
        Cipher cipher = Cipher.getInstance("RSA/None/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, mPeerPublicKey);
        return cipher.doFinal(data);
    }
}
