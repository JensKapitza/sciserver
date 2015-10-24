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
package de.bluepair.sci.database;

import javax.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "users")
public class User implements Serializable {

    private static final long serialVersionUID = -6001638219743426011L;

    @Id
    @Column(nullable = false, name = "name")
    private String userName;

    private boolean admin;

    @Column(nullable = false, name = "password")
    private String password;

    private String owners;

    public String getOwners() {
        return owners;
    }

    public void setOwners(String owners) {
        this.owners = owners;
    }


    public synchronized void addOwner(String o) {
        if (o != null) {

            if (owners == null) {
                owners = o;
            } else {
                if (!owners.contains(o)) {
                    owners += "," + o;
                }
            }

        }
    }

    public synchronized void removeOwner(String o) {
        if (o != null) {
            // hier ist auch möglich ein split auf ,owner, zu machen und die arrays wieder zu verbinden
            // fall: 1 es gibt nur owner,2 es gibt nur zwei elemente also ein komma
            if (owners != null && owners.contains(o)) {
                String[] data = owners.split(",");
                setOwners(null);
                for (String x : data) {
                    if (!x.equals(o)) {
                        addOwner(x);
                    }
                }
            }

        }
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

}
