/**
 * This file is part of database.
 * <p>
 * database is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * database is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * <p>
 * You should have received a copy of the GNU Affero General Public License
 * along with database.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.bluepair.sci;

import de.bluepair.commons.jms.JMSClientAPI;
import de.bluepair.sci.database.File;
import de.bluepair.sci.database.FileHash;
import de.bluepair.sci.database.Property;
import de.bluepair.sci.database.Tag;
import de.bluepair.sci.messages.ApplicationStart;
import de.bluepair.sci.messages.FileMessage;
import de.bluepair.sci.messages.Message;
import de.bluepair.sci.messages.WWWMessage;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.jms.JMSException;
import javax.persistence.EntityManager;
import java.util.Date;
import java.util.Map;

@ApplicationScoped
public class DatabaseManager {

    private JMSClientAPI jms;
    private ApplicationStart app;
    private EntityManager manager;

    public void main(@Observes ApplicationStart start) throws JMSException {
        jms = new JMSClientAPI();
        jms.setMasterTopic("database");
        this.app = start;
        jms.addListener(this::consumer);
        jms.start();
        manager = start.getFactory().createEntityManager();
    }

    public void consumer(Map<String, Object> map) {
        String type = (String) map.get("type");
        if (type == null) {
            // all others
            type = "default";
        }


        switch (type) {
            case "file":
                app.fire(toFile(map));
                break;
            case "tag":
                app.fire(new FileMessage(map));
                break;
            case "www":
                app.fire(new WWWMessage(map));
                break;
            default:
                app.fire(new Message(map));
        }


    }


    private File toFile(Map<String, Object> map) {

        File file = new File();
        file.setFilePath((String) map.get("path"));
        String provider = (String) map.get("from");
        if (provider != null && !provider.isEmpty()) {
            file.setProvider(provider);
        }
        String prefix = provider + "_";


        for (Map.Entry<String,Object> e : map.entrySet()){
            String key = e.getKey();
            if (key.startsWith(prefix)){
                String value = (String) e.getValue();
                if (!manager.getTransaction().isActive()) {
                    manager.getTransaction().begin();
                }


                Property p = new Property();
                p.setKey("config." + provider + "." + key.substring(prefix.length()).replace("_", "\\."));
                p.setValue(value);

                if (value == null || value.isEmpty()) {
                    manager.remove(p);
                } else {
                    manager.merge(p);
                }
            }
        }

        if (manager.getTransaction().isActive()) {
            manager.getTransaction().commit();
        }

        if (((boolean) map.get("exists"))) {

            file.setSha((String) map.get("sha512"));
            file.setFileSize((long) map.get("filesize"));

            file.setAccessTime(new Date((long) map.get("lastAccessTime")));
            file.setCreationTime(new Date((long) map.get("creationTime")));
            file.setModificationTime(new Date((long) map.get("lastModifiedTime")));

            file.setFileOwner((String) map.get("fileowner"));

            file.setWriteable((boolean) map.get("writeable"));

            file.setReadable((boolean) map.get("readable"));
            file.setFolder((boolean) map.get("folder"));
            file.setSymbolicLink((boolean) map.get("symbolicLink"));
            file.setExecutable((boolean) map.get("executable"));

            file.setContentType((String) map.get("mime"));

            file.setLinkPath((String) map.get("symbolicLinkPath"));

            file.setUserAttributes((boolean) map.get("userAttributes"));

            // alle sonstigen hashes speichern
            map.keySet().stream().filter(k -> k.startsWith("sha512_"))
                    .map(k -> new FileHash((String) map.get(k), (long) map.get("blocksize"))).forEach(file::addHash);


            map.entrySet().stream().forEach(e -> file.addSysTag(new Tag(e.getKey(), e.getValue())));

        }
        return file;

    }
}
