package de.bluepair.sci.master;

import de.bluepair.commons.jms.JMSClientAPI;
import de.bluepair.sci.client.SHAUtils;
import org.hornetq.jms.client.HornetQConnectionFactory;

import javax.jms.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;


public class AdminBridge extends Thread {

    private AtomicBoolean printTextMessages = new AtomicBoolean();

    private AtomicBoolean forceHold = new AtomicBoolean();

    private Properties properties;
    private EmbeddedJMS jms;


    private HornetQConnectionFactory factory;

    private JMSContext jmsContext;
    private Topic topic;

    private JMSConsumer consumer;


    private Topic database;
    private Topic bridge;


    public AdminBridge(Properties properties, EmbeddedJMS jms) {

        this.properties = properties;
        this.jms = jms;

        Queue queue = (Queue) jms.lookup("jms/master");
        topic = (Topic) jms.lookup("jms/topic/master");


        factory = (HornetQConnectionFactory) jms
                .lookup("jms/ConnectionFactory");


        if (jmsContext == null) {
            jmsContext = factory.createContext(JMSContext.AUTO_ACKNOWLEDGE);
            jmsContext.setAutoStart(true);
        }

        consumer = jmsContext.createConsumer(queue);
        consumer.setMessageListener(this::handleMsg);

        // alle nachrichten immer auf
        // bridge und database legen
        // beide topics werden zum interconnect verwendet.

        bridge = (Topic) jms.lookup("jms/topic/bridge");
        database = (Topic) jms.lookup("jms/topic/database");

    }

    public void debugEnable() {
        printTextMessages.set(true);
    }

    public void onMessage(Map<String, Object> message, JMSProducer producer,
                          JMSContext context) {
        // System.out.println("GOT: " + message);
        if (printTextMessages.get()) {
            System.out.println(message);

        }

        message.remove("JMSXDeliveryCount");
        String hash = String.valueOf(message.remove("masterhash"));
        String destination = (String) message.remove("topic");

        String masterKey = properties.getProperty("de.bluepair.jms.masterkey");

        Topic topic = null;
        if (destination != null) {
            if (masterKey != null) {
                masterKey = SHAUtils.sha512(masterKey);
            }

            if (hash.equals(masterKey)) {
                topic = (Topic) jms
                        .lookup("jms/topic/" + destination);

            }
        }

        if (topic != null) {

            bridge(context, producer, message, bridge, database, topic);
        } else {
            bridge(context, producer, message, bridge, database);
        }

    }


    private void bridge(JMSContext context, JMSProducer producer,
                        Map<String, Object> msg, Topic... topics) {
        Objects.requireNonNull(context, "JMS Context need not to be null");
        Objects.requireNonNull(producer, "Producer need not to be null");
        MapMessage message = context.createMapMessage();
        msg.forEach((k, v) -> {

            if (printTextMessages.get()) {
               System.out.println(k + " -> " + v);
            }
            try {
                message.setObject(k, v);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        for (Topic topic : topics) {
            producer.send(topic, message);
        }

    }


    public boolean isStopped() {
        return forceHold.get();
    }

    public void setForceHold(boolean forceHold) {
        this.forceHold.set(forceHold);
    }


    private void handleMsg(Message message) {
        if (message instanceof MapMessage) {

            if (message != null) {
                Map<String, Object> map = JMSClientAPI.translate(message);
                try {
                    message.acknowledge();
                } catch (JMSException e) {
                    e.printStackTrace();
                }

                Thread tx = new Thread(() -> {

                    try (JMSContext ct2 = factory.createContext(JMSContext.SESSION_TRANSACTED);) {
                        onMessage(map, ct2.createProducer(), ct2);
                        if (ct2.getTransacted()) {
                            ct2.commit();
                        }

                    }


                });
                tx.start();
            }
        }

    }

    @Override
    public void run() {

        jmsContext.start();
        while (consumer != null && jmsContext != null && !forceHold.get()) {
            try {
                Thread.sleep(60000);
                // session ping
                jmsContext.start();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }
        forceHold.set(true);


        try {
            jmsContext.stop();
        } catch (Exception e) {
            // senden kann evtl. nicht klappen
            // ignore
        }
        try {
            if (consumer != null) {
                consumer.close();
            }
        } catch (Exception e) {
            // senden kann evtl. nicht klappen
            // ignore
        }
        consumer = null;

        try {
            jmsContext.close();
        } catch (Exception e) {
            // senden kann evtl. nicht klappen
            // ignore
        }
        jmsContext = null;
        // nun das JMS abstellen


    }

    private void send(Map<String, Object> msg, Topic... topics) {

        try (JMSContext ctx = factory
                .createContext(JMSContext.SESSION_TRANSACTED);) {

            MapMessage message = ctx.createMapMessage();

            msg.forEach((k, v) -> {
                try {
                    message.setObject(k, v);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            ctx.start();
            JMSProducer p = ctx.createProducer();
            p.setTimeToLive(0);
            for (Topic topic : topics) {
                p.send(topic, message);
            }


            if (ctx.getTransacted()) {
                ctx.commit();
            }

        }
    }


    public void sendX(HashMap<String, Object> message) {
        send(message, topic, bridge, database);
    }
}
