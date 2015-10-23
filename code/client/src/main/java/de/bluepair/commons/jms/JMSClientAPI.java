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
package de.bluepair.commons.jms;

import de.bluepair.sci.client.OSPrinter;
import de.bluepair.sci.client.SHAUtils;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.jms.HornetQJMSClient;
import org.hornetq.api.jms.JMSFactoryType;
import org.hornetq.core.remoting.impl.netty.NettyConnectorFactory;
import org.hornetq.jms.client.HornetQConnectionFactory;

import javax.jms.*;
import javax.jms.Queue;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JMSClientAPI implements Runnable {

    public static final String SYSTEM_JMS_MASTERKEY = "de.bluepair.jms.masterkey";
    public static final String SYSTEM_JMS_AUTOCONNECT = "de.bluepair.jms.autoconnect";
    public static final String SYSTEM_JMS_HOST = "de.bluepair.jms.host";
    public static final String SYSTEM_JMS_MASTER_TOPIC = "de.bluepair.jms.topic.master";
    public static final String SYSTEM_JMS_PORT = "de.bluepair.jms.port";
    public static final String SYSTEM_JMS_CLIENTID = "de.bluepair.jms.client.id";
    public static final String SYSTEM_JMS_ECHO_PORT = "de.bluepair.jms.echo.port";
    private Thread clientThread;
    private Map<String, String> env = new ConcurrentHashMap<>();
    private BiConsumer<JMSClientAPI, Message> listener;
    private boolean forceHold;

    public JMSClientAPI() {
        addAll(System.getProperties());
        addAll(System.getenv());
    }

    public JMSClientAPI(String host) {
        this();
        put(SYSTEM_JMS_HOST, host);
    }

    public JMSClientAPI(String host, String topic) {
        this(host);
        put(SYSTEM_JMS_MASTER_TOPIC, topic);
    }

    private static MapMessage translate(MapMessage m, Map<String, Object> o) {
        o.forEach((name, value) -> {
            try {
                if (value != null) {
                    if (value instanceof Number || value instanceof Boolean) {
                        m.setObject(name, value);
                    } else {
                        m.setObject(name, String.valueOf(value));
                    }

                }
            } catch (JMSException ex) {
                Logger.getLogger(JMSClientAPI.class.getName()).log(
                        Level.SEVERE, null, ex);
            }
        });
        return m;
    }

    public static Map<String, Object> translate(Message o) {
        if (o instanceof MapMessage) {
            return translate((MapMessage) o);
        }
        if (o instanceof TextMessage) {
            return translate((TextMessage) o);
        }
        return Collections.emptyMap();
    }

    private static Map<String, Object> translate(TextMessage o) {
        HashMap<String, Object> map = new HashMap<>();
        try {

            Enumeration<?> it = o.getPropertyNames();
            while (it.hasMoreElements()) {
                String key = String.valueOf(it.nextElement());
                Object value = o.getObjectProperty(key);
                if (value != null) {
                    map.put(key, value);
                }
            }

            map.put("text", o.getText());

        } catch (JMSException ex) {
            Logger.getLogger(JMSClientAPI.class.getName()).log(Level.SEVERE,
                    null, ex);
        }
        return map;
    }

    private static Map<String, Object> translate(MapMessage o) {
        HashMap<String, Object> map = new HashMap<>();
        try {

            Enumeration<?> it = o.getPropertyNames();
            while (it.hasMoreElements()) {
                String key = String.valueOf(it.nextElement());
                Object value = o.getObjectProperty(key);
                if (value != null) {
                    map.put(key, value);
                }
            }

            it = o.getMapNames();
            while (it.hasMoreElements()) {
                String key = String.valueOf(it.nextElement());
                // HornetQ gibt den richtigen Typen weiter!
                Object value = o.getObject(key);
                if (value != null) {
                    map.put(key, value);
                }
            }

        } catch (JMSException ex) {
            Logger.getLogger(JMSClientAPI.class.getName()).log(Level.SEVERE,
                    null, ex);
        }
        return map;
    }

    public static String getOID() {
        return UUID.randomUUID().toString();
    }

    private static HornetQConnectionFactory getConnectionFactory(String host) {
        // Add hort, port configuration
        HashMap<String, Object> params = new HashMap<>();
        // verbindungen immer erlauben
        params.put("host", host);
        // port is not touched 5445 for JMS,

        String connector = NettyConnectorFactory.class.getName();
        TransportConfiguration transportConfiguration = new TransportConfiguration(
                connector, params);
        return HornetQJMSClient.createConnectionFactoryWithoutHA(
                JMSFactoryType.CF, transportConfiguration);

    }

    private static Queue getMasterQueue() {
        return (Queue) getDestination(true, "master");
    }
    private static Topic getMasterTopic() {
        return (Topic) getDestination(false, "master");
    }

    protected static Destination getDestination(boolean queue, String name) {
        if (queue) {
            return HornetQJMSClient.createQueue(name);
        }
        return HornetQJMSClient.createTopic(name);
    }

    private static Map<String, Object> fill(String id, Map<String, Object> map) {
        // avoid npe
        return fill(id, map, System.getenv());
    }

    private static Map<String, Object> fill(String id, Map<String, Object> map, Map<String, String> env) {
        Enumeration<NetworkInterface> netif;
        StringBuilder b = new StringBuilder();
        try {
            netif = NetworkInterface.getNetworkInterfaces();
            while (netif.hasMoreElements()) {
                NetworkInterface nif = netif.nextElement();
                if (!nif.isLoopback()) {
                    b.append(toString(nif.getInetAddresses(), " "));
                    if (netif.hasMoreElements()) {
                        b.append(" ");
                    }
                }

            }
        } catch (SocketException ex) {
            Logger.getLogger(JMSClientAPI.class.getName()).log(Level.SEVERE,
                    null, ex);
        }
        String prefix = "";
        if (id != null) {
            prefix = id + "_";
            // do not overwrite
            if (!map.containsKey("from")) {
                map.put("from", id);
            }
        }
        // mehr infos zum sender adden
        map.put(prefix + "system", OSPrinter.getSystemInfo());

        map.put(prefix + "net", b.toString());


        String vKey = env.get(SYSTEM_JMS_MASTERKEY);
        if (vKey == null) {
            vKey = System.getProperty(SYSTEM_JMS_MASTERKEY);
        }
// relay for all who know the key
        String masterCheck = SHAUtils.sha512(vKey);
        if (masterCheck != null && !masterCheck.isEmpty()) {
            map.put("masterhash", masterCheck);
        }


        return map;
    }

    public static void doSend(Map<String, Object> message) {
        doSend(System.getProperty(SYSTEM_JMS_HOST), message, null, -1);
    }

    public static void doSend(Map<String, Object> message,
                              Consumer<Map<String, Object>> answer, long timeout) {
        doSend(System.getProperty(SYSTEM_JMS_HOST), message, answer, timeout);
    }


    public static void doSend(Map<String, Object> message,
                              Consumer<Map<String, Object>> answer) {
        doSend(System.getProperty(SYSTEM_JMS_HOST), message, answer, -1);
    }


    public static void doSend(String host, Map<String, Object> message) {
        doSend(host, message, null, -1);
    }

    public static void doSend(String host, Map<String, Object> message,
                              Consumer<Map<String, Object>> answer, long timeout) {
        List<Message> receivedMessages = new ArrayList<>();
        MessageConsumer consumer = null;
        HornetQConnectionFactory cf = getConnectionFactory(host);
        try (Connection connection = cf.createConnection();
             Session s = connection.createSession(Session.AUTO_ACKNOWLEDGE);
             MessageProducer producer = s.createProducer(getMasterQueue());
        ) {

            if (answer != null) {
                consumer = s.createConsumer(getMasterTopic());
            }
            connection.start();
            Message m = translate(s.createMapMessage(),
                    fill(System.getProperty(SYSTEM_JMS_CLIENTID), message));
            if (timeout > 0) {
                producer.setTimeToLive(timeout);
            }
            producer.send(m);
            if (consumer != null) {
                Message receivedMessage = consumer.receive(timeout < 0 ? 5000 : timeout);
                do {
                    if (receivedMessage != null) {
                        // receivedMessage überall hin nur nicht zurück an
                        // mich!
                        receivedMessages.add(receivedMessage);
                    }
                    // falls mehr als eine Antwort kommt
                    receivedMessage = consumer.receiveNoWait();
                } while (receivedMessage != null);

                consumer.close();
            }
            // seams that autoclose is not working
            if (s.getTransacted()) {
                s.commit();
            }

        } catch (JMSException ex) {
            Logger.getLogger(JMSClientAPI.class.getName()).log(Level.SEVERE,
                    null, ex);
        } finally {
            cf.close();
        }

        if (answer != null) {
            receivedMessages.forEach(mx -> answer.accept(translate(mx)));
        }


    }

    private static String toString(Enumeration<?> items, String sep) {
        StringBuilder b = new StringBuilder();
        while (items.hasMoreElements()) {
            b.append(items.nextElement());
            if (items.hasMoreElements()) {
                b.append(sep);
            }
        }

        return b.toString();
    }

    public final void addAll(Map<?, ?> env) {
        env.entrySet().stream().forEach(e -> {
            try {
                this.env.put(e.getKey().toString(), e.getValue().toString());
            } catch (NullPointerException npe) {
                // ignore this!
            }
        });
    }

    public void setEnv(Map<String, String> env) {
        this.env = env;
    }

    public final String get(String key){
        return env.get(key);
    }

    public final String put(String key, String value) {
        if (value == null) {
            return env.remove(key);
        }
        return env.put(key, value);
    }

    protected void addMessageListener(Consumer<Message> listener) {
        addMessageListener((a, b) -> listener.accept(b));
    }

    public void addListener(Consumer<Map<String, Object>> listener) {
        addListener((a, b) -> listener.accept(b));
    }

    public void addListener(
            BiConsumer<JMSClientAPI, Map<String, Object>> listener) {
        addMessageListener((a, b) -> listener.accept(a, translate(b)));
    }

    protected synchronized void addMessageListener(
            BiConsumer<JMSClientAPI, Message> listener) {
        if (this.listener == null) {
            this.listener = listener;
        } else {
            this.listener = this.listener.andThen(listener);
        }
    }

    private HornetQConnectionFactory getConnectionFactory() {
        return getConnectionFactory(getJMSHost());

    }

    public void setMasterTopic(String topicName) {
        env.put(SYSTEM_JMS_MASTER_TOPIC, topicName);
    }

    private Topic getTopic() {
        return (Topic) getDestination(false,
                env.getOrDefault(SYSTEM_JMS_MASTER_TOPIC, "master"));
    }

    private boolean isAutoconnect() {
        return env.getOrDefault(SYSTEM_JMS_AUTOCONNECT, "false")
                .equalsIgnoreCase("true");
    }

    protected void setAutoconnect(boolean state) {
        env.put(SYSTEM_JMS_AUTOCONNECT, String.valueOf(state));
    }

    private int getEchoPort() {
        String port = env.getOrDefault(SYSTEM_JMS_ECHO_PORT, "8888");
        try {
            return Integer.parseInt(port);
        } catch (NumberFormatException nfe) {
            Logger.getLogger(JMSClientAPI.class.getName()).log(Level.SEVERE,
                    null, nfe);
            return 8888;
        }
    }

    protected String getJMSHost() {
        return env.getOrDefault(SYSTEM_JMS_HOST, "localhost");
    }

    public synchronized JMSClientAPI start() {
        if (forceHold) {
            return null;
            // nicht nochmal starten
            // wenn ende erzwungen wurde
        }

        if (clientThread == null) {
            clientThread = new Thread(this, "JMS_CLIENT connectionTO="
                    + getJMSHost());
            clientThread.start();
        }

        return this;

    }

    public void join() throws InterruptedException {
        clientThread.join();
    }

    public void stop() {
        stop(false);
    }

    public synchronized void stop(boolean killThread) {
        forceHold = killThread;
        if (!isAutoconnect() || forceHold) {
            Thread t = clientThread;
            clientThread = null;
            if (t != null) {
                t.interrupt();
            }
        }
    }

    public JMSClientAPI send(Map<String, Object> message) {
        start();
        doSend(getJMSHost(), message);
        return this;
    }

    public JMSClientAPI sendMap(Map<String, ?> message) {
        return send(new HashMap<>(message));
    }

    public String getID() {
        return env.get(SYSTEM_JMS_CLIENTID);
    }


    @Override
    public void run() {
        int errorWindow = 0;
        HornetQConnectionFactory cf = getConnectionFactory();
        do {

            try (Connection connection = cf.createConnection();
                 Session session = connection.createSession(false,
                         Session.AUTO_ACKNOWLEDGE);
                 MessageConsumer consumer = session
                         .createSharedDurableConsumer(getTopic(), "JMS_master_consumer_"+getID());) {
                connection.start();
                errorWindow = 0;
                while (!forceHold) {
                    try {
                        Message receivedMessage = consumer.receive(5000);
                        if (listener != null && receivedMessage != null) {
                            listener.accept(this, receivedMessage);
                        }

                    } catch (Exception e) {
                        Logger.getLogger(JMSClientAPI.class.getName()).log(
                                Level.FINEST, null, e);
                        break;
                    }
                }
            } catch (Exception e1) {
                e1.printStackTrace();
                if (forceHold) {
                    break;
                } else {
                    // we should w8 for connection come up
                    errorWindow += 1;
                    errorWindow = Math.min(errorWindow, 60);

                    synchronized (this) {
                        // hier werde ich aktive wenn send einmal erfolgreich
                        // war
                        try {
                            wait(1000 * errorWindow);
                        } catch (InterruptedException e) {
                            // mir egal, werde hier wieder landen, wenn ich noch
                            // immer keine verbindung hab.
                        }
                    }
                }
            }
        } while (isAutoconnect() && clientThread != null
                && clientThread.equals(Thread.currentThread()));
        // we died
        cf.close();
        clientThread = null;
        forceHold = true;
    }

    private String toStringInet(Enumeration<InetAddress> items, String sep) {
        StringBuilder b = new StringBuilder();
        while (items.hasMoreElements()) {
            String line = items.nextElement().getHostAddress();
            if (line.contains("%")) {
                // habe ipv6
                int pp = line.indexOf("%");
                b.append(line.substring(0, pp).trim());
            } else {
                b.append(line);
            }
            if (items.hasMoreElements()) {
                b.append(sep);
            }
        }

        return b.toString();
    }

    public boolean isSameClientOrEmpty(String provider) {
        return provider == null || provider.isEmpty()
                || (getID() != null && provider.equals(getID()));
    }

    public String tryEcho() {

        String myIP;
        try (Socket helper = new Socket(getJMSHost(), getEchoPort());) {
            helper.setSoTimeout(5000);

            try (InputStream in = helper.getInputStream();) {
                byte[] myxIP = new byte[1024];
                int z = in.read(myxIP);
                myIP = new String(myxIP, 0, z, "utf8");
            }

        } catch (IOException ex) {
            Logger.getLogger(JMSClientAPI.class.getName()).log(Level.SEVERE,
                    null, ex);
            myIP = "";
        }

        Enumeration<NetworkInterface> netif;
        try {
            netif = NetworkInterface.getNetworkInterfaces();
            StringBuilder b = new StringBuilder();
            while (netif.hasMoreElements()) {
                NetworkInterface nif = netif.nextElement();

                if (!nif.isLoopback() && nif.isUp()) {
                    if (b.length() > 0) {
                        b.append(" ");
                    }
                    b.append(toStringInet(nif.getInetAddresses(), " "));
                } else {
                    if (toStringInet(nif.getInetAddresses(), " ")
                            .contains(myIP)) {
                        myIP = "";
                    }
                }
            }
            String t = b.toString();
            if (t.contains(myIP) || myIP.isEmpty()) {
                return t;
            } else {
                return myIP;
            }
        } catch (SocketException ex) {
            Logger.getLogger(JMSClientAPI.class.getName()).log(Level.SEVERE,
                    null, ex);
        }
        return "localhost";
    }

}
