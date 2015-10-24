/**
 * This file is part of database.
 *
 * database is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * database is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with database.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.bluepair.sci.database;

import javax.persistence.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity(name = "File")
// diese Klasse bitte mit Tabelle files verbinden
@Table(name = "files")
public class File implements Serializable {


    private static final long serialVersionUID = 3663262160582339213L;
    // keine Mapping angaben
    // bedeuten immer @Column(name=FELD_NAME,\ldots)
    // und diverse weitere defaults
    // \"Anderungen m\"ussen nur bei bedarf gemacht werden.

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private long id;

    private String provider;
    private String filePath;
    private String linkPath;

    private String sha;
    private boolean symbolicLink;
    private boolean folder;
    private boolean readable;
    private boolean writeable;
    private boolean executable;
    private String fileOwner;
    // hier bitte das SQL Date YYYY-MM-DD HH:MI:SS in ein Java Datum umwandeln
    @Temporal(TemporalType.TIMESTAMP)
    private Date creationTime;
    @Temporal(TemporalType.TIMESTAMP)
    private Date modificationTime;
    @Temporal(TemporalType.TIMESTAMP)
    private Date accessTime;
    private String fileKey;
    private String contentType;
    private long fileSize;

    private boolean userAttributes;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name="TAGS",
            joinColumns=@JoinColumn(name="FILE_ID")
    )
    private List<Tag> tagList;


    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name="SYSTEM_TAGS",
            joinColumns=@JoinColumn(name="FILE_ID")
    )
    private List<Tag> sysTagList;


    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name="HASHS",
            joinColumns=@JoinColumn(name="FILE_ID")
    )
    private List<FileHash> hashList;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getLinkPath() {
        return linkPath;
    }

    public void setLinkPath(String linkPath) {
        this.linkPath = linkPath;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getSha() {
        return sha;
    }

    public void setSha(String sha) {
        this.sha = sha;
    }

    public boolean isSymbolicLink() {
        return symbolicLink;
    }

    public void setSymbolicLink(boolean symbolicLink) {
        this.symbolicLink = symbolicLink;
    }

    public boolean isFolder() {
        return folder;
    }

    public void setFolder(boolean folder) {
        this.folder = folder;
    }

    public boolean isExecutable() {
        return executable;
    }

    public void setExecutable(boolean executable) {
        this.executable = executable;
    }

    public boolean isReadable() {
        return readable;
    }

    public void setReadable(boolean readable) {
        this.readable = readable;
    }

    public boolean isWriteable() {
        return writeable;
    }

    public void setWriteable(boolean writeable) {
        this.writeable = writeable;
    }

    public String getFileOwner() {
        return fileOwner;
    }

    public void setFileOwner(String fileOwner) {
        this.fileOwner = fileOwner;
    }

    public Date getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(Date creationTime) {
        this.creationTime = creationTime;
    }

    public Date getModificationTime() {
        return modificationTime;
    }

    public void setModificationTime(Date modificationTime) {
        this.modificationTime = modificationTime;
    }

    public Date getAccessTime() {
        return accessTime;
    }

    public void setAccessTime(Date accessTime) {
        this.accessTime = accessTime;
    }

    public String getFileKey() {
        return fileKey;
    }

    public void setFileKey(String fileKey) {
        this.fileKey = fileKey;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public boolean isUserAttributes() {
        return userAttributes;
    }

    public void setUserAttributes(boolean userAttributes) {
        this.userAttributes = userAttributes;
    }

    public List<Tag> getTagList() {
        return tagList;
    }

    public void setTagList(List<Tag> tagList) {
        this.tagList = tagList;
    }

    public boolean addTag(Tag tag) {
        if (tagList == null) {
            tagList = new ArrayList<Tag>();
        }
        if (tagList.contains(tag)) {
            return false;
        }
        return tagList.add(tag);

    }

    public List<Tag> getSysTagList() {
        return sysTagList;
    }

    public void setSysTagList(List<Tag> sysTagList) {
        this.sysTagList = sysTagList;
    }

    public void addSysTag(Tag tag){
        if (sysTagList == null){
            sysTagList = new ArrayList<>();
        }
        if (!sysTagList.contains(tag)){
            sysTagList.add(tag);
        }
    }

    public List<FileHash> getHashList() {
        return hashList;
    }

    public void setHashList(List<FileHash> hashList) {
        this.hashList = hashList;
    }

    public boolean addHash(FileHash hash) {
        if (hashList == null) {
            hashList = new ArrayList<>();
        }
        if (hashList.contains(hash)) {
            return false;
        }
        return hashList.add(hash);
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }


    public void mergeUpdate(File file) {
        setAccessTime(file.getAccessTime());
        setModificationTime(file.getModificationTime());
        setFileOwner(file.getFileOwner());

        setContentType(file.getContentType());
        setCreationTime(file.getCreationTime());
        setExecutable(file.isExecutable());
        setFileKey(file.getFileKey());

        setFileSize(file.getFileSize());
        setFolder(file.isFolder());
        setLinkPath(file.getLinkPath());

        setReadable(file.isReadable());
        setSha(file.getSha());
        setSymbolicLink(file.isSymbolicLink());

        if (file.getTagList() != null) {
            if (getTagList()==null) {
                setTagList(file.getTagList());
            } else {
                getTagList().addAll(file.getTagList());
            }
        }

        if (file.getSysTagList()!= null){
            // no streams JPA does not support this
            for(Tag tag:file.getSysTagList()){
                addSysTag(tag);
            }
        }
        getHashList().addAll(file.getHashList());

    }
}
