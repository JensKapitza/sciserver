/**
 * This file is part of database.
 *
 * database is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * database is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with database.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.bluepair.sci.messages;

import java.util.Map;

public class Message {
    private Map<String, Object> map;

    public Message(Map<String, Object> map) {
        this.map = map;
    }

    public Map<String, Object> getMap() {
        return map;
    }

    public String getString(String key) {
        return (String) map.get(key);
    }

    public int getInt(String key) {
        return (int) map.get(key);
    }

    public boolean getBoolean(String key) {
        return (boolean) map.get(key);
    }

    public boolean isEquals(String key, String vgl) {
        return vgl.equals(String.valueOf(map.get(key)));
    }
}
