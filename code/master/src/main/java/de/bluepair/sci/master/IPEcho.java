/**
 * This file is part of master.
 *
 * master is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * master is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with master.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.bluepair.sci.master;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class IPEcho extends Thread {

    private int port;

    private AtomicBoolean stop = new AtomicBoolean();

    public IPEcho(int port) {
        this.port = port;
    }

    public void stopEcho() {
        stop.set(true);
    }

    @Override
    public void run() {
        try (ServerSocket socket = new ServerSocket(port, 50);) {

            do {
                Socket client = socket.accept();
                String ip = client.getInetAddress().getHostAddress();
                try (OutputStream out = client.getOutputStream()) {
                    out.write(ip.getBytes("utf8"));
                    out.flush();
                }
            } while (!stop.get());

        } catch (IOException ex) {
            Logger.getLogger(IPEcho.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

}
