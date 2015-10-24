/**
 * This file is part of master.
 * <p>
 * master is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * master is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * <p>
 * You should have received a copy of the GNU Affero General Public License
 * along with master.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.bluepair.sci.master;

import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.core.config.Configuration;
import org.hornetq.core.config.impl.ConfigurationImpl;
import org.hornetq.core.server.JournalType;
import org.hornetq.core.settings.impl.AddressSettings;
import org.hornetq.jms.server.config.ConnectionFactoryConfiguration;
import org.hornetq.jms.server.config.JMSConfiguration;
import org.hornetq.jms.server.config.impl.ConnectionFactoryConfigurationImpl;
import org.hornetq.jms.server.config.impl.JMSConfigurationImpl;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EmbeddedJMS extends org.hornetq.jms.server.embedded.EmbeddedJMS {

    private File dataDir;


    public EmbeddedJMS(String dir) {
        super();

        if (dir == null) {
            dataDir = new File("embedded", "jms").getAbsoluteFile();
        } else {
            this.dataDir = new File(dir).getAbsoluteFile();
        }

        Configuration myConfiguration = createHornetQConfig();
        JMSConfiguration jmsConfig = createJmsConfig();

        setConfiguration(myConfiguration);
        setJmsConfiguration(jmsConfig);

    }

    private static JMSConfiguration createJmsConfig() {
        JMSConfiguration jmsConfig = new JMSConfigurationImpl();
        ConnectionFactoryConfiguration cfConfig = new ConnectionFactoryConfigurationImpl(
                "ConnectionFactory", false, Arrays.asList("connector"),
                "jms/ConnectionFactory");
        jmsConfig.getConnectionFactoryConfigurations().add(cfConfig);

        return jmsConfig;
    }

    public boolean createTopic(String topic) {
        try {
            return serverManager.createTopic(false, topic,
                    "jms/topic/" + topic, "topic/" + topic);
        } catch (Exception ex) {
            Logger.getLogger(EmbeddedJMS.class.getName()).log(Level.SEVERE,
                    null, ex);
        }

        return false;
    }

    public boolean createQueue(final String name, final boolean durable) {
        try {
            return serverManager.createQueue(false, name, null, durable, "jms/"
                    + name, "queue/" + name);
        } catch (Exception ex) {
            Logger.getLogger(EmbeddedJMS.class.getName()).log(Level.SEVERE,
                    null, ex);
        }

        return false;
    }

    private Configuration createHornetQConfig() {
        ConfigurationImpl cfg = new ConfigurationImpl();

        cfg.setPersistenceEnabled(false);
        // Enable the security or not
        cfg.setSecurityEnabled(false);
        cfg.setJMXManagementEnabled(false);


        // Add hort, port configuration
        HashMap<String, Object> params = new HashMap<>();
        // verbindungen immer erlauben
        // params.put("host", InetAddress.getLocalHost().getHostName());
        params.put("host", "0.0.0.0");
        // port is not touched 5445 for JMS,
        // JNDI port is needed for JNA
        // host is used for both
        // params.put("port", port);
        cfg.getAcceptorConfigurations()
                .add(new TransportConfiguration(
                        "org.hornetq.core.remoting.impl.netty.NettyAcceptorFactory",
                        params));

        cfg.getConnectorConfigurations()
                .put("connector",
                        new TransportConfiguration(
                                "org.hornetq.core.remoting.impl.netty.NettyConnectorFactory"));

        cfg.setJournalType(JournalType.NIO);

        // http://stackoverflow.com/questions/10739717/hornetq-embedded-can-i-set-paging-options-for-dynamic-addreses
        final AddressSettings addressSetting = new AddressSettings();
        addressSetting.setMaxSizeBytes(100 * 1024 * 1024); // 100 MB
        addressSetting.setPageSizeBytes(1024 * 1024); // 1 MB

        final Map<String, AddressSettings> addressing = new HashMap<>();
        addressing.put("#", addressSetting); // the # pattern matches all
        // addresses
        cfg.setAddressesSettings(addressing);

        // Set up the file system directory will contain the data of HornetQ
        // server
        cfg.setPagingDirectory(new File(dataDir, "paging").getAbsolutePath());
        cfg.setBindingsDirectory(new File(dataDir, "bindings")
                .getAbsolutePath());
        cfg.setJournalDirectory(new File(dataDir, "journal").getAbsolutePath());
        cfg.setLargeMessagesDirectory(new File(dataDir, "large-messages")
                .getAbsolutePath());

        return cfg;
    }

}
