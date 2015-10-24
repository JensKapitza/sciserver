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

import org.jboss.weld.environment.se.WeldContainer;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

public class ApplicationStart {

    private EntityManagerFactory factory;
    private WeldContainer container;

    public ApplicationStart(WeldContainer container) {
        this.container = container;
        factory = Persistence.createEntityManagerFactory("sci");

    }

    public WeldContainer getContainer() {
        return container;
    }

    public EntityManagerFactory getFactory() {
        return factory;
    }

    public EntityManager createEntityManager(){
        return getFactory().createEntityManager();
    }


    public void fire(Object ev){
        getContainer().event().fire(ev);
    }
}
