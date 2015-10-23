/**
 * This file is part of bridge.
 *
 * bridge is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * bridge is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bridge.  If not, see <http://www.gnu.org/licenses/>.
 */
package main;

import de.bluepair.commons.jms.JMSClientAPI;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Bridge {

    private final Map<String, JMSClientAPI> masters = new ConcurrentHashMap<>();

    public void addMaster(String host,String masterKey,String ttl) {
        JMSClientAPI api = new JMSClientAPI(host, "bridge") {

            public void run() {
                super.run();
                masters.remove(getJMSHost());
            }

        };
        api.put("bridge.ttl",ttl);
        api.put(JMSClientAPI.SYSTEM_JMS_MASTERKEY,masterKey);
        api.addListener(this::bridgeMessages);
        api.start();
        masters.put(host, api);

    }

    public void bridgeMessages(JMSClientAPI jms, Map<String, Object> map) {

        if (map.get("jms_client_id").equals(jms.getID()) || map.get(jms.getID() + "_jms_client_id").equals(jms.getID())) {
            return;
        }
       
        int ttl = 1;
        try {
            ttl= Integer.parseInt(jms.get("bridge.ttl"));

        } catch (NumberFormatException nfe){
            ttl = 1;
            jms.put("bridge.ttl","1");
        }

        if (map.containsKey("bridge.ttl")) {
            try {
                ttl = (int) map.get("bridge.ttl") - 1;
            } catch (Exception e) {
                ttl = 0;
            }

        } else {
            map.put("bridge.ttl", ttl);

        }

        if (ttl <= 0) {
            return;
        }

        masters.values().stream().filter((c) -> (jms != c)).forEach((c) -> {
            c.send(map);
        });

    }

    public void join() {
        while (!masters.isEmpty()) {
            masters.values().forEach(e -> {
                try {
                    e.join();

                } catch (InterruptedException ex) {
                    Logger.getLogger(Bridge.class
                            .getName()).log(Level.SEVERE, null, ex);
                }
            });
        }
    }
}
