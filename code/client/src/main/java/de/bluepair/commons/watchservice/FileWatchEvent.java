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
package de.bluepair.commons.watchservice;

import de.bluepair.commons.file.FileAnalysis;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FileWatchEvent {

    private final List<FileWatchEvent> suppressed = new CopyOnWriteArrayList<>();
    private FileWatchEventType type;
    private Object userData;
    private Date eventTime;
    private FileAnalysis pathAnalysis;
    private FileWatchService service;

    public FileWatchEvent(FileWatchService service, FileWatchEventType type, FileAnalysis path) {
        Objects.requireNonNull(path);
        this.type = type;
        pathAnalysis = path;
        this.service = service;

        if (equalsType(FileWatchEventType.UPDATE) && pathAnalysis.exists()) {
            eventTime = new Date(pathAnalysis.getLastModifiedTime());
        } else {
            eventTime = new Date();
        }
    }

    public FileWatchEvent(FileWatchService service, FileWatchEventType type, Path path) {
        this(service, type, new FileAnalysis(path, true));
    }

    public FileWatchEvent(FileWatchService service, FileWatchEventType type, String path) {
        this(service, type, new FileAnalysis(path, true));
    }

    public FileWatchEvent(FileWatchService service, FileWatchEventType watchEventType, Path changed, Object data) {
        this(service, watchEventType, changed);
        this.userData = data;
    }

    public FileWatchEvent(FileWatchService service, FileWatchEventType fileWatchEventType, FileWatchEvent oldEvent) {
        this(service, fileWatchEventType, oldEvent == null ? null : oldEvent.getResolvedPath());
        addSuppressed(oldEvent);
    }

    public String readAttribute(String key) {
        return pathAnalysis.readAttribute(key);
    }

    public long readLongAttribute(String key, long defValue) {
        return pathAnalysis.readLongAttribute(key, defValue);
    }

    public String deleteAttribute(String key) {
        return pathAnalysis.deleteAttribute(key);
    }

    public void writeAttribute(String key, String value) throws IOException {
        pathAnalysis.writeAttribute(key, value);
    }

    public Map<String, String> readAttributes(String prefix, String suffix) {
        return pathAnalysis.readAttributes(prefix, suffix);
    }

    public void writeAttributes(Map<String, String> map) {
        pathAnalysis.writeAttributes(map);
    }

    public FileWatchService getService() {
        return service;
    }

    FileAnalysis getPathAnalysis() {
        return pathAnalysis;
    }

    public Object getUserData() {
        return userData;
    }

    public void setUserData(Object userData) {
        this.userData = userData;
    }

    public long getFileSize() {
        return pathAnalysis.getFileSize();
    }

    public boolean isSymbolicLink() {
        return pathAnalysis.isSymbolicLink();
    }

    public boolean exists() {
        return pathAnalysis.exists();
    }

    public String getOwner() {
        return pathAnalysis.getOwner();
    }

    public Object getFileKey() {
        return pathAnalysis.getFileKey();
    }

    public long getLastAccessTime() {
        return pathAnalysis.getLastAccessTime();
    }

    public long getLastModifiedTime() {
        return pathAnalysis.getLastModifiedTime();
    }

    public long getCreationTime() {
        return pathAnalysis.getCreationTime();
    }

    public Path getResolvedPath() {
        return pathAnalysis.getResolvedPath();
    }

    public boolean isReadable() {
        return pathAnalysis.isReadable();
    }

    public boolean isWriteable() {
        return pathAnalysis.isWriteable();
    }

    public boolean isFolder() {
        return pathAnalysis.isFolder();
    }

    public Date getEventTime() {
        return eventTime;
    }

    public void setEventTime(Date eventTime) {
        this.eventTime = eventTime;
    }

    public FileWatchEventType getType() {
        return type;
    }

    public final void addSuppressed(List<FileWatchEvent> events) {
        suppressed.addAll(events);

    }

    public final void addSuppressed(FileWatchEvent event) {
        suppressed.add(event);
    }

    public List<FileWatchEvent> getSuppressed() {
        return Collections.unmodifiableList(suppressed);
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 79 * hash + Objects.hashCode(this.type);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final FileWatchEvent other = (FileWatchEvent) obj;
        if (this.type != other.type) {
            return false;
        }
        return Objects.equals(this.getResolvedPath(), other.getResolvedPath());
    }

    public boolean equalsPath(Object obj) {
        if (obj == null) {
            return false;
        }

        if (obj instanceof FileWatchEvent) {
            final FileWatchEvent other = (FileWatchEvent) obj;
            return Objects.equals(this.getResolvedPath(), other.getResolvedPath());
        } else if (obj instanceof Path) {

            return Objects.equals(this.getResolvedPath(), obj);
        }

        return false;
    }

    @Override
    public String toString() {
        return getResolvedPath() + " [ " + type + " ]  (" + getEventTime() + ")";
    }

    public boolean eventBefor(Date date) {
        return getEventTime().compareTo(date) < 0;
    }

    public boolean eventBefor(long time) {
        return getEventTime().compareTo(new Date(time)) < 0;
    }

    public boolean eventBefor(FileWatchEvent other) {
        return getEventTime().compareTo(other.getEventTime()) < 0;
    }

    public final boolean equalsType(FileWatchEventType type) {
        return getType().equals(type);
    }

    public void calculateSHATask(BiConsumer<FileWatchEvent, Map<String, String>> listener, Long blSize, boolean forceBL) {

        Runnable r = this.pathAnalysis.calculateSHATask(((a, b) -> {
            if (getUserData() == null) {
                setUserData(a);
            }
            listener.accept(this, b);
        }), blSize, forceBL);

        // berechnen und dann schreiben!
        // schreibe das attribute
        service.submit(r);

    }

    public Map<String, Object> buildAttributeMap() {

        // es sind nur primitive typen erlaubt,
        // path ist verboten im JMS
        final HashMap<String, Object> map = new HashMap<>();
        map.put("path", pathAnalysis.getPath().toAbsolutePath().toString());

        map.put("exists", exists());

        if (exists()) {
            Path res = getResolvedPath().toAbsolutePath();
            if (isSymbolicLink()) {
                map.put("symbolicLinkPath", res.toString());
            }

            try {
                map.put("mime", Files.probeContentType(res));
            } catch (Exception ex) {
                Logger.getLogger(FileWatchEvent.class.getName()).log(Level.SEVERE, null, ex);
            }
            map.put("executable", Files.isExecutable(res));

            map.put("symbolicLink", isSymbolicLink());
            map.put("folder", isFolder());
            map.put("readable", isReadable());
            map.put("writeable", isWriteable());
            map.put("userAttributes", pathAnalysis.isUserDefinedFileAttributeViewSupported());

            map.put("fileowner", String.valueOf(getOwner()));

            map.put("creationTime", getCreationTime());
            map.put("lastModifiedTime", getLastModifiedTime());
            map.put("lastAccessTime", getLastAccessTime());

            // windows does not have one!
            map.put("filekey", String.valueOf(getFileKey()));

            map.put("filesize", getFileSize());
            map.putAll(readAttributes(null, null));
        }
        return map;
    }

    public Map<String, String> sha512(long lastModifiedTime) {
        return pathAnalysis.sha512(lastModifiedTime);

    }

    public Map<String, String> sha512() {
        return sha512(getLastModifiedTime());

    }

    public void deleteAttributes(String prefix) {
        pathAnalysis.readAttributeNames().stream().filter(s -> prefix == null || s.startsWith(prefix)).forEach(this::deleteAttribute);

    }

}
