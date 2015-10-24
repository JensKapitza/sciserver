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
package main;

import de.bluepair.sci.client.PropertyLoader;
import de.bluepair.sci.database.EmbeddedDatabase;
import de.bluepair.sci.database.Property;
import de.bluepair.sci.messages.ApplicationStart;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;

import javax.jms.JMSException;
import javax.persistence.EntityManager;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class DatabaseMain {

    public static void main(String[] args) throws InterruptedException, IOException, JMSException, Exception {

        PropertyLoader.loadProperties(true,"database.properties",args);

        Weld weld = new Weld();
        WeldContainer container = weld.initialize();

        // Database Starten
        EmbeddedDatabase database = new EmbeddedDatabase("embeddedDatabase");
        database.start();
        ApplicationStart start = new ApplicationStart(container);

        container.event().fire(start);

    }

}
