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
import de.bluepair.sci.provider.transmit.FileGetService;
import de.bluepair.sci.provider.transmit.PartialFile;

import javax.jms.JMSException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FileResvBootstrap extends Thread {

    private String path;
    private Path store;
    private String shaSum;
    private FileGetService service;

    private Date eventTime = new Date();
    private ArrayList<long[]> rangX;
    private Map<String, String> rangXChecks;

    private boolean wasCalled;
    private long[] currentPart;
    private FileAnalysis test;
    private long size;
    // man kann noch eine checkfunktion erstellen um zu prüfen ob alles richtig
    // war
    // zb. shasummen nutzen
    public FileResvBootstrap(String path, Path store, long size, String shaSum, String masterKey, String sendKey,
                             Timer timer, Consumer<Runnable> execHelper) throws IOException {
        super("bootstrap_fileget_" + path);
        this.path = path;
        this.shaSum = shaSum;
        this.store = store;
        this.size = size;
        test = new FileAnalysis(store, true);
        PartialFile.create(store, size);

        service = new FileGetService(masterKey, sendKey, this::consumeBytes, timer, execHelper);
        service.openSocket();
    }

    private synchronized void consumeBytes(byte[] data) {
        if (data == null) {

            // evtl sind wir einfach fertig
            // die checksumms anhängen und später prüfen lassen.
            return;
        }
        wasCalled = true;

        int kill = 0;
        // warte bis ranges da sind
        while (rangX == null && kill < 10) {
            try {
                wait(3000);
                kill += 1;
            } catch (InterruptedException ex) {
                Logger.getLogger(FileResvBootstrap.class.getName()).log(Level.SEVERE, null, ex);
            }

        }

        if (rangX == null) {
            // keine daten schreiben, da scheinbar was falsch ist.
            Logger.getLogger(FileResvBootstrap.class.getName()).log(Level.SEVERE,
                    "error on ranges: is finish bootstrap not called?");

            return;

        }
        if (rangX.isEmpty() && rangXChecks.isEmpty()) {
            Logger.getLogger(FileResvBootstrap.class.getName()).log(Level.SEVERE,
                    "ERROR not supported we need  a range?");
            return;
        }

        // in data sind mehrere ranges
        // ich muss da also nochmal splitten
        try (RandomAccessFile file = new RandomAccessFile(store.toFile(), "rw")) {
            int bytesRead = 0;
            do {

                if (rangX.isEmpty()) {
                    Logger.getLogger(FileResvBootstrap.class.getName()).log(Level.SEVERE, "data dropt no ranges?");
                    return; // scheinbar fertig
                }
                currentPart = rangX.get(0);
                file.seek(currentPart[0]);

                long len = currentPart[1];

                if (data.length >= len) {
                    // rest schreiben dann return
                    file.write(data, bytesRead, (int) len);
                    rangX.remove(0);

                } else {
                    len = data.length - bytesRead;
                    // hier schreibe ich komplett in data.
                    file.write(data, bytesRead, (int) len);

                }
                bytesRead += len;
                currentPart[1] -= len;
                currentPart[0] += len;

            } while (bytesRead < data.length);

        } catch (IOException ex) {
            Logger.getLogger(FileResvBootstrap.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    @Override
    public void run() {
        service.run();
    }

    public void cleanUP() {
        // delete files with no download

        if (!wasCalled && test.exists() && isClosed()) {

            service.close();

            try {
                Files.deleteIfExists(store);
            } catch (IOException ex) {
                Logger.getLogger(FileResvBootstrap.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }

    public boolean isClosed() {
        return service.isClosed();
    }

    public Path getNewPath() {
        return store;
    }

    public int getPort() {
        return service.getPort();
    }

    public Date getEventTime() {
        return eventTime;
    }

    public boolean isOlder(long millisec) {
        return System.currentTimeMillis() - eventTime.getTime() > millisec;
    }

    public void finishBootstrap(Map<String, Object> map) throws JMSException {

        String file = (String) map.get("path");

        if (!path.equals(file)) {
            return;
        }

        if (!service.getMasterKey().equals(map.get("masterKey"))) {
            return;
        }

        if (!shaSum.equals(map.get("sha512"))) {
            return; // es ist einfach das falsche!
        }

        String ranges = (String) map.get("ranges"); // komma delimited ranges
        wasCalled = true;
        // sha ist entweder nicht geprüft oder
        // ignored
        // 0 51, 51 511 ...
        rangX = new ArrayList<>();
        rangXChecks = new HashMap<>();
        if (ranges != null && !ranges.isEmpty()) {
            String[] xrange = ranges.split(",");
            for (String range : xrange) {
                try {
                    String[] parts = range.split(" ");
                    long start = Long.parseLong(parts[0]);
                    long leng = Long.parseLong(parts[1]);
                    String checkSum = parts[2];
                    rangX.add(new long[]{start, leng});

                    // kann später prüfen ob alles richtig war.
                    rangXChecks.put(start + "_" + leng, checkSum);
                } catch (Exception e) {
                    // ignore just do not send this
                }
            }
        }

        synchronized (this) {

            // wenn die datei das erste mal existiert
            if (test.exists()) {
                // wenn ich das erste mal schreibe, dann
                // attribute wegschreiben!
                test.putAttribute("check.sha512", shaSum);
                // attribute schreiben
                if (rangX.isEmpty()) {
                    // ein range für alles was nun kommt!
                    rangX.add(new long[]{0, size});
                }
            }
            notifyAll(); // sofort weiter machen
        }
    }
}
