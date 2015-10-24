/**
 * This file is part of master.
 * <p>
 * master is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * master is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * <p>
 * You should have received a copy of the GNU Affero General Public License
 * along with master.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.bluepair.sci.master;

import org.glassfish.embeddable.*;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EmbeddedJSF {

    private int port;
    private GlassFishRuntime glassfishRuntime;
    private GlassFish glassfish;
    private Deployer deployer;
    private CommandRunner commandRunner;

    public EmbeddedJSF(int port, String dataDir) throws GlassFishException {
        this.port = port;

        BootstrapProperties bootstrapProperties = new BootstrapProperties();
        bootstrapProperties.setInstallRoot(dataDir);
        glassfishRuntime
                = GlassFishRuntime.bootstrap(bootstrapProperties);
        GlassFishProperties glassfishProperties = new GlassFishProperties();

        File home = new File(dataDir);
        if (!home.exists()) {
            home.mkdirs();
        } else {
            // es gibt genau ein subdir. 
            if (home.list().length == 1) {
                glassfishProperties.setInstanceRoot(home.listFiles()[0].getAbsolutePath());
            } else {
                glassfishProperties.setProperty("org.glassfish.embeddable.autoDelete", "false");
                glassfishProperties.setProperty("glassfish.embedded.tmpdir", dataDir);
            }
        }

        glassfishProperties.setPort("http-listener", port);
        // SSL im moment nicht aktive also auch nicht anbieten! 
        //glassfishProperties.setPort("https-listener", port +101);
        glassfish = glassfishRuntime.newGlassFish(glassfishProperties);

    }

    public void deploy(File war, String name, String context) throws GlassFishException {
        String xname;
        if (context != null) {
            xname = deployer.deploy(war, "--name", name, "--contextroot", context);
        } else {
            xname = deployer.deploy(war, "--name", name);
        }
        if (!name.equals(xname)) {
            throw new GlassFishException(xname + " does not match " + name);
        }
    }

    public String runCommand(String command, String... args) {

        if (commandRunner == null) {
            return "server not running start server before running commands";
        }
        CommandResult result = commandRunner.run(command, args);
        if (result.getFailureCause() != null) {
            return "command: " + command + " excecution faild: " + result.getFailureCause().getMessage();
        }
        return result.getOutput();
    }

    public void start() throws GlassFishException {
        if (deployer != null || commandRunner != null) {
            throw new GlassFishException("server may be running");
        }
        glassfish.start();
        commandRunner = glassfish.getCommandRunner();
        deployer = glassfish.getDeployer();
    }

    public void stop() {
        try {
            glassfish.stop();
        } catch (GlassFishException ex) {
            Logger.getLogger(EmbeddedJSF.class.getName()).log(Level.SEVERE, null, ex);
        }
        try {
            glassfish.dispose();
        } catch (GlassFishException ex) {
            Logger.getLogger(EmbeddedJSF.class.getName()).log(Level.SEVERE, null, ex);
        }
        try {
            glassfishRuntime.shutdown();
        } catch (GlassFishException ex) {
            Logger.getLogger(EmbeddedJSF.class.getName()).log(Level.SEVERE, null, ex);
        }
        commandRunner = null;
        deployer = null;
    }

    public String getPort() {
        return String.valueOf(port);
    }


}
