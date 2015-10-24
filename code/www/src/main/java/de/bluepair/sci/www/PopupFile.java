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

import javax.enterprise.context.RequestScoped;
import javax.faces.component.UIComponent;
import javax.faces.component.UIParameter;
import javax.faces.component.html.HtmlCommandLink;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequestScoped
@Named("popup")
public class PopupFile {

    @Inject
    private User user;
    @Inject
    private EditSession edit;
    @Inject
    private Search search;
    private FileItem item;
    private HtmlCommandLink link;
    private HtmlCommandLink view;

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public FileItem getFileItem() {
        return item;
    }

    public FileItem getItem() {
        return item;
    }

    public void setItem(FileItem item) {
        this.item = item;
    }

    public Search getSearch() {
        return search;
    }

    public void setSearch(Search search) {
        this.search = search;
    }

    public HtmlCommandLink getView() {
        return view;
    }

    public void setView(HtmlCommandLink view) {
        this.view = view;
    }

    public HtmlCommandLink getLink() {
        return link;
    }

    public void setLink(HtmlCommandLink link) {
        this.link = link;
    }




    public String redirect() {

        List<UIComponent> children = link.getChildren();
        Map<String, String> parameters = getParameters(children);
        if (parameters.containsKey("id")) {
            item = search.findID(parameters.get("id"));
        }
        // edit Session setzen
        edit.setItem(getFileItem());
        return null;
    }

    public String redirectView() {

        List<UIComponent> children = view.getChildren();
        Map<String, String> parameters = getParameters(children);
        if (parameters.containsKey("id")) {
            item = search.findID(parameters.get("id"));
        }

        edit.setItem(null);
        return null;
    }

    private Map<String, String> getParameters(final List<UIComponent> components) {
        Map<String, String> parameters = null;

        if (components != null) {
            parameters = new HashMap<>(components.size());

            for (UIComponent component : components) {
                if (component instanceof UIParameter) {
                    final UIParameter parameter = (UIParameter) component;
                    parameters.put(parameter.getName(),
                            (String) parameter.getValue());
                }
            }
        }
        return parameters;
    }

}
