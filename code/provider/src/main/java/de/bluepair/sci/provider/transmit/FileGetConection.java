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
package de.bluepair.sci.provider.transmit;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

class FileGetConection implements Runnable {

    private Socket socket;

    private AtomicInteger count;

    private Runnable closeCall;

    private String masterKey;

    private String sendKey;
    private Consumer<byte[]> resv;
    private Timer timer;

    public FileGetConection(Timer timer, String masterKey, String sendKey, Socket socket, AtomicInteger count, Runnable closeCall, Consumer<byte[]> resv) {
        this.socket = socket;
        this.timer = timer;
        this.count = count;
        this.masterKey = masterKey;
        this.sendKey = sendKey;
        this.resv = resv;
        this.closeCall = closeCall;
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

        TimerTask task = getTask(socket);

        try {

            // eingehender Client hat folgendes Verhalten.
            // 1. sendet master
            // maximal 1MB
            InputStream in = socket.getInputStream();
            byte[] vglIn = masterKey.getBytes("utf8");
            byte[] inKey = new byte[vglIn.length * 2];
            timer.schedule(task, 10000);
            int lastRead = in.read(inKey);
            if (vglIn.length != lastRead) {
                // ende falsche daten
                return;
            }

            byte[] copy = new byte[lastRead];
            System.arraycopy(inKey, 0, copy, 0, lastRead);
            inKey = copy;

            if (!task.cancel() || !Arrays.equals(inKey, vglIn)) {
                // keys are wrong
                return;

            }

            // 2. ich sende dann sendKey
            OutputStream out = socket.getOutputStream();
            byte[] outKey = sendKey.getBytes("utf8");

            out.write(outKey);
            out.flush();
            try {
                // nun sollte wieder was gesendet werden!
                // 3. alle daten die ich bekomme durchgeben.
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Logger.getLogger(FileGetConection.class.getName()).log(Level.SEVERE, null, ex);
            }
            // warte ein wenig um anderem zeit zur prüfung zu geben.
            byte[] buffer = new byte[8192];
            do {
                try {
                    task = getTask(socket);
                    timer.schedule(task, 10000);
                    lastRead = in.read(buffer);
                    if (!task.cancel()) {
                        lastRead = -1;
                    }
                    if (lastRead != -1) {
                        byte[] copyx = new byte[lastRead];
                        System.arraycopy(buffer, 0, copyx, 0, lastRead);

                        resv.accept(copyx);

                    }
                } catch (IOException ioe) {
                    // ende erkannt!
                    // das kann mehrere gründe haben.
                    lastRead = -1; // ende von read
                }
            } while (lastRead != -1);

        } catch (IOException ex) {
            Logger.getLogger(FileGetConection.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            count.decrementAndGet();
            closeCall.run();
            // marker 4finish
            resv.accept(null);

        }

    }

}
