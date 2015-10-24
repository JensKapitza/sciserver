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

import de.bluepair.commons.mail.JavaMailMessage;
import de.bluepair.commons.mail.MailClient;
import de.bluepair.sci.database.File;
import de.bluepair.sci.database.Tag;
import de.bluepair.sci.database.User;
import de.bluepair.sci.messages.ApplicationStart;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.mail.MessagingException;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.TypedQuery;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


@ApplicationScoped
public class MailSender {


    private final Timer timer = new Timer();
    private ApplicationStart app;
    private MailClient client;

    private EntityManager manager;

    public void main(@Observes ApplicationStart start) {
        app = start;
        manager = start.createEntityManager();
        try {
            client = new MailClient(System.getProperty("de.bluepair.mail.user"), System.getProperty("de.bluepair.mail.address"), System.getProperty("de.bluepair.mail.password"), System.getProperty("de.bluepair.mail.host"), Integer.parseInt(System.getProperty("de.bluepair.mail.port", "25")));

            // um z.b. Tags hinzuzugeben
            final TimerTask beeper = new TimerTask() {
                public void run() {
                    // nachsehen ob es mails gibt.

                    if (!client.isConnected()) {
                        try {
                            client.connect();
                        } catch (MessagingException e) {
                            e.printStackTrace();
                        }
                    }
                    // sehe immer wieder mal nach antworten
                    try {
                        client.checkMailbox(System.getProperty("de.bluepair.mail.mailbox"), MailSender.this::mailChecker);
                        client.expunge(); // kill alle als gelöscht markierten
                    } catch (MessagingException e) {
                        e.printStackTrace();
                        try {
                            // es muss nicht daran liegen!
                            client.close();
                            // reconnect versuch
                            client.connect();
                        } catch (MessagingException re) {
                            e.printStackTrace();
                        }

                    }
                }
            };

            // default ist 1min oder angabe in der Properties
            timer.scheduleAtFixedRate(beeper, 0, Long.parseLong(System.getProperty("de.bluepair.mail.imap.polling", "60000")));
            // default ist 1min oder angabe in der Properties
            timer.schedule(new TimerTask() {

                @Override
                public void run() {
                    try {
                        remindNewMails();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }  // late run erste mal erst nach x sec.
            }, Long.parseLong(System.getProperty("de.bluepair.mail.reminder", "60000")), Long.parseLong(System.getProperty("de.bluepair.mail.reminder", "60000")));


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void remindNewMails() {

        // test SIZE(f.tagList) >= 1 AND
        TypedQuery<File> q = manager.createQuery("SELECT DISTINCT f FROM File f LEFT JOIN f.tagList t WHERE t.tagName IS NULL", File.class);
        List<File> items = q.getResultList();
        items.forEach(this::notifyUserViaMail);

    }


    private void notifyUserViaMail(File file) {

        // construct Link

        // lade alle User
        List<User> user = manager.createQuery("SELECT u FROM User u", User.class).getResultList();
        final TypedQuery<String> q = manager.createQuery("SELECT p.value FROM Property  p WHERE  p.key = :key", String.class);

        for (User u : user) {

            try {

                if ((u.getOwners() != null && u.getOwners().contains(file.getFileOwner())) || u.getUserName().equals(file.getFileOwner())) {
                    String mailAddr = u.getUserName();
                    if (!mailAddr.contains("@")) {
                        q.setParameter("key", "user.config." + u.getUserName() + ".email");
                        List<String> ix = q.getResultList();
                        if (ix.size() > 0) {
                            mailAddr = ix.get(0);
                        } else {
                            mailAddr = null;
                        }
                    }

                    if (mailAddr != null) {
                        // ist push an?
                        q.setParameter("key", "user.config." + u.getUserName() + ".email.push");
                        List<String> ix = q.getResultList();
                        String answer = null;
                        if (ix.size() > 0) {
                            answer = ix.get(0);
                        }
                        // NPW wenn nichts da ist, aber das kann hier egal sein!
                        if (answer != null && "true".equals(answer)) {
                            // mail link bauen

                            String urlX = "/redirect.xhtml?url=search.xhtml&path=" + file.getFilePath();

                            q.setParameter("key", "user.config." + u.getUserName() + ".email.baseurl");
                            // NPE wenn nichts da ist.
                            List<String> ask = q.getResultList();
                            answer = ask.isEmpty() ? null : ask.get(0);
                            if (answer != null && answer.startsWith("http")) {
                                urlX = answer + urlX;
                            } else {
                                String host = System.getProperty("de.bluepair.www.host",System.getProperty("de.bluepair.jms.host"));
				
                                urlX = "http://" + host + ":" + System.getProperty("de.bluepair.www.port", "8080") + urlX;
                            }

                            sendMailForFile(mailAddr, urlX, file);

                        }

                    }

                }
            } catch (Exception empty) {
                empty.printStackTrace();
            }
        }


    }


    public void mailChecker(JavaMailMessage msg) {
        try {
            String content = msg.getTextContent();
            if (content.contains("JAVA_MAIL") && content.contains("PATH=") && content.contains("SHA512")) {
                // das ist meine Mail also löschen nach bearbeiten
                // hier den roboter aktivieren
                // finde

                String shaNum = null;
                String[] lines = content.split("=");
                // finde index von SHA512
                int index = 0;
                for (String t : lines) {
                    String line = t.trim();
                    index++;
                    if (line.startsWith("SHA512")) {
                        break;

                    }
                }
                if (index < lines.length) {
                    shaNum = lines[index].replaceAll("\r", "").replaceAll("\n", "");
                }


                if (shaNum != null) {

                    EntityTransaction tx = manager.getTransaction();
                    if (tx.isActive()) {
                        tx.rollback();
                    }
                    manager.clear();

                    tx.begin();

                    TypedQuery<File> q = manager.createQuery("SELECT f FROM  File f WHERE f.sha LIKE :sha", File.class);
                    q.setParameter("sha", shaNum);
                    List<File> items = q.getResultList();

                    if (!items.isEmpty()) {

                        lines = content.split("\r\n");
                        for (String t : lines) {
                            String line = t.trim();
                            if (line.startsWith(">")) {
                                continue;
                            }

                            if (line.startsWith("TAGS=")) {
                                String tags = line.substring("TAGS=".length());

                                String[] tagsx = tags.trim().split(" ");
                                if (tags.contains(",")) {
                                    tagsx = tags.trim().split(",");
                                }

                                if (tagsx.length > 0) {

                                    for (String s : tagsx) {
                                        items.forEach(f -> {
                                            f.addTag(new Tag(s.trim()));
                                        });
                                    }


                                    items.forEach(manager::merge);

                                }
                            }
                        }


                        if (tx.isActive()) {
                            tx.commit();
                        }

                    }
                }

                msg.delete();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendMailForFile(String to, String redirectURL, File file) {
        StringBuilder text = new StringBuilder();
        text.append("JAVA_MAIL: ").append(file.getProvider());
        text.append("\n");
        text.append("PATH=").append(file.getFilePath());
        text.append("\n");
        text.append("\n");
        text.append("=SHA512=").append(file.getSha());
        text.append("=\n");
        text.append("\n");
        text.append("URL=").append(redirectURL);
        text.append("\n");
        text.append("\n");
        text.append("> Antworte mit TAGS= eine Liste mit Einträgen z.b. TAGS= wichtig, creator:heinz, ...");


        try {
            client.send(to, "JAVA_MAIL new File on Server, " + file.getProvider(), text.toString());
        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }
}
