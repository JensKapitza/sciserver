/**
 * This file is part of client.
 * <p>
 * client is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * client is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * <p>
 * You should have received a copy of the GNU Affero General Public License
 * along with client.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.bluepair.commons.file;

import de.bluepair.commons.watchservice.ChecksumCalculation;
import de.bluepair.commons.watchservice.FileWatchEvent;
import de.bluepair.sci.client.SHAUtils;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class FileAnalysis {

    private Path path;
    private boolean resolveLink;

    private Charset charset = Charset.forName("utf8");
    private Path cacheResolved;

    public FileAnalysis(String path, boolean resolveLinks) {
        this(path == null ? null : Paths.get(path), resolveLinks);
    }

    public FileAnalysis(Path path, boolean resolveLinks) {
        super();
        Objects.requireNonNull(path, "we need a path to work on, path=null detected");
        this.resolveLink = resolveLinks;
        this.path = path.normalize();

    }

    public List<FileAnalysis> getFiles(boolean rec) {
        if (!isFolder() || !rec) {
            return Collections.emptyList();
        }

        List<FileAnalysis> list = new ArrayList<>();
        File[] items = getResolvedFile().listFiles();

        if (items != null) {
            for (File f : items) {
                FileAnalysis fa = new FileAnalysis(f.toPath(), resolveLink);
                if (fa.isFolder()) {
                    list.addAll(fa.getFiles(true));
                } else {
                    if (!fa.isFolder()) {
                        list.add(fa);
                    }
                }
            }

        }

        return list;

    }

    public Runnable calculateSHATask(BiConsumer<Object, Map<String, String>> listener, Long blSize, boolean forceBL) {
        return new ChecksumCalculation(getLastModifiedTime(), this, listener, blSize, forceBL);
    }

    public long getFileSize() {
        if (!exists()) {
            return 0;
        }
        // fehler nur dann wenn im lesen gelöscht wird.
        try {
            return Files.size(getResolvedPath());
        } catch (IOException ex) {
            Logger.getLogger(FileAnalysis.class.getName()).log(Level.SEVERE, null, ex);
            return -1; // keine datei hat i.d.r. 0 aber ich will den fehler
            // erkennen.
        }
    }

    public boolean isSymbolicLink() {
        return Files.isSymbolicLink(getPath());
    }

    public boolean exists() {
        return Files.exists(getResolvedPath());
    }

    public String getOwner() {
        try {
            UserPrincipal up = Files.getOwner(getResolvedPath());
            if (up == null) {
                return null;
            }
            return up.getName();
        } catch (UnsupportedOperationException | IOException e) {
            return null;
        }
    }

    public String getFileKey() {
        BasicFileAttributes basic = getBasicFileAttributes();
        if (basic == null) {
            return null;
        }
        Object obj = basic.fileKey();
        if (obj == null) {
            return null;
        }
        return String.valueOf(obj);
    }

    public long getLastAccessTime() {
        BasicFileAttributes basic = getBasicFileAttributes();
        if (basic == null) {
            return -1;
        }

        return basic.lastAccessTime().toMillis();
    }

    public long getLastModifiedTime() {
        BasicFileAttributes basic = getBasicFileAttributes();
        if (basic == null) {
            return -1;
        }
        return basic.lastModifiedTime().toMillis();
    }

    public long getCreationTime() {
        BasicFileAttributes basic = getBasicFileAttributes();
        if (basic == null) {
            return -1;
        }
        return basic.creationTime().toMillis();
    }

    public BasicFileAttributes getBasicFileAttributes() {
        BasicFileAttributeView view = getBasicFileAttributeView();
        if (view == null) {
            return null;
        }
        try {
            return view.readAttributes();
        } catch (IOException ex) {
            Logger.getLogger(FileAnalysis.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }

    public Path getPath() {
        return path;
    }

    public List<FileAnalysis> listFiles() {
        if (isFolder()) {
            ArrayList<FileAnalysis> flist = new ArrayList<>();

            File[] items = getResolvedFile().listFiles();

            if (items != null) {
                for (File f : items) {
                    FileAnalysis fa = new FileAnalysis(f.toPath(), resolveLink);
                    flist.add(fa);
                }
            }
            return flist;
        } else {
            return Arrays.asList(this);
        }
    }

    public File getResolvedFile() {
        return getResolvedPath().toFile();
    }


    public Path getResolvedPath() {
        if (cacheResolved != null) {
            return cacheResolved;
        }
        if (resolveLink && isSymbolicLink()) {
            try {
                cacheResolved = Files.readSymbolicLink(getPath()).toAbsolutePath().normalize();
            } catch (IOException ex) {
                Logger.getLogger(FileAnalysis.class.getName()).log(Level.SEVERE, "resolve link faild", ex);
                cacheResolved = getPath().toAbsolutePath().normalize();
            }
        } else {
            cacheResolved = getPath().toAbsolutePath().normalize();
        }
        return cacheResolved;
    }

    public boolean isReadable() {
        return Files.isReadable(getResolvedPath());
    }

    public boolean isWriteable() {
        return Files.isWritable(getResolvedPath());
    }

    public boolean isFolder() {
        return Files.isDirectory(getResolvedPath());
    }

    public boolean isUserDefinedFileAttributeViewSupported() {
        return supportedViews().contains("user");
    }

    public Set<String> supportedViews() {
        FileSystem fs = getResolvedPath().getFileSystem();
        if (fs == null) {
            return Collections.emptySet();
        }
        return fs.supportedFileAttributeViews();
    }

    public BasicFileAttributeView getBasicFileAttributeView() {
        if (!exists()) {
            return null;
        }
        return Files.getFileAttributeView(getResolvedPath(), BasicFileAttributeView.class);
    }

    public UserDefinedFileAttributeView getUserDefinedFileAttributeView() {
        if (!exists() && !isUserDefinedFileAttributeViewSupported()) {
            return null;
        }
        return Files.getFileAttributeView(getResolvedPath(), UserDefinedFileAttributeView.class);

    }

    public Map<String, String> sha512(long blSize) {
        return sha512(Long.MAX_VALUE, blSize, false);
    }

    public boolean wasModified(long lastTime) {
        return getLastModifiedTime() > lastTime;
    }

    public boolean wasNotModified(long lastTime) {
        return !wasModified(lastTime);
    }

    public Map<String, String> sha512(long modificationGard, Long blSize, boolean force) {
        if (!exists() || isFolder()) {
            return null;
        }
        return SHAUtils.sha512(getResolvedPath(), this::wasNotModified, modificationGard, blSize, force);
    }

    public long readLongAttribute(final String key, long defaultValue) {
        String value = readAttribute(key);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException nfe) {
            return defaultValue;
        }

    }

    public List<String> readAttributeNames() {
        final UserDefinedFileAttributeView view = getUserDefinedFileAttributeView();
        if (view == null) {
            return Collections.emptyList();
        }
        try {
            return Collections.unmodifiableList(view.list());
        } catch (IOException ex) {
            Logger.getLogger(FileAnalysis.class.getName()).log(Level.SEVERE, null, ex);
            return Collections.emptyList();
        }
    }

    public String readAttribute(final String key) {
        return readAttribute(key, "").trim();
    }

    public String readAttribute(final String key, String defaultValue) {
        final UserDefinedFileAttributeView view = getUserDefinedFileAttributeView();
        final String lookup = key;
        if (!readAttributeNames().contains(lookup)) {
            return defaultValue;
        }
        int size;
        try {
            size = view.size(lookup);
        } catch (Exception ex) {
            Logger.getLogger(FileAnalysis.class.getName()).log(Level.SEVERE, null, ex);
            return defaultValue;
        }

        if (size <= 0) {
            return defaultValue;
        }

        ByteBuffer buffer = ByteBuffer.allocate(size);
        String value = defaultValue;
        try {
            if (readAttributeNames().contains(lookup)) {
                view.read(lookup, buffer);
            } else {
                buffer = null;
            }
            if (buffer != null) {
                buffer.flip();
                value = charset.decode(buffer).toString();
            }
        } catch (Exception ex) {
            Logger.getLogger(FileWatchEvent.class.getName()).log(Level.SEVERE, null, ex);
            // nicht sperren
            // es kann sein, das die attribute verschwinden
            value = defaultValue;
        }

        return value;
    }

    public String deleteAttribute(final String key) {
        final UserDefinedFileAttributeView view = getUserDefinedFileAttributeView();

        if (readAttributeNames().contains(key)) {
            String read = readAttribute(key, null);
            if (view != null) {
                try {
                    view.delete(key);
                } catch (IOException e) {
                    return null;
                }
            }
            if (read == null || read.isEmpty()) {
                return null;
            }
            return read;
        }
        return null;
    }

    public boolean putAttribute(final String key, final Object myValue) {

        try {
            writeAttribute(key, myValue);
            return readAttribute(key).equals(myValue);
        } catch (IOException e) {
            return false;
        }

    }

    public void writeAttribute(final String key, final Object myValue) throws IOException {
        if (!exists()) {
            throw new IOException("file was removed befor writing attributes: " + key + "=" + myValue);
        }
        String value = null;
        if (myValue != null) {
            value = String.valueOf(myValue).trim();
        }

        if (value == null || value.isEmpty()) {
            throw new IOException("empty values are not allowed");
        }

        final UserDefinedFileAttributeView view = getUserDefinedFileAttributeView();
        final String lookup = key;

        if (view != null) {
            view.write(lookup, charset.encode(value));
        }

    }

    public Map<String, String> readAttributes(String prefix, String suffix) {
        Predicate<String> prefixFilter = item -> prefix == null ? true : item.startsWith(prefix);
        Predicate<String> suffixFilter = item -> suffix == null ? true : item.endsWith(suffix);

        return readAttributeNames().stream().filter(prefixFilter).filter(suffixFilter)
                .collect(Collectors.toMap(Function.identity(), k -> {
                    return readAttribute(k);
                }));

    }

    public void writeAttributes(Map<String, String> map) {
        // ignoriere die vorgänge die nicht erfolgreich waren
        map.forEach(this::putAttribute);
    }

}
