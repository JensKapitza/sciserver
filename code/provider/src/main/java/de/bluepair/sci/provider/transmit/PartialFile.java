/**
 * This file is part of provider.
 *
 * provider is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * provider is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with provider.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.bluepair.sci.provider.transmit;

import de.bluepair.commons.file.FileAnalysis;
import de.bluepair.sci.provider.FileResvBootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PartialFile {


    private FileAnalysis path;
    private long size;

    public PartialFile(FileAnalysis path, long size) {
        this.path = path;
        this.size = size;
    }

    public static boolean fill(Path path, long start, InputStream in, String shaSum, long len) {
        Objects.requireNonNull(path, "path need not to be null");
        Objects.requireNonNull(in, "no input stream detected");
        long cSize = 0;
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "rw")) {
            raf.seek(start);
            byte[] data = new byte[8192];
            int r = -1;
            do {
                r = in.read(data);
                if (r > 0) {
                    cSize += r;
                    raf.write(data, 0, r);
                }
            } while (r > 0);

        } catch (IOException ex) {
            Logger.getLogger(FileResvBootstrap.class.getName()).log(Level.SEVERE, null, ex);
        }
        if (len != cSize) {
            return false;
        }

        // alles ok also direkt checkSchreiben

        FileAnalysis a = new FileAnalysis(path, true);
        try {
            a.writeAttribute("check.sha512_" + start + "_" + len, shaSum);
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    public static void create(Path path, long size) {
        Objects.requireNonNull(path, "path need not to be null");

        if (size > 0 && Files.notExists(path)) {
            // try to create sparse file
            try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "rws")) {
                raf.setLength(size);
            } catch (IOException ex) {
                Logger.getLogger(FileResvBootstrap.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public void write(long start, InputStream in, String shaSum, long len) {
        if (!path.exists()) {
            create(path.getResolvedPath(), size);
        }
        fill(path.getResolvedPath(), start, in, shaSum, len);
    }

}
