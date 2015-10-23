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

import java.io.File;
import java.util.Arrays;

public class OSPrinter {

    public static String getSystemInfo() {
        String ident = "";
        ident += System.getProperty("java.version") + "_";
        ident += System.getProperty("os.arch") + "_";
        ident += System.getProperty("os.name") + "_";
        ident += System.getProperty("os.version");
        return ident;
    }

    public static void main(String[] args) {
        System.out.println(System.getProperty("os.name"));

        File f = new File(".");
        System.out.println(Arrays.asList(f.list()));
    }

}
