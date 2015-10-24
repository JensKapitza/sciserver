/**
 * This file is part of www.
 *
 * www is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * www is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with www.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.bluepair.sci.www;

import de.bluepair.commons.jms.JMSClientAPI;
import de.bluepair.sci.client.SHAUtils;

import javax.enterprise.context.SessionScoped;
import javax.faces.context.FacesContext;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;
import java.util.*;

@SessionScoped
@Named("user")
public class User implements Serializable {


    private static final long serialVersionUID = 4464157613332968018L;

    private String name = "";
    private String password;
    private boolean loginOK;
    private boolean admin;

    @Inject
    private Admin adminSession;
    private List<String> tagsMost = new ArrayList<>();
    private List<String> tagsLastSession = new ArrayList<>();
    private List<String> tagsLast = new ArrayList<>();
    private List<String> newestFiles = new ArrayList<>();
    private String input;
    private List<String> owner = new ArrayList<>();
    private Map<String, String> config = new HashMap<>();

    public static Map<String, Object> createConfigMsg(User user, String key, String value, boolean admin, boolean delete) {

        Map<String, Object> message = new HashMap<>();
        message.put("type", "www");
        message.put("cmd", "config");
        message.put("user", user.getName());
        String sorted = "";
        if (value != null) {
            message.put(key, value);
            char[] chars = value.toCharArray();
            Arrays.sort(chars);
            sorted=    new String(chars);
        }

        if (admin) {
            message.put("admin", "true");
        }
        if (delete) {
            message.put("delete", "true");
        }




        message.put("hash", SHAUtils.sha512(user.getPasswordHash() + sorted)); // ueber die nachrichtendaten

        return message;
    }

    public Admin getAdminSession() {
        return adminSession;
    }

    public void setAdminSession(Admin adminSession) {
        this.adminSession = adminSession;
    }

    public List<String> getTagsLastSession() {
        return tagsLastSession;
    }

    public void setTagsLastSession(List<String> tagsLastSession) {
        this.tagsLastSession = tagsLastSession;
    }

    public List<String> getNewestFiles() {
        return newestFiles;
    }

    public List<String> getTagsLast() {
        return tagsLast;
    }

    public List<String> getTagsMost() {
        return tagsMost;
    }

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public String addOwner() {
        if (input != null && !input.isEmpty() && !owner.contains(input)) {
            owner.add(input);
            EditSession.sendOwner(input, name, true);
        }

        return null;
    }

    public boolean isAdmin() {
        return admin && isActive();
    }

    public boolean isActive() {
        return loginOK;
    }

    public String logout() {
        loginOK = false;
        admin = false;
        password = "";
        tagsLastSession.clear();


        // kann man sich drüber streiten!
        // ist aber besser so als vergessene delete aufrufe!
        FacesContext.getCurrentInstance().getExternalContext()
                .invalidateSession();


        return "home";
    }

    private void onMessage(Map<String, Object> map) {
        loginOK = name.equals(String.valueOf(map.get("user")))
                && "loginOK".equals(String.valueOf(map.get("login")));
        admin = loginOK && "true".equals(String.valueOf(map.get("admin")));
        // put config

        onConfigAdd(map);

        String most = (String) map.get("tag.mostused");
        String last = (String) map.get("tag.lastused");

        String xfiles = (String) map.get("files.newest");
        updateTagsX(most, last, xfiles);
        String owners = (String) map.get("owner");
        if (owners != null && !owners.isEmpty()) {
            owner.addAll(new ArrayList<String>(Arrays.asList(owners.split(","))));
        }
    }

    public void clearTagsX() {

        // es macht eigentlich wenig sinn
        // ein clear zu machen, da mehrere antworten kommen können!

        // daher sparsam hier aufrufen
        tagsMost.clear();
        tagsLast.clear();
        newestFiles.clear();


    }

    public void updateTagsX(String most, String last, String files) {

        if (most != null && !most.isEmpty()) {
            tagsMost.addAll(Arrays.asList(most.split(",")));
        }
        if (last != null && !last.isEmpty()) {
            tagsLast.addAll(Arrays.asList(last.split(",")));
        }

        if (files != null && !files.isEmpty()) {
            newestFiles.addAll(Arrays.asList(files.split(",")));

        }

    }

    public void onConfigAdd(Map<String, Object> map) {
        if (map.keySet().stream().filter(s -> s.startsWith("config.")).count() > 0) {

            map.entrySet().stream().filter(e -> e.getKey().startsWith("config.")).forEach(e -> {
                config.put(e.getKey().substring("config.".length()), String.valueOf(e.getValue()));
            });
        }

        // nun admin filter
        if (isAdmin()) {
            if (map.keySet().stream().filter(s -> s.startsWith("admin.")).count() > 0) {

                map.entrySet().stream().filter(e -> e.getKey().startsWith("admin.")).forEach(e -> {
                    adminSession.getSessionCfg().put(e.getKey().substring("admin.".length()), String.valueOf(e.getValue()));
                });
            }
        }

    }

    public String addConfigLine() {
        if (input != null && !input.isEmpty()) {
            String[] data = input.split("=");
            if (data.length == 2) {

                Map<String, Object> message = createConfigMsg(this, "config." + data[0], data[1], false, false);


                //clear old data
                config.clear();
                adminSession.getSessionCfg().clear();

                JMSClientAPI.doSend(message, this::onConfigAdd, 1000);


            }
        }

        input = "";
        return null;

    }

    public Map<String, String> getConfig() {
        return config;
    }

    public void setConfig(Map<String, String> config) {
        this.config = config;
    }

    public String getConfigValue(String key) {
        return config.get(key);
    }

    public String login() {

        Map<String, Object> message = new HashMap<>();
        message.put("type", "www");
        message.put("cmd", "login");
        message.put("user", name);
        message.put("create", "true");
        message.put("password", SHAUtils.sha512(password));

        // clear old data
        owner.clear();
        config.clear();
        adminSession.getSessionCfg().clear();
        clearTagsX();

        JMSClientAPI.doSend(message, this::onMessage);
        return null; // "home"
        // with null stay on site
    }

    public List<String> getOwner() {
        return owner;
    }

    public void setOwner(List<String> owner) {
        this.owner = owner;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPasswordHash() {
        return SHAUtils.sha512(password);
    }

    public void removeConfig(String key) {
        String old = config.remove(key);

        Map<String, Object> message = createConfigMsg(this, "config." + key, old, false, true);

        JMSClientAPI.doSend(message, this::onConfigAdd, 1000);
    }

    public void addTagsLastSession(String input) {
        if (!tagsLastSession.contains(input)) {
            tagsLastSession.add(0, input);
        }
    }
}
