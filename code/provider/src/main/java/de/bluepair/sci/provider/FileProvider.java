/**
 * This file is part of provider.
 *
 * provider is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * provider is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with provider.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.bluepair.sci.provider;

import de.bluepair.commons.file.FileAnalysis;
import de.bluepair.commons.file.JarFileUpdater;
import de.bluepair.commons.jms.JMSClientAPI;
import de.bluepair.commons.watchservice.FileWatchEvent;
import de.bluepair.commons.watchservice.FileWatchEventType;
import de.bluepair.commons.watchservice.FileWatchService;
import de.bluepair.sci.client.SHAUtils;
import de.bluepair.sci.provider.transmit.FilePartInputStream;
import de.bluepair.sci.provider.transmit.FileSend;
import de.bluepair.sci.provider.transmit.PartialFile;

import javax.jms.JMSException;
import java.io.*;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class FileProvider {

    private final ConcurrentHashMap<String, FileResvBootstrap> bootstrap = new ConcurrentHashMap<>();
    private final Random zufall = new Random();
    boolean started = false;
    private FileWatchService service;
    private long blockSize = 625_000; // 5MB
    private JMSClientAPI jms;
    private ConcurrentHashMap<String, Long> blockSizes = new ConcurrentHashMap<>();

    public FileProvider(String firstDir) {
        jms = new JMSClientAPI();
        jms.addListener(this::adminCommands);
        jms.addListener(this::onMessage);
        // immer meine eigenen änderungen prüfen ;)
        // nur absolute paths
        service = new FileWatchService(Paths.get(firstDir).toAbsolutePath().normalize(), true);
        // nun kommen die sachen aus der config.
        service.addListener(this::onEvent);

    }

    public void start() {

        jms.start();

        service.startService();
        started = true;
        service.getTimer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (!bootstrap.isEmpty()) {
                    bootstrap.entrySet().stream().filter(e -> e.getValue().isClosed()).map(e -> {
                        e.getValue().cleanUP();
                        System.out.println("remove faild download - " + e.getKey());
                        return e.getKey();
                    }).forEach(bootstrap::remove);
                }
            }
        }, 15000, 15000);
        System.out.println("service online ...");
    }

    public void join() {
        try {
            jms.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void addWatcherWithBlockSize(Path path, long parseLong, boolean recalc) {
        blockSizes.put(path.toAbsolutePath().normalize().toString(), parseLong);
        service.addWatcher(path);
        if (recalc) {
            service.publishUpdateAll(path.toAbsolutePath().normalize(), true);
        }

    }

    public void adminCommands(Map<String, Object> map) {
        try {
            String cmd = (String) map.get("cmd");
            if (cmd == null) {
                return;
            }
            String to = (String) map.get("to");
            String sforce = (String) map.get("force");
            boolean force = sforce != null && sforce.equalsIgnoreCase("true");

            if (String.valueOf(System.getProperty("de.bluepair.nodelete")).equals("true")) {
                force = false;
            }

            boolean itsMe = to != null && !to.isEmpty() && jms.isSameClientOrEmpty(to);

            if (!itsMe) {
                return;

            }// ich bin gemeint
            // lokale ausführung

            switch (cmd) {
                case "exit":
                    stop();
                    break;
                case "restart":
                    JarFileUpdater.restart(getClass(), this::stop);
                    break;

                case "exec":

                    String pfad = (String) map.get("path");
                    String sha = (String) map.get("sha512");
                    String w8 = (String) map.get("waitfor");
                    boolean waitf = w8 != null && w8.equals("true");
                    final String xprog = (String) map.get("app");
                    final String prog = System.getProperty(xprog + ".path");



                    if (prog != null && !prog.isEmpty() && pfad != null) {
                        FileAnalysis fa = new FileAnalysis(pfad, false);
                        if (!fa.exists() || (sha != null && !fa.readAttribute("sha512").equals(sha))) {
                            return;
                        }
                        ArrayList<String> argsx = new ArrayList<>();
                        argsx.add(prog);
                        map.entrySet().stream().filter(e -> e.getKey().startsWith("arg.")).sorted((a, b) -> Integer.compare(Integer.parseInt(a.getKey().substring(4)), Integer.parseInt(b.getKey().substring(4))))
                                .map(e -> e.getValue().toString().replaceAll(System.getProperty(xprog + ".replacewithpath"), pfad)).forEach(argsx::add);
                        ProcessBuilder builder = new ProcessBuilder(argsx);
                        String logx = (String) map.get("logfile");

                        builder.redirectErrorStream(true);
                        if (logx != null) {
                            File log = new File(logx);
                            builder.redirectOutput(ProcessBuilder.Redirect.appendTo(log));
                        }


                        Process p = builder.start();
                        if (p.isAlive() && waitf) {
                            int exitValue = p.waitFor();
                        }

                        HashMap<String, Object>   xmap = new HashMap<>();
                        xmap.put("app",xprog);
                        xmap.put("topic","master");
                        xmap.put("command", argsx.stream().collect(Collectors.joining(" ")));

                        if (logx == null && !p.isAlive()) {

                            InputStream in = p.getInputStream();

                            StringBuilder sb = new StringBuilder();
                            try (BufferedReader br = new BufferedReader(new InputStreamReader(in));) {
                                String line = null;
                                while ((line = br.readLine()) != null) {
                                    sb.append(line + System.getProperty("line.separator"));
                                }
                            }


                            xmap.put("output", sb.toString());

                        } else {

                            xmap.put("output", "Process: " + p.toString() +" isAlive:"+p.isAlive());


                        }
                        JMSClientAPI.doSend(xmap);
                    } else if ( "true".equals(System.getProperty(xprog + ".internal","false"))){

                        map.put("cmd",xprog);
                        map.put("path",pfad);

                        // exec new internal command!
                        adminCommands(map);
                        onMessage(map);
                    }

                    break;

                case "property":

                    String key = (String) map.get("key");
                    String value = (String) map.get("value");
                    jms.put(key, value);
                    break;
                case "systemproperty":

                    String skey = (String) map.get("key");
                    String svalue = (String) map.get("value");
                    System.setProperty(skey, svalue);

                    break;
                case "publish":
                    // mache meine datei oeffentlich, also
                    // verteile die datei.

                    String file = (String) map.get("path"); // leerzeichen

                    if (file != null) {
                        FileAnalysis xpath = new FileAnalysis(file, true);
                        if (!xpath.exists()) {
                            return;
                        }
                        List<FileAnalysis> paths;
                        if (xpath.isFolder()) {
                            // nun für jedes subelement neu aufrufen
                            // da wir nun alle dateien im ordner veröffentlichen
                            paths = xpath.getFiles("true".equalsIgnoreCase(String.valueOf(map.get("recursion"))));
                        } else {
                            paths = Arrays.asList(xpath);
                        }

                        for (FileAnalysis path : paths) {

                            String xsha = path.readAttribute("sha512");

                            if (xsha.isEmpty()) {
                                // berechnen und nochmal testen ob wir
                                // senden können
                                // neueinfügen der nachricht wenn sha noch nicht da
                                // ist.
                                service.submit(path.calculateSHATask((a, b) -> {
                                            onMessage(map);
                                        }, blockSizes.getOrDefault(path.getResolvedPath().getParent().toString(), blockSize),
                                        blockSizes.containsKey(path.getResolvedPath().getParent().toString())));
                            }

                            if (path.exists() && !xsha.isEmpty()) {
                                // resv erzeugen und via server senden.

                                // alte werde uebernehmen
                                // nur einige werden ueberschreiben.
                                Map<String, Object> xmap = new HashMap<>(map);

                                xmap.remove("to");

                                xmap.put("cmd", "resv");
                                xmap.put("topic", "master");
                                xmap.put("from", jms.getID());
                                xmap.put("sha512", xsha);
                                // alle anderen bekannten hashes noch mit angeben

                                // wenn blocksize angegeben ist
                                if (map.containsKey("blocksize")) {
                                    xmap.putAll(SHAUtils.sha512(path, Long.parseLong(String.valueOf(map.get("blocksize"))),
                                            true));

                                    // alle anderen sha daten von mir
                                } else {
                                    xmap.putAll(path.readAttributes("sha512_", null));
                                }

                                xmap.put("size", path.getFileSize());
                                xmap.put("path", path.getResolvedPath().toString());

                                Path base = Paths.get("./").toAbsolutePath().normalize();
                                Path storx = path.getResolvedPath().toAbsolutePath().normalize().getParent();

                                if (storx.equals(base) || xmap.containsKey("target")) {
                                    xmap.remove("store");
                                } else if (storx.startsWith(base) && !storx.equals(base)) {
                                    xmap.put("store", storx.subpath(base.getNameCount(), storx.getNameCount()).toString());
                                }
                                // ich logge mich ein damit ich gleich senden darf
                                // relay
                                // jms.login(jms.getID());
                                jms.send(xmap);

                            }
                        }
                    }

                    break;
            }
        } catch (Exception ex) {
            Logger.getLogger(FileProvider.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public void onMessage(Map<String, Object> map) {
        try {

            String cmd = (String) map.get("cmd");
            if (cmd==null) {
                return;
            }
            String to = (String) map.get("to");
            String sforce = (String) map.get("force");


            String sha512 = (String) map.get("sha512");
            String path = (String) map.get("path"); // leerzeichen

            boolean force = sforce != null && sforce.equalsIgnoreCase("true");

            if (String.valueOf(System.getProperty("de.bluepair.nodelete")).equals("true")) {
                force = false;
            }


            switch (cmd) {

                case "update":
                    service.publishUpdateAll(force);

                    break;
                case "adddir":
                    addWatcherWithBlockSize(Paths.get(String.valueOf(path)),
                            Long.parseLong(String.valueOf(map.get("blocksize"))), force);

                    break;

                case "path4hash":
                    Map<String, String> data = new HashMap<>();
                    String xhash = (String) map.get("hash");
                    service.pathForHash(xhash).forEach(a -> {
                        String xpath = a.getResolvedPath().toString();
                        Map<String, String> r = a.readAttributes("sha512", null);
                        r.forEach((k, v) -> {
                            if (v.equals(xhash)) {
                                data.put(k, xpath);
                            }
                        });
                    });

                    jms.sendMap(data);

                    break;
                case "allhashes":
                    // sende einfach alle gerade bekannten hashes
                    data = service.allSHAAttributes();
                    jms.sendMap(data);
                    break;


                case "find":

                    if (path != null) {
                        FileAnalysis xpath = new FileAnalysis(path, true);
                        if (sha512 == null || xpath.readAttribute("sha512").equals(sha512)) {
                            FileWatchEvent ev = new FileWatchEvent(service, FileWatchEventType.UPDATE, path);
                            service.inject(ev);
                        }
                    }
                    break;

                case "resv":
                    // ich finde mich selbst
                    // das bin ich selbst
                    if (jms.isSameClientOrEmpty((String) map.get("from"))) {
                        return; // warum sollte ich eine datei an mich selber
                        // senden?

                    }

                    long size;
                    try {
                        size = (long) map.get("size"); // leerzeichen

                    } catch (Exception e) {
                        size = -1;
                    }

                    Path xtarget;
                {
                    Path store = Paths.get("./");
                    String tmStore = (String) map.get("store");
                    if (tmStore != null && !tmStore.trim().isEmpty()) {
                        store = Paths.get(tmStore);
                    }

                    String target = (String) map.get("target");
                    if (target != null && !target.trim().isEmpty()) {
                        xtarget = store.resolve(target);
                    } // das ist ein dir
                    else {
                        xtarget = store.resolve(Paths.get(path).getFileName());
                    }
                }

                Map<String, Object> mx = new HashMap<>();
                FileAnalysis test = new FileAnalysis(xtarget, true);
                // wenn ich die schon habe, dann sha prüfen
                if (test.exists()) {
                    if (test.readAttribute("sha512").equals(sha512)) {
                        // alles ok
                        // haben die datei schon
                        return;
                    } else if (!force) {
                        Logger.getLogger(FileProvider.class.getName()).log(Level.SEVERE,
                                "found differences but do not force resv - you should delete the file: {0} on one site",
                                path);
                        return;
                    }
                    // we have force
                    // do it
                }
                Path oldDownload = null;
                if (force) {
                    // lösche Target wenn size not match
                    if (test.exists()) {

                        try {
                            do {
                                oldDownload = test.getResolvedPath().getParent()
                                        .resolve(JMSClientAPI.getOID() + "_tmpfile");
                            } while (Files.exists(oldDownload));

                            // aktuell geht das wiederaufsetzen des dl nicht;
                            // daher ist dieser modus eher unwichtig.
                            Files.move(test.getResolvedPath(), oldDownload);
                        } catch (IOException e) {
                            Logger.getLogger(FileProvider.class.getName()).log(Level.SEVERE,
                                    "no way, can not create tmpfile");
                        }
                        try {
                            Files.deleteIfExists(test.getResolvedPath());
                        } catch (IOException e) {
                            // hope this works
                        }
                        // verschiebe in ein tmpFile und lösche es dann

                    }
                }

                {
                    // TODO: partail transfer
                  
                    // bestcase ich habe die Datei schon
                    List<FileAnalysis> pathes = service.pathForHash(sha512);
                    if (!pathes.isEmpty()) {
                        // datei hab ich nur noch an das ziel bewegen
                        try {
                            Files.copy(pathes.remove(0).getResolvedPath(), test.getResolvedPath());
                            return; // wir sind fertig, rest macht mein programm
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    // leider entweder fehler oder nur teile da.

                    Map<String, String> shaValues = new HashMap<>();
                    // leider keine stream api, da duplicates da sind.
                    // Collector muss man neu schreiben.
                    map.forEach((k, v) -> {
                        if (k.startsWith("sha512_")) {
                            shaValues.put(String.valueOf(v), k);
                        }
                    });

                    // nun suche alle pfade und
                    // schreibe die teile

                    PartialFile pFile = new PartialFile(test, size);

                    List<FileAnalysis> px = service.pathForHash(shaValues.keySet().toArray(new String[0]));

                    shaValues.forEach((k, v) -> {
                        String[] ranges = v.split("_");
                        if (ranges.length == 3) {
                            long start = Long.parseLong(ranges[1]);
                            long len = Long.parseLong(ranges[2]);

                            if (!px.isEmpty()) {

                                AtomicBoolean doWrite = new AtomicBoolean(true);
                                for (FileAnalysis a : px) {
                                    Map<String, String> r = a.readAttributes("sha512", null);

                                    r.forEach((kx, vx) -> {
                                        if (vx.equals(k) && doWrite.get()) {
                                            String[] bereich = kx.split("_");

                                            long start2 = Long.parseLong(bereich[1]);
                                            long len2 = Long.parseLong(bereich[2]);

                                            if (len2 != len) {
                                                Logger.getLogger(FileProvider.class.getName()).log(Level.SEVERE,
                                                        "hash collision? with: " + a.getResolvedPath() + " ->" + kx + "; "
                                                                + test.getResolvedPath() + " -> " + v);
                                            }

                                            // sollten nun kopieren
                                            try (SeekableByteChannel filePart = Files.newByteChannel(a.getResolvedPath(),
                                                    StandardOpenOption.READ);
                                                 InputStream in = new FilePartInputStream(filePart,
                                                         new long[]{start2, len2});) {
                                                pFile.write(start, in, k, len);
                                                doWrite.set(false);
                                            } catch (Exception e) {
                                            }

                                        }
                                    });
                                }
                            } else {
                                // anfordern
                                String line = start + " " + len + " " + k;
                                String old = (String) mx.put("ranges", line);
                                if (old != null) {
                                    mx.put("ranges", old + "," + line);
                                }
                            }
                        }

                    });

                    // nun war der test und die such nach vorberechneten daten
                    // erfolgreich
                    // kill nun den old download

                    if (force && oldDownload != null) {
                        try {
                            Files.deleteIfExists(oldDownload);
                        } catch (IOException e1) {
                            // ignoriere es, will nun weiter machen
                        }
                    }

                    if (test.exists() && mx.get("ranges") == null) {
                        // habe alles also ende;
                        return;
                    }

                }

                try {
                    if (Files.notExists(xtarget.getParent())) {
                        try {
                            Files.createDirectories(xtarget.getParent());
                        } catch (IOException ex) {
                            Logger.getLogger(FileProvider.class.getName()).log(Level.SEVERE, null, ex);
                            return; // bei fehler keine chance
                        }
                    }

                    // generiere zugang
                    String masterKey = JMSClientAPI.getOID();
                    String sendKey = JMSClientAPI.getOID();

                    // share timer (it will be to much load else)

                    Consumer<Runnable> startThread = r -> {Thread t = new Thread(r); t.start();};

                    FileResvBootstrap bs = new FileResvBootstrap(path, xtarget, size, sha512, masterKey, sendKey,
                            service.getTimer(), startThread);
                    // nun nachricht an den Server

                    mx.put("path", path);
                    mx.put("masterKey", masterKey);

                    // should change this
                    // route message
                    mx.put("topic", "master");
                    mx.put("cmd", "sendfile");
                    mx.put("from", jms.getID());

                    mx.put("sendKey", sendKey);
                    mx.put("sha512", sha512);
                    // echoService

                    // evtl kommt mehr als 1 zurück
                    mx.put("host", jms.tryEcho());
                    // meine IP ist?
                    // zum empfangen?

                    mx.put("port", bs.getPort());
                    bootstrap.put(sendKey + "_" +path, bs);
                    bs.start();
                    jms.send(mx);

                } catch (IOException ex) {
                    Logger.getLogger(FileProvider.class.getName()).log(Level.SEVERE, null, ex);
                }

                break;

                case "sendfile":


                    String masterKey = (String) map.get("masterKey");

                    String sendKey = (String) map.get("sendKey");

                    // wollte ich etwas von diesem haben?
                    FileResvBootstrap bs = bootstrap.get(sendKey + "_" +path);
                    if (bs != null) {
                        bs.finishBootstrap(map);
                        return;
                    }

                    if (!jms.isSameClientOrEmpty(to)) {

                        // also geht es anderwaltig weiter?
                        return; /// ende wenn ich nicht gemeint bin.
                    }

                    test = new FileAnalysis(Paths.get(path), true);
                    if (!test.exists()) {
                        return; // habe den file nicht mehr, ignoriere es
                    }

                    String ranges = (String) map.get("ranges"); // komma delimited
                    // ranges

                    // ranges
                    // ist die selbe datei gemeint?

                    if (sha512 != null && !sha512.isEmpty() && !test.readAttribute("sha512").equals(sha512)) {
                        return; // kann nicht sein, dass ich was versende dessen
                        // summe ich nicht hab.
                    }

                    // sha ist entweder nicht geprüft oder
                    // ignored
                    // 0 51, 51 511 ...
                    List<long[]> rangX = new ArrayList<>();
                    List<String> rangXChecks = new ArrayList<>();
                    if (ranges != null && !ranges.isEmpty()) {
                        String[] xrange = ranges.split(",");
                        for (String range : xrange) {
                            try {
                                String[] parts = range.split(" ");
                                long start = Long.parseLong(parts[0]);
                                long leng = Long.parseLong(parts[1]);
                                String checkSum = parts[2];
                                rangX.add(new long[]{start, leng});
                                rangXChecks.add(checkSum);
                            } catch (Exception e) {
                                // ignore just do not send this
                            }
                        }
                    }

                    // soweit weiß ich nun alles.
                    String host = (String) map.get("host");
                    String[] chooseHosts = new String[] {host};
                    if (host != null && host.contains(" ")) {
                        chooseHosts = host.split(" ");
                    }
                    int port = (int) map.get("port");
                    // filesend wenn ich zuständig bin!
                    InputStream stream = null;
                    if (rangX.isEmpty()) {
                        try {
                            stream = Files.newInputStream(test.getResolvedPath());
                        } catch (IOException ex) {
                            Logger.getLogger(FileProvider.class.getName()).log(Level.SEVERE, null, ex);
                            return; // fehler beim lesen
                        }
                    } else {
                        try {
                            stream = new FilePartInputStream(Files.newByteChannel(test.getResolvedPath()), rangX);
                        } catch (IOException ex) {
                            Logger.getLogger(FileProvider.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }

                    // ignoriere einfach die zwischencheckts, ist für die andere
                    // seite,
                    rangXChecks.clear();

                    FileSend sendFile = new FileSend(masterKey, sendKey, stream, chooseHosts, port);
                    service.submit(sendFile);

                    // so nun sollte das alles geklappt haben.
                    break;
            }

        } catch (JMSException ex) {
            Logger.getLogger(FileProvider.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void stop() {
        service.stopService();
        started = false;
        jms.stop(true);

        System.out.println("service stopped");

    }

    public void onFinish(FileWatchEvent event) {
        Map<String, Object> m = event.buildAttributeMap();
        // die map anreichern mit infos, das es sich um dateien handelt.
        m.put("type", "file");
        m.put("blocksize", blockSizes.getOrDefault(event.getResolvedPath().getParent().toString(), blockSize));
        jms.send(m);
    }

    public void onEvent(FileWatchEvent event) {
        if (started) {
            // System.out.println("on " + event);
            switch (event.getType()) {
                case DELETE:
                    onFinish(event);
                    break;
                case UPDATE:
                    if (event.isFolder()) {
                        onFinish(event);
                        break;
                    } // in den näschten fall
                    // da es eine datei IST, evtl. neu berechnen!

                    if ("FORCE_CALC".equals(String.valueOf(event.getUserData()))) {
                        // lösche alle zuvor gemachten attribute!
                        event.deleteAttributes(null);
                    }
                case CREATE:
                case FINISH:
                    // start calc
                    event.calculateSHATask((e, x) -> onFinish(e),
                            blockSizes.getOrDefault(event.getResolvedPath().getParent().toString(), blockSize),
                            blockSizes.containsKey(event.getResolvedPath().getParent().toString()));
                    break;
                // ignore events which will result in wrong calculation
                case MODIFY:
                case OVERFLOW:
                case ONCOPY:
                    break;
            }
        }
    }

    public void addWatchDir(String dir) {
        service.addWatcherRecursive(Paths.get(dir));
    }
}
