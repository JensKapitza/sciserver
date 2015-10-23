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
/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.bluepair.test;

import de.bluepair.commons.mail.MailClient;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.mail.MessagingException;
import org.junit.Assert;

/**
 *
 * @author kapitza
 */
public class MailTest {

 //   @Test
    public void testMail() throws IOException, MessagingException {
        MailClient mc = new MailClient("masterarbeit", "masterarbeit@bluepair.de", "demo1234#", "mail.bluepair.de", 587);
        mc.connect();
        String subject = "Das ist ein Test";
        String msg = "Super";
        mc.send("masterarbeit@bluepair.de", subject, msg);

        mc.checkMailbox("INBOX", m -> {
            try {
                Assert.assertEquals("subjecttest", subject, m.getMessage().getSubject().trim());
                Assert.assertEquals("bodychek", msg, m.getTextContent().trim());
            } catch (MessagingException ex) {
                Logger.getLogger(MailTest.class.getName()).log(Level.SEVERE, null, ex);
            }
        });

        mc.close();
    }
}
