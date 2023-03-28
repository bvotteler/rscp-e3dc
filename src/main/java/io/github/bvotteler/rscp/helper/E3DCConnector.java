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

import io.vavr.control.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.function.Function;

import static io.vavr.control.Either.left;
import static io.vavr.control.Either.right;

public class E3DCConnector {
    private static final int maxRetries = 3;
    private static final long sleepMillisBeforeRetry = 5000;
    private static final Logger logger = LoggerFactory.getLogger(E3DCConnector.class.getSimpleName());

    private static boolean isNotConnected(Socket socket) {
        return socket == null || socket.isClosed();
    }

    public static void silentlyCloseConnection(Socket socket) {
        try {
            socket.close();
        } catch (Exception e) {
            // ignore
        }
    }

    public static Socket openConnection(String ipAddress, int port, int maxRetries, long sleepMillisBeforeRetry) throws UnknownHostException {
        Socket socket = null;
        int retries = 0;
        while (isNotConnected(socket) && retries++ < maxRetries) {
            try {
                logger.debug("Connection attempt #" + retries + " ...");
                socket = new Socket(ipAddress, port);
                socket.setTcpNoDelay(true);
                socket.setSoTimeout(10000);
                logger.debug("Connected successfully.");
            } catch (UnknownHostException e) {
                logger.error("Failed to connect to host: Unknown host.", e);
                silentlyCloseConnection(socket);
                throw e;
            } catch (IOException e) {
                logger.error("Failed to connect to host: IOException occurred.", e);
                silentlyCloseConnection(socket);
                if (retries < maxRetries) {
                    logger.debug("Retrying in " + E3DCConnector.sleepMillisBeforeRetry + " seconds.");
                    try {
                        Thread.sleep(sleepMillisBeforeRetry);
                    } catch (Exception ex) {
                        // ignore
                    }
                }
            } catch (Exception e) {
                logger.error("Exception while trying to establish connection to server.", e);
                throw e;
            }
        }

        if (socket == null) {
            // retries exhausted, still no connection
            throw new RuntimeException("Failed to establish connection to server.");
        } else {
            return socket;
        }
    }

    public static Socket openConnection(String ipAddress, int port) throws UnknownHostException {
        return openConnection(ipAddress, port, maxRetries, sleepMillisBeforeRetry);
    }

    /**
     * Send a encrypt and send a byte array through a provided socket.
     *
     * @param socket      The socket to write to.
     * @param encryptFunc A function to encrypt the provided frame.
     * @param frame       The unencrypted frame as byte array.
     * @return Either an exception or the number of bytes sent.
     */
    public static Either<Exception, Integer> sendFrameToServer(Socket socket, Function<byte[], byte[]> encryptFunc, byte[] frame) {
        if (isNotConnected(socket)) {
            return left(new IllegalStateException("Not connected to server. Must connect to server first before sending."));
        }

        try {
            byte[] encryptedFrame = encryptFunc.apply(frame);
            DataOutputStream dOut = new DataOutputStream(socket.getOutputStream());
            dOut.write(encryptedFrame);
            dOut.flush();
            return right(encryptedFrame.length);
        } catch (Exception e) {
            logger.error("Error while encrypting and sending frame.", e);
            return left(e);
        }
    }

    /**
     * Receive a frame from a socket and decrypted it.
     *
     * @param socket      A socket to read from.
     * @param decryptFunc A function to decrypt the received byte array.
     * @return Either an exception or the decrypted response as byte array.
     */
    public static Either<Exception, byte[]> receiveFrameFromServer(Socket socket, Function<byte[], byte[]> decryptFunc) {
        if (isNotConnected(socket)) {
            return left(new IllegalStateException("Not connected to server. Must connect to server first before sending."));
        }

        try {
            int totalBytesRead = 0;
            DataInputStream dIn = new DataInputStream(socket.getInputStream());
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[4096];
            do {
                int bytesRead = dIn.read(data, 0 , data.length);
                logger.debug("Received " + bytesRead + " bytes, append to buffer... ");
                if (bytesRead == -1) {
                    logger.warn("Socket closed unexpectedly by server.");
                    break;
                }
                buffer.write(data, 0, bytesRead);
                totalBytesRead += bytesRead;
            } while (dIn.available() > 0);

            logger.debug("Finished reading " + totalBytesRead + " bytes.");
            buffer.flush();

            byte[] decryptedData = decryptFunc.apply(buffer.toByteArray());
            logger.debug("Decrypted frame data.");

            return right(decryptedData);
        } catch (Exception e) {
            logger.error("Error while receiving and decrypting frame.", e);
            return left(e);
        }
    }
}
