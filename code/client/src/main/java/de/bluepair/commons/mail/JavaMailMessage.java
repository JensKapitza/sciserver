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
package de.bluepair.commons.mail;

import javax.mail.Address;
import javax.mail.Flags.Flag;
import javax.mail.Message;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import java.io.IOException;

public class JavaMailMessage {

    private MailClient client;
    private Message message;

    public JavaMailMessage(MailClient client, Message msg) {
        this.message = msg;
        this.client = client;
    }

    public Message getMessage() {
        return message;
    }

    public String getTextContent() throws MessagingException {
        try {
            return client.getContent(message);
        } catch (IOException e) {
            throw new MessagingException(e.getMessage());
        }
    }

    public void delete() throws MessagingException {
        message.setFlag(Flag.DELETED, true);
    }

    public void hold() throws MessagingException {
        message.setFlag(Flag.DELETED, false);
    }

    public Address[] getFrom() throws MessagingException {
        return message.getFrom();
    }

    public void answerAll(Message msg) throws MessagingException {
        msg.addRecipients(RecipientType.TO, getFrom());
        client.send(msg);
    }
}
