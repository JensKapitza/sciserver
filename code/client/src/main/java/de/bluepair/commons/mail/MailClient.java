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
package de.bluepair.commons.mail;

import javax.mail.*;
import javax.mail.Message.RecipientType;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class MailClient {

    private final Properties props = new Properties();
    private String email;
    private String password;
    private Session session;
    private Store store;
    private Folder inbox;
    private String username;

    public MailClient(String username, String email, String password,
                      String mailServer, int smtpPort) throws IOException {
        super();
        this.username = username;
        this.email = email;
        this.password = password;



        props.setProperty("mail.imap.starttls.enable", "true");
        props.setProperty("mail.smtp.starttls.enable", "true");

        props.put("mail.smtp.port", smtpPort);
        props.put("mail.smtp.host", mailServer);


        // mehr dazu: https://javamail.java.net/nonav/docs/api/com/sun/mail/smtp/package-summary.html
        // SSL alles erlaubt
        props.put("mail.smtp.ssl.trust", "*");
        // SSL alles erlaubt
        props.put("mail.imap.ssl.trust", "*");

        props.put("mail.smtp.auth", "true");

        // nun props aus System holen

        for (Map.Entry<Object, Object> e : System.getProperties().entrySet()) {
            // nur meine Keys und dann als normale eigenschaften setzen
            if (e.getKey().toString().startsWith("de.bluepair.")) {
                props.put(e.getKey().toString().substring("de.bluepair.".length()),e.getValue());
            }
        }

        // sollte alle trusts setzen
        // evtl. auch andere SSL parameter!


        session = Session.getInstance(props, null);

        try {
            store = session.getStore("imap");
        } catch (NoSuchProviderException e) {
            throw new IOException("no provider to connect", e);
        }

    }

    public String getHost() {
        return props.getProperty("mail.smtp.host");
    }

    public void checkMailbox(String folder, Consumer<JavaMailMessage> consumer) throws MessagingException {
        String oldBox = inbox == null ? "INBOX" : inbox.getFullName();
        changeFolder(folder);
        getMessages().forEach(consumer);
        changeFolder(oldBox);

    }

    public boolean isConnected() {
        return store.isConnected() && inbox != null && inbox.isOpen();
    }

    public void connect() throws MessagingException {
        store.connect(getHost(), getUserName(), password);
        inbox = store.getFolder("INBOX");
        inbox.open(Folder.READ_WRITE);
    }

    public void changeFolder(String folderName) throws MessagingException {
        inbox.close(false);
        inbox = store.getFolder(folderName);
        inbox.open(Folder.READ_WRITE);
    }

    public void close() throws MessagingException {
        inbox.close(false);
        store.close();
    }

    public String getUserName() {
        return username;
    }

    public List<JavaMailMessage> getMessages() throws MessagingException {
        ArrayList<JavaMailMessage> msgs = new ArrayList<>();
        if (inbox.getMessageCount() > 0) {
            for (int i = 1; i <= inbox.getMessageCount(); i++) {
                Message msg = inbox.getMessage(i);
                msgs.add(new JavaMailMessage(this, msg));
            }
        }
        return msgs;
    }

    public String getContent(Message msg) throws IOException,
            MessagingException {
        if (msg.getContent() instanceof Multipart) {
            Multipart mp = (Multipart) msg.getContent();
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart bp = mp.getBodyPart(i);
                builder.append(bp.getContent());
            }
            return builder.toString();
        } else {

            return msg.getContent().toString();
        }

    }

    public List<JavaMailMessage> expunge() throws MessagingException {
        return Arrays.asList(inbox.expunge()).stream()
                .map(msg -> new JavaMailMessage(this, msg))
                .collect(Collectors.toList());
    }

    public void send(String to, String subject, String message)
            throws MessagingException {
        send(createMessage(to, null, null, subject, message));
    }

    public void send(Message message) throws MessagingException {
        message.setSentDate(new Date());
        Transport.send(message, getUserName(), password);
    }

    public Message createMessage(String to, String cc, String bcc,
                                 String subject, String msg) throws MessagingException {
        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(email));
        message.setRecipients(RecipientType.TO, InternetAddress.parse(to));
        if (cc != null) {
            message.setRecipients(RecipientType.CC, InternetAddress.parse(cc));
        }
        if (bcc != null) {
            message.setRecipients(RecipientType.BCC, InternetAddress.parse(bcc));
        }

        message.setSubject(subject);
        message.setText(msg);

        return message;
    }

}
