/*
 *  MIT License
 *
 *  Copyright (c) 2023. Georg Beier
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package io.github.bvotteler.rscp.helper;

import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.RijndaelEngine;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.paddings.ZeroBytePadding;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class BouncyAES256Helper implements AES256Helper {
    private static final Logger logger = LoggerFactory.getLogger(BouncyAES256Helper.class.getSimpleName());
    private final int messageBlockSize = 256;
    private byte[] key;
    private byte[] ivEnc;
    private byte[] ivDec;

//    public static BouncyAES256Helper createBouncyAES256Helper(String key) {
//        BouncyAES256Helper bouncyAES256Helper = new BouncyAES256Helper();
//        bouncyAES256Helper.initializeFromKey(key);
//        return bouncyAES256Helper;
//    }

    public BouncyAES256Helper(String key) {
        initializeFromKey(key);
    }

    private  BouncyAES256Helper() {}

    private void initializeFromKey(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Key must not be null");
        }
        byte[] aesKey = new byte[32];
        byte[] tmp = key.getBytes();
        // copy password into key
        logger.debug("Setting up encryption password...");
        for (int i = 0; i < aesKey.length; i++) {
            if (i < tmp.length) { // got a byte from password, copy it
                aesKey[i] = tmp[i];
            } else { // password bytes used up, fill with 0xFF
                aesKey[i] = (byte) 0xFF;
            }
        }

        logger.debug("Setting up initialization vectors... ");
        // initialize IV with 0xFF for first contact
        byte[] initializationVectorEncrypt = new byte[32];
        byte[] initializationVectorDecrypt = new byte[32];
        Arrays.fill(initializationVectorEncrypt, (byte) 0xFF);
        Arrays.fill(initializationVectorDecrypt, (byte) 0xFF);

        this.init(aesKey, initializationVectorEncrypt, initializationVectorDecrypt);
    }

    public void init(byte[] key, byte[] ivEnc, byte[] ivDec) {
        this.key = null;
        this.ivEnc = null;
        this.ivDec = null;
        if (key.length != 32) {
            throw new IllegalArgumentException("Key has to be 32 bytes long.");
        }

        if (ivEnc.length != 32)
            throw new IllegalArgumentException("IV has to be 32 bytes long.");

        if (ivDec.length != 32)
            throw new IllegalArgumentException("IV has to be 32 bytes long.");

        this.key = new byte[32];
        System.arraycopy(key, 0, this.key, 0, key.length);

        this.ivEnc = new byte[32];
        System.arraycopy(ivEnc, 0, this.ivEnc, 0, ivEnc.length);

        this.ivDec = new byte[32];
        System.arraycopy(ivEnc, 0, this.ivDec, 0, ivDec.length);
    }

    public byte[] encrypt(byte[] message) {
        if (this.key == null || this.ivEnc == null) {
            throw new IllegalStateException("Both key and IV have to be defined prior to encryption.");
        }

        try {
            byte[] sessionKey = this.key;
            byte[] iv = this.ivEnc;

            PaddedBufferedBlockCipher cipher = new PaddedBufferedBlockCipher(
                    new CBCBlockCipher(new RijndaelEngine(messageBlockSize)), new ZeroBytePadding());

            int keySize = messageBlockSize / 8;

            CipherParameters ivAndKey = new ParametersWithIV(new KeyParameter(sessionKey, 0, keySize), iv, 0, keySize);

            cipher.init(true, ivAndKey);
            byte[] encrypted = new byte[cipher.getOutputSize(message.length)];
            int oLen = cipher.processBytes(message, 0, message.length, encrypted, 0);

            cipher.doFinal(encrypted, oLen);

            // update IV
            System.arraycopy(encrypted, encrypted.length - this.ivEnc.length, this.ivEnc, 0, this.ivEnc.length);

            return encrypted;
        } catch (InvalidCipherTextException e) {
            logger.error("Exception encountered during encryption.", e);
            throw new RuntimeException(e);
        }
    }

    public byte[] decrypt(byte[] encryptedMessage) {
        if (this.key == null || this.ivDec == null) {
            throw new IllegalStateException("Both key and IV have to be defined prior to decryption.");
        }

        if (encryptedMessage == null)
            return null;

        try {
            byte[] sessionKey = this.key;
            byte[] iv = this.ivDec;

            PaddedBufferedBlockCipher cipher = new PaddedBufferedBlockCipher(
                    new CBCBlockCipher(new RijndaelEngine(messageBlockSize)), new ZeroBytePadding());

            int keySize = messageBlockSize / 8;

            CipherParameters ivAndKey = new ParametersWithIV(new KeyParameter(sessionKey, 0, keySize), iv, 0, keySize);

            cipher.init(false, ivAndKey);
            byte[] decrypted = new byte[cipher.getOutputSize(encryptedMessage.length)];
            int oLen = cipher.processBytes(encryptedMessage, 0, encryptedMessage.length, decrypted, 0);
            cipher.doFinal(decrypted, oLen);

            // Strip zeroes from decrypted message
            int lastZeroIdx = decrypted.length - 1;
            while (lastZeroIdx >= 0 && decrypted[lastZeroIdx] == 0)
            {
                --lastZeroIdx;
            }
            decrypted = Arrays.copyOf(decrypted, lastZeroIdx + 1);

            // update IV with the last bytes from the encrypted message
            System.arraycopy(encryptedMessage, encryptedMessage.length - this.ivDec.length, this.ivDec, 0, this.ivDec.length);

            return decrypted;
        } catch (InvalidCipherTextException e) {
            logger.error("Exception encountered during decryption.", e);
            throw new RuntimeException(e);
        }
    }
}