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
import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;
import java.util.*;

@SessionScoped
@Named
public class Admin implements Serializable {
    @Inject
    private User user;
    private Map<String, String> sessionCfg = new HashMap<>();
    private String[] configs;
    private String input;

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Map<String, String> getSessionCfg() {
        return sessionCfg;
    }

    public void setSessionCfg(Map<String, String> sessionCfg) {
        this.sessionCfg = sessionCfg;
    }

    public String[] getConfigs() {
        return configs;
    }

    public void setConfigs(String[] configs) {
        this.configs = configs;
    }

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public String update() {
        if (user.isAdmin()) {
            Map<String, Object> map = new HashMap<>();
            map.put("topic", "master");
            map.put("cmd", "update");
            map.put("from", user.getName());

            // auth evtl. um database drop zu machen.

            JMSClientAPI.doSend(map);
        }

        return "admin";
    }


    public List<String> properties() {
        List<String> li = new ArrayList<>();

        System.getProperties().forEach((k, v) -> {
            li.add("p." + k + "=" + v);
        });

        System.getenv().forEach((k, v) -> {
            li.add("e." + k + "=" + v);
        });

        return li;
    }


    public String addConfigLine() {
        if (input != null && !input.isEmpty()) {
            String[] data = input.split("=");
            if (data.length == 2) {

                Map<String, Object> message = User.createConfigMsg(user, "config." + data[0], data[1], true, false);

                //clear old data
                user.getConfig().clear();
                getSessionCfg().clear();

                JMSClientAPI.doSend(message, user::onConfigAdd, 5000);
            }
        }

        input = "";
        return null;
    }

    public String deleteConfig() {
        if (configs != null) {
            StringBuilder data = new StringBuilder();

            Map<String, Object> map = User.createConfigMsg(user, null, null, true, true);
            for (String cx : configs) {
                String v = sessionCfg.remove(cx);
                data.append(v);
                map.put("config." + cx, v);
            }


            char[] chars = data.toString().toCharArray();
            Arrays.sort(chars);
            String sorted = new String(chars);

            map.put("hash", SHAUtils.sha512(user.getPasswordHash() + sorted));

            JMSClientAPI.doSend(map);


        }
        return null;
    }


}
