/**
 * This file is part of database.
 *
 * database is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * database is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with database.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.bluepair.sci.database;

import org.apache.derby.drda.NetworkServerControl;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EmbeddedDatabase {

    private NetworkServerControl control;

    public EmbeddedDatabase(String dataDir) throws Exception {

        File derbySystemDirectory = new File(dataDir);
        if (!derbySystemDirectory.exists()) {
            derbySystemDirectory.mkdirs();
        }

        System.setProperty("derby.system.home", derbySystemDirectory.getPath());

        control = new NetworkServerControl();

    }

    public void start() throws Exception {
        control.start(null);
    }

    public void stop() {

        try {
            control.shutdown();
        } catch (Exception ex) {
            Logger.getLogger(EmbeddedDatabase.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public String getDataDir() {
        return System.getProperty("derby.system.home");
    }

}
