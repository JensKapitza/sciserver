/**
 * This file is part of provider.
 * <p>
 * provider is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * provider is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * <p>
 * You should have received a copy of the GNU Affero General Public License
 * along with provider.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.bluepair.sci.provider.transmit;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FileSend implements Runnable {

    private String masterKey;
    private String sendKey;

    private InputStream toSend;

    private String[] host;
    private int port;

    public FileSend(String masterKey, String sendKey, InputStream toSend, String[] host, int port) {
        this.masterKey = masterKey;
        this.sendKey = sendKey;
        this.toSend = toSend;
        this.host = host;
        this.port = port;
    }

    public static FileSend filePath(Path file, String masterKey, String sendKey, String[] host, int port) throws IOException {
        return new FileSend(masterKey, sendKey, Files.newInputStream(file), host, port);
    }

    public TimerTask getTask(Socket connection) {
        return new TimerTask() {

            @Override
            public void run() {
                try {
                    connection.close();
                } catch (IOException ex) {
                    Logger.getLogger(FileGetConection.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        };
    }

    @Override
    public void run() {
        Timer    timer = new Timer();
        // fire and forget // need just one connection

        for (String hostx : host) {
            try (final Socket connection = new Socket(hostx, port);) {


                // los gehts,
                TimerTask task = getTask(connection);
                connection.setSoTimeout(10000);

                try (InputStream in = connection.getInputStream();
                     OutputStream out = connection.getOutputStream();) {

                    // 1. ich sende masterkey
                    out.write(masterKey.getBytes("utf8"));

                    // 2. sendkey prüfen
                    byte[] sendK = sendKey.getBytes("utf8");
                    byte[] inKey = new byte[sendK.length * 2];
                    timer.schedule(task, 10000);
                    int lastRead = in.read(inKey);
                    if (sendK.length != lastRead) {
                        return; // falscher key
                    }

                    byte[] copy = new byte[lastRead];
                    System.arraycopy(inKey, 0, copy, 0, lastRead);
                    inKey = copy;

                    if (!task.cancel() || !Arrays.equals(sendK, inKey)) {
                        return; // oder falsche daten
                        // oder zu langsam
                    }
                    // nun alles richtig,
                    // sende daten.
                    // senden durch Files.copy

                    byte[] buffer = new byte[8192];
                    do {
                        // reuse inkey buffer
                        lastRead = toSend.read(buffer);
                        if (lastRead > 0) {
                            out.write(buffer, 0, lastRead);
                        }
                        out.flush(); // damit der timer nicht gekillt wird!
                    } while (lastRead > 0);
                }
                // alles fertig kill die verbindung!
                // ich bin zu schnell!
                // wenn dass ok war brauch ich keinen weiteren verbindungsversuch!
                break;
            } catch (IOException ex) {
                Logger.getLogger(FileSend.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        if (timer != null) {
            timer.cancel();
        }
        // ist aber nicht so wild
        try {
            toSend.close();
        } catch (IOException ex) {
            Logger.getLogger(FileSend.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

}
