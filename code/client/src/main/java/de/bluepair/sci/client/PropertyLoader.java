/**
 * This file is part of client.
 *
 * client is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * client is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with client.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.bluepair.sci.client;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Optional;
import java.util.Properties;

/**
 * Created by kapitza on 19.09.15.
 */
public class PropertyLoader {


    public static Optional<Properties> loadProperties(boolean populateToSystem, String defaultFile, String... args) {

        Properties props = new Properties();
        String fname = defaultFile;
        if (args.length > 0) {
            fname = args[0];
        }

        try (FileInputStream fin = new FileInputStream(fname)) {
            props.load(fin);
            // hope there are defaults
        } catch (FileNotFoundException fnf) {
            return Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }


        if (populateToSystem) {

            props.entrySet().forEach(e -> {
                        System.setProperty(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
                    }
            );
        }

        return Optional.of(props);
    }

}
