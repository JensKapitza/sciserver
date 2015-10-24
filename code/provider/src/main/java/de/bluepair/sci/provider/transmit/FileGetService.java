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
import java.net.*;
import java.util.Timer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FileGetService implements Runnable {

    public static final String FILE_GET_SERVICE_PORT = "de.bluepair.provider.fileget.port";
    private String masterKey;
    private String sendKey;
    private ServerSocket socket;
    private AtomicInteger onTalk = new AtomicInteger();
    private Timer timer;
    private Consumer<byte[]> resv;
    private Consumer<Runnable> execHelper;

    public FileGetService(String masterKey, String sendKey, Consumer<byte[]> resv, Timer timer, Consumer<Runnable> execHelper) {
        this.masterKey = masterKey;
        this.sendKey = sendKey;
        this.resv = resv;
        this.execHelper = execHelper;
        this.timer = timer;
    }

    public boolean isClosed() {
        return socket == null;
    }

    public void close() {
        if (socket != null && onTalk.get() <= 0) {
            try {
                socket.close();
            } catch (IOException ex) {
                Logger.getLogger(FileGetService.class.getName()).log(Level.SEVERE, null, ex);
            }
            socket = null;

            // wenn bislang nichts gekommen ist.
            // nun ist aber auch ende
            resv.accept(null);
        }
    }

    public int getPort() {
        return socket.getLocalPort();
    }

    public void openSocket() throws IOException {

        if (socket == null) {

            socket = new ServerSocket();
        }
        InetSocketAddress address;
        try {
            int port = Integer.parseInt(System.getProperty(FILE_GET_SERVICE_PORT, "0"));
            //bad if we do not use this
            socket.setReuseAddress(true);
            address = new InetSocketAddress(port);
        } catch (NumberFormatException nfe) {
            socket.setReuseAddress(false);
            address = null;
        }
        socket.bind(address, 50);
        // 10 sec timeout
        // socket.setSoTimeout(10000);

    }

    @Override
    public void run() {
        do {
            try {
                Socket client = socket.accept();
                // fork den client damit ich nicht zu lange warte!
                // verhindere timeout wenn ich bereits reden

                onTalk.incrementAndGet();

                FileGetConection conection = new FileGetConection(timer, masterKey, sendKey, client, onTalk, this::close, resv);

                if (execHelper == null) {
                    Thread get = new Thread(conection, "getfile_download");
                    get.start();
                } else {
                    execHelper.accept(conection);
                }

            } catch (SocketTimeoutException | SocketException ex) {
                // hier ist ende, keine anfrage in 10sec bekommen
            } catch (IOException ex) {
                Logger.getLogger(FileGetService.class.getName()).log(Level.SEVERE, null, ex);
                // ende bei fehler in der verbindung
                break;

            }
        } while (onTalk.get() > 0);

        close();

    }

    public String getMasterKey() {
        return masterKey;
    }
}
