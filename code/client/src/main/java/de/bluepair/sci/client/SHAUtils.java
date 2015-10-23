/**
 * This file is part of client.
 *
 * client is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * client is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with client.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.bluepair.sci.client;

import de.bluepair.commons.file.FileAnalysis;
import org.apache.commons.codec.binary.Hex;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SHAUtils {

    private SHAUtils() {
    }

    public static MessageDigest getDigest() {
        try {
            return MessageDigest.getInstance("SHA-512");

        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(FileAnalysis.class.getName()).log(Level.SEVERE, null, ex);
            // try rescue with sha
            try {
                return MessageDigest.getInstance("SHA");
            } catch (NoSuchAlgorithmException ex1) {
                Logger.getLogger(FileAnalysis.class.getName()).log(Level.SEVERE, null, ex1);
                return null;
            }
        }
    }

    public static String sha512(String data) {
        return sha512(data == null || data.isEmpty() ? null : data.getBytes());
    }

    public static String sha512(byte[] data) {
        MessageDigest md = getDigest();
        if (md == null || data == null) {
            return "";
        }
        md.update(data);
        final byte[] sha1hash = md.digest();
        return Hex.encodeHexString(sha1hash);
    }


    public static <T> Map<String, String> sha512(FileAnalysis path, long blockSizePref, boolean forceBlockSize) {
        long lastMod = path.getLastModifiedTime();
        Predicate<Long> gard = (test) -> path.getLastModifiedTime() <= test;
        return sha512(path.getResolvedPath(), gard, lastMod, blockSizePref, forceBlockSize);
    }


    public static <T> Map<String, String> sha512(Path path, Predicate<T> gard, T testValue, long blockSizePref) {
        return sha512(path, gard, testValue, blockSizePref, false);
    }

    public static <T> Map<String, String> sha512(Path path, Predicate<T> gard, T testValue, long blockSizePref,
                                                 boolean forceBlockSize) {

        if (Files.notExists(path)) {
            return null;
        }
        MessageDigest md = getDigest();
        MessageDigest md1 = getDigest();

        if (!gard.test(testValue)) {
            return null;
        }
        long blockSize = blockSizePref;
        long size = -1;
        try {
            size = Files.size(path);
            if (!forceBlockSize) {// maximal 10 hashsummen
                // sonst hab ich zu viele in der datei
                // stehen!
                while (size / blockSize > 10) {
                    blockSize += blockSizePref;
                }
            }

        } catch (IOException e) {
            blockSize = blockSizePref;
            return null;
        }

        Map<String, String> map = new HashMap<>();

        long lastStart = 0;

        long stepDown = blockSize;

        try (final SeekableByteChannel fileChannel = Files.newByteChannel(path, StandardOpenOption.READ);) {

            final ByteBuffer buffer = ByteBuffer.allocateDirect(8192);
            int last;
            do {
                if (!gard.test(testValue) || Files.notExists(path)) {
                    return null;
                }
                buffer.clear();
                last = fileChannel.read(buffer);

                buffer.flip();
                md.update(buffer);

                // calc 2checksups
                buffer.flip();
                md1.update(buffer);

                if (last > 0) {
                    stepDown -= last;
                }

                // wenn ich ein 100mb netzwerk habe
                // ~ca. 5MB übertragung
                // also bei abbruch kann wiederaufgesetzt werden wenn die summen
                // bekannt sind.
                // ~ähnlich Blöcke berechen also
                // 0-5 c1
                // 0-10 c2
                // 5-10 c3 ...

                if (stepDown <= 0 || (last <= 0)) {
                    long len = (blockSize + Math.abs(stepDown));
                    if (stepDown > 0) {
                        // kottektur wenn last <0
                        len = blockSize - stepDown;
                    }
                    stepDown = blockSize;
                    map.put("sha512_" + lastStart + "_" + len, Hex.encodeHexString(md1.digest()));
                    lastStart += len;
                    md1.reset();
                }

            } while (last > 0);

        } catch (IOException ex) {
            Logger.getLogger(FileAnalysis.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }

        final byte[] sha1hash = md.digest();
        map.put("sha512", Hex.encodeHexString(sha1hash));
        return map;

    }

}
