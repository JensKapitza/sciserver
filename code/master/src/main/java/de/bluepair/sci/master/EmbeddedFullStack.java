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

import de.bluepair.sci.client.SHAUtils;
import org.glassfish.embeddable.GlassFishException;

import java.io.*;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EmbeddedFullStack {

    private final Properties properties = new Properties();

    private EmbeddedJMS jms;
    private EmbeddedJSF jsf;

    private Path baseDir;


    private IPEcho echo;

    private AdminBridge console;

    public EmbeddedFullStack() {
        this("embeddedServer");
    }

    public EmbeddedFullStack(String dataDir) {
        baseDir = Paths.get(dataDir);

        try {
            Files.createDirectories(baseDir);
        } catch (Exception e) {
            // ignore this,
            // if there is an error
            // dirs exist etc.?
        }

        loadProperties();
        // wenn masterkey nicht da, dann
        // erzeuge ich einen.

        if (!properties.containsKey("de.bluepair.jms.masterkey")) {
            properties.setProperty("de.bluepair.jms.masterkey", SHAUtils.sha512(UUID.randomUUID()
                    .toString()));
        }

        saveProperties();

    }

    public static void main(String[] args) throws Exception {

        // deploy ../www/target/www-1.0-SNAPSHOT.war www /
        EmbeddedFullStack fullStack = new EmbeddedFullStack();
        fullStack.start();
        fullStack.loadDefaults();

        System.out
                .println("-------------------------------------------------\n\n");
        System.out
                .println("system core is now online peers can connect to JMS: "
                        + InetAddress.getLocalHost().getCanonicalHostName()
                        + " PORT: " + fullStack.getPorts());
        System.out
                .println("\n\n-------------------------------------------------");
        BufferedReader in = null;

        try {

            Console console = System.console();
            if (console == null) {
                in = new BufferedReader(new InputStreamReader(System.in));
            }

            String line = null;
            do {
                if (console != null) {
                    console.printf("\n$:>");
                } else {
                    System.out.print("\n$:> ");
                }

                if (in != null) {
                    line = in.readLine().trim();
                }
                if (console != null) {
                    line = console.readLine();
                }

                if (line == null) {
                    line = "join";
                }
                int first = line.indexOf(' ');
                String content = "";
                if (first > 0) {
                    content = line.substring(first + 1).trim();
                }
                // ab send ist inhalt
                String[] items = line.split(" ");

                try {
                    switch (items[0]) {
                        case "masterkey":
                            System.out
                                    .println("masterkey: "
                                            + fullStack.properties
                                            .getProperty("de.bluepair.jms.masterkey"));
                            break;
                        case "newmasterkey":
                            fullStack.properties.setProperty("de.bluepair.jms.masterkey",
                                    SHAUtils.sha512(UUID.randomUUID().toString()));
                            fullStack.saveProperties();
                            break;


                        case "join":
                            fullStack.join();
                            break;
                        case "print":
                            fullStack.console.debugEnable();

                            System.out.println("print messages debuging enabled");

                            break;
                        case "deploy":

                            System.out.println(fullStack.jsf.runCommand("undeploy",
                                    items[2]));

                            fullStack.jsf.deploy(new File(items[1]), items[2],
                                    items.length > 3 ? items[3] : null);
                            break;
                        case "property":
                            fullStack.setProperty(items[1],
                                    items.length == 3 ? items[2] : null);
                            break;
                        case "send":
                            if (!content.isEmpty()) {
                                HashMap<String, Object> message = new HashMap<>();
                                String[] entries = content.split(" ");
                                for (String e : entries) {
                                    String[] lv = e.split("=");
                                    if (lv.length == 2) {
                                        message.put(lv[0], lv[1]);

                                        if (lv[1].chars().allMatch(
                                                Character::isDigit)) {
                                            message.put(lv[0],
                                                    Long.parseLong(lv[1]));
                                        }
                                    }
                                }

                                fullStack.console.sendX(message);
                            }
                            break;

                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("ERROR DETECTED (see above)");
                }
            } while (!line.equalsIgnoreCase("exit"));
            System.out.println("system will now shutdown");
            fullStack.stop();
            System.out.println("shutdown complete");

        } finally {
            if (in != null) {
                in.close();
            }
        }

        // TODO: fix threading in JMS sessions
        // kill hard cause there are open threads in JMS
        // sieht nach RMI threads aus, zumindest der port scheint es zu sein.
        System.exit(0);
    }


    private int getEchoPort() {
        String port = properties.getProperty("ipecho.port", "8888");

        int xport = -1;
        try {
            xport = Integer.parseInt(port);
        } catch (NumberFormatException nfe) {
            xport = 8888;
        }

        return xport;
    }

    public void start() throws Exception {

        if ( console != null && !console.isStopped()) {
            return;
        }


        if (echo == null) {
            echo = new IPEcho(getEchoPort());
            echo.start();
        }
        if (jms == null) {
            jms = new EmbeddedJMS(baseDir.resolve("jms").toString());
            jms.start();


            jms.createQueue("master", true);
            jms.createTopic("master");
            jms.createTopic("bridge");
            jms.createTopic("database");
            // nun kann ich einen NachrichtenListener Erzeugen!

        }


        if (jsf == null) {
            if (System.getProperty("de.bluepair.www", "false").equals("true")) {
                String port = properties.getProperty("jsf.port", "7070");

                int jsfport = -1;
                try {
                    jsfport = Integer.parseInt(port);
                } catch (NumberFormatException nfe) {
                    jsfport = 7070;
                }


                jsf = new EmbeddedJSF(jsfport, baseDir.resolve("jsf").toString());
                jsf.start();
            }
        }


        console = new AdminBridge(properties, jms);
        console.start();


    }

    @SuppressWarnings("unsafe")
    private void loadDefaults() {
        // hier aus den properties evtl. alles starten was ich benötige!
        // autodeploy von den anwendungen etc.

        // z.b. jms queues und topics?

        // datenbank hochfahren

        // call DatabaseMain


        if (System.getProperty("de.bluepair.databse", "false").equals("true")) {
            Thread thread = new Thread() {
                @Override
                public void run() {
                    try {
                        Class<?> databaseMain = Class.forName("main.DatabaseMain");

                        try {
                            databaseMain.getMethod("main", new Class[]{String[].class}).invoke(null, new Object[]{new String[0]});
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } catch (Exception e) {
                        // not found?! ignore it!
                        e.printStackTrace();
                    }
                }
            };

            // exec db
            thread.start();

        }
        if (System.getProperty("de.bluepair.www", "false").equals("true")) {
            File wwwFile = new File(System.getProperty("de.bluepair.www.file", "www.war"));
            if (wwwFile.exists()) {
                jsf.runCommand("undeploy", "www");
                try {
                    jsf.deploy(wwwFile, "www", "/");
                } catch (GlassFishException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    public void join() {
        if (console != null) {
            try {
                console.join();
            } catch (InterruptedException ex) {
                Logger.getLogger(EmbeddedFullStack.class.getName()).log(
                        Level.SEVERE, null, ex);
            }
        }
    }


    private void stop() {

        console.setForceHold(true);


        try {
            jms.stop();
        } catch (Exception ex) {
            Logger.getLogger(EmbeddedFullStack.class.getName()).log(
                    Level.SEVERE, null, ex);
        }

        echo.stopEcho();
        try {
            jsf.stop();
        } catch (Exception ex) {
            // if we are in stop.
            // ignore this
        }

    }

    private String getPorts() {
        return "(JMS/5445) (WWW/" + jsf.getPort() + ")";
    }

    private void setProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
            properties.remove("system." + key);
        } else {
            System.setProperty(key, value);
            properties.setProperty("system." + key, value);

        }
        saveProperties();
        // evtl. in die konfiguration schreiben.
    }


    private void saveProperties() {
        synchronized (properties) {
            try {
                properties.store(
                        Files.newBufferedWriter(
                                baseDir.resolve("server.properties"),
                                Charset.forName("utf8")), "last update: "
                                + new Date());
            } catch (IOException ex) {
                Logger.getLogger(EmbeddedFullStack.class.getName()).log(
                        Level.SEVERE, null, ex);
            }
        }
    }

    private void loadProperties() {
        synchronized (properties) {
            properties.clear();
            try {
                properties.load(Files.newBufferedReader(
                        baseDir.resolve("server.properties"),
                        Charset.forName("utf8")));
            } catch (NoSuchFileException nofile) {
                // ignored don't care we bootstrap first time
            } catch (IOException ex) {
                Logger.getLogger(EmbeddedFullStack.class.getName()).log(
                        Level.SEVERE, null, ex);
            }

// alle System properties hier nochmal rein
            properties.putAll(System.getProperties());


        }
    }

}
