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
package de.bluepair.commons.file;

import de.bluepair.sci.client.PropertyLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * Created by kapitza on 20.08.15.
 */
public class JarFileUpdater {

    private JarFileUpdater() {
        // do nothing
    }


    public static void restart(Class<?> clazz, Runnable andThen) throws URISyntaxException, IOException {
        File jar = new File(clazz.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
        if (jar.isFile() && jar.getAbsolutePath().endsWith(".jar")) {
            // this is bad because we do not know where java is
            // TODO fix java detection find caller VM (i was executed from one)
            ProcessBuilder builder = new ProcessBuilder("java", "-jar", jar.getAbsolutePath());
            Process p = builder.start();
            if (p.isAlive() && andThen != null) {
                andThen.run();
            }
        } else {
            throw new IOException("this is not a jar file, can't restart");
        }
    }

    public static void checkUpdateOrStart(Class<?> clazz, Consumer<String[]> bootstrap, String[] args) throws Exception {

        // fork den gleichen prozess nochmal und dann
        // schalte mich selbst ab
        File jar = new File(clazz.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());

        File use = jar;
        boolean restart = false;
        if (jar.isFile()) {

            File[] items = jar.getParentFile().listFiles((File dir, String name) -> name != null && name.endsWith("jar"));

            if (items.length > 1) {
                // da sind updates also starte ein update
                String baseName = jar.getName().split("update")[0];
                System.out.println(baseName);

                int neuste = 0;
                for (File f : items) {
                    String[] i = f.getName().split("update");
                    if (i.length == 2) {
                        String number = i[1].split("\\.")[0];
                        try {
                            int z = Integer.parseInt(number);
                            if (z > neuste) {
                                use = f;
                                neuste = z;
                            }
                        } catch (NumberFormatException e) {
                            e.printStackTrace();
                        }
                    }
                }

                restart = (use != jar);

            }

            if (jar.getName().contains("update")) {
                use = jar.toPath().getParent().resolve(jar.getName().split("update")[0] + ".jar").toFile();
                if (use.exists()) {
                    Files.delete(use.toPath());
                }
                Files.copy(jar.toPath(), use.toPath());
                restart = true;
            }

            if (restart) {
                // rename
                for (File f : items) {
                    if (f.exists() && !f.equals(use)) {
                        Files.delete(f.toPath());
                    }
                }
            }

        }


        if (!restart) {
            bootstrap.accept(args);
        } else {
            restart(clazz, null);
        }

    }


    public static void publishProperties(String defaultName, String[] args) {
        String fileName = defaultName + ".properties";
        PropertyLoader.loadProperties(true,fileName,args);
    }

}
