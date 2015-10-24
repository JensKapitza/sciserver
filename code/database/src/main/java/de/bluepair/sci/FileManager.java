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

import de.bluepair.sci.database.File;
import de.bluepair.sci.database.Tag;
import de.bluepair.sci.messages.ApplicationStart;
import de.bluepair.sci.messages.FileMessage;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.TypedQuery;
import java.util.Iterator;
import java.util.List;

@ApplicationScoped
public class FileManager {

    private EntityManager manager;


    private ApplicationStart app;

    public void main(@Observes ApplicationStart start) {
        app = start;
        manager = start.getFactory().createEntityManager();
    }

    public void onFile(@Observes FileMessage map) {

        String sha = map.getString("sha512");
        String tag = map.getString("tag");

        boolean delete = map.isEquals("delete", "true");
        EntityTransaction tx = manager.getTransaction();
        if (tx.isActive()) {
            tx.rollback();
        }

        manager.clear();
        tx.begin();

        TypedQuery<File> qf = manager.createQuery(
                "SELECT f FROM File f WHERE f.sha = :sha", File.class);
        qf.setParameter("sha", sha);
        for (File f : qf.getResultList()) {
            if (!delete) {
                //TODO owner des Tags angeben
                f.addTag(new Tag(tag));
            } else {
                Iterator<Tag> it = f.getTagList().iterator();
                while (it.hasNext()) {
                    Tag t = it.next();
                    if (t.getTagName().equals(tag)) {
                        it.remove();
                    }
                }
            }
            manager.merge(f);
        }


        // tags werden nie gelöscht
        if (tx.isActive()) {
            tx.commit();
        }
    }

    public void onFile(@Observes File file) {
        EntityTransaction tx = manager.getTransaction();
        if (tx.isActive()) {
            tx.rollback();
        }
        manager.clear();

        tx.begin();
        try {
            if (file.getSha() != null) {
                // insert oder update

                // gib es bereits ein file?
                TypedQuery<File> qf = manager
                        .createQuery(
                                "SELECT f FROM File f WHERE f.filePath = :path AND f.provider = :provider",
                                File.class);
                qf.setParameter("path", file.getFilePath());
                qf.setParameter("provider", file.getProvider());

                List<File> files = qf.getResultList();

                if (files != null && !files.isEmpty()) {
                    // wenn kein path trifft dann neu
                    // was macht weld hier?  NPE
                    for (File f : files) {
                        f.mergeUpdate(file);
                        manager.merge(f);
                    }

                } else {

// gibt es tags zur SHA summe?

                    // gib es bereits ein file?
                    qf = manager
                            .createQuery(
                                    "SELECT f FROM File f WHERE f.sha = :sha",
                                    File.class);
                    qf.setParameter("sha", file.getSha());
                    files = qf.getResultList();


                    files.forEach(f -> {
                        if (f.getTagList() != null) {
                            f.getTagList().forEach(file::addTag);
                        }
                    });

                    manager.persist(file);
                }
                // habe den file schon ich sollte ein update machen!


            } else {
                // das wird ein delete sein
                // da es sonst nicht eindeutig ist.

                TypedQuery<File> q = manager
                        .createQuery(
                                "SELECT f FROM File f WHERE  f.filePath = :path AND f.provider =:provider",
                                File.class);
                q.setParameter("path", file.getFilePath());
                q.setParameter("provider", file.getProvider());

                List<File> items = q.getResultList();
                items.forEach(manager::remove);
            }
        } finally {
            if (tx.isActive()) {
                tx.commit();
            }
        }

    }

}
