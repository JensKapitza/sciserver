/**
 * This file is part of bridge.
 * <p>
 * bridge is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * bridge is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * <p>
 * You should have received a copy of the GNU Affero General Public License
 * along with bridge.  If not, see <http://www.gnu.org/licenses/>.
 */
package main;

import de.bluepair.sci.client.PropertyLoader;

import java.util.Optional;
import java.util.Properties;

public class Main {

    public static void main(String[] args) throws Exception {

        Optional<Properties> p = PropertyLoader.loadProperties(true, "bridge.properties", args);


        Bridge bridge = new Bridge();

        if (p.isPresent()) {
            p.get().entrySet().forEach(e -> {
                if (String.valueOf(e.getKey()).startsWith("de.bluepair.jms.host")) {
                    String prefix = e.getKey().toString().substring("de.bluepair.jms.host.".length());
                    bridge.addMaster(String.valueOf(e.getValue()), String.valueOf(p.get().getProperty("de.bluepair.jms.masterkey." + prefix)), String.valueOf(p.get().getProperty("de.bluepair.jms.ttl." + prefix, "1")));
                }
            });
        }

        bridge.join();
    }
}
