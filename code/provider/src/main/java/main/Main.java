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
package main;

import de.bluepair.commons.file.JarFileUpdater;
import de.bluepair.sci.provider.FileProvider;

public class Main {

    public static void main(String[] args) throws Exception {
        JarFileUpdater.publishProperties("provider", args);
        JarFileUpdater.checkUpdateOrStart(Main.class, Main::boot, args);
    }


    public static void boot(String[] args) {
        String firstDir = ".";
        if (args.length >= 2) {
            firstDir = args[1];
        }


        FileProvider p = new FileProvider(firstDir);

        for (int i=2; i <args.length;i++){
            p.addWatchDir(args[i]);
        }

        p.start();
        p.join();
    }
}
