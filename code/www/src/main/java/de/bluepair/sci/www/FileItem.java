/**
 * This file is part of www.
 *
 * www is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * www is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with www.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.bluepair.sci.www;

import java.util.ArrayList;
import java.util.List;

public class FileItem {

    // brauch ich weil SHA nicht eindeutig ist
    private String id;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }


    private String path;
    private String fileName;
    private String shortPath;
    private String sha;
    private long size;
    private String owner;
    private String provider;
    private String linkPath;

    private String internetShortcut;

    private String internetShortcutFull;
    private String tagPrev;
    private List<String> tags = new ArrayList<>();

    public FileItem() {
    }

    public String getInternetShortcutFull() {
        if (internetShortcutFull == null) {
            return getPath();
        }
        return internetShortcutFull;
    }

    public void setInternetShortcutFull(String internetShortcutFull) {
        this.internetShortcutFull = internetShortcutFull;
    }

    public String getTagPrev() {
        return tagPrev;
    }

    public void setTagPrev(String tagPrev) {
        this.tagPrev = tagPrev;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getShortPath() {
        return shortPath;
    }

    public void setShortPath(String shortPath) {
        this.shortPath = shortPath;

        if (shortPath != null) {
            setFileName(shortPath.substring(shortPath.lastIndexOf("/") + 1));
        }
    }

    public String getInternetShortcut() {
        if (internetShortcut == null) {
            return getPath();
        }
        return internetShortcut;
    }

    public void setInternetShortcut(String internetShortcut) {
        this.internetShortcut = internetShortcut;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getLinkPath() {
        return linkPath;
    }

    public void setLinkPath(String linkPath) {
        this.linkPath = linkPath;
    }

    public boolean hasLink() {
        return linkPath != null && !linkPath.isEmpty();
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getSha() {
        return sha;
    }

    public void setSha(String sha) {
        this.sha = sha;
    }

    public String getPath() {
        return path;
    }


    public void setPath(String path) {
        this.path = path;
        setShortPath(path);


        if (path != null && path.length() > 50 ) {
            if (path.indexOf("/", path.length() - 40) > 0) {
                setShortPath("..." + path.substring(path.indexOf("/", path.length() - 40)));
            } else
            if (path.indexOf("\\", path.length() - 40) > 0) {
                setShortPath("..." + path.substring(path.indexOf("\\", path.length() - 40)));
            }
        }
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;

        StringBuilder b = new StringBuilder();
        if (tags != null) {
            int lastR = -1;
            for (int i = 0; i < tags.size() && b.length() < 10; i++) {
                String tagX = tags.get(i);
                // Attribute erlauben in den Tags
                if (tagX.contains(":") || tagX.contains("=")) {
                    if (tagX.contains(":")) {
                        tagX = tagX.split(":")[0];
                    } else if (tagX.contains("=")) {
                        tagX = tagX.split("=")[0];
                    }
                }
                if (b.length() > 0) {
                    b.append(",");
                }
                b.append(tagX);
                lastR = i;
            }
            lastR++;
            if (lastR < tags.size()) {
                if (b.length() > 0) {
                    b.append(",");
                }
                b.append("...");
            }
        }
        setTagPrev(b.toString());

    }
}
