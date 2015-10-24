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
package main;

import de.bluepair.sci.client.PropertyLoader;
import de.bluepair.sci.master.EmbeddedFullStack;

public class Main {
    public static void main(String[] args) throws Exception {

        PropertyLoader.loadProperties(true, "server.properties", args);

        EmbeddedFullStack.main(args);
    }
}
