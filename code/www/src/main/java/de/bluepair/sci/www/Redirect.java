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

import javax.annotation.PostConstruct;
import javax.enterprise.context.RequestScoped;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.util.Map;

@RequestScoped
@Named
public class Redirect {

    @Inject
    private Search search;
    private String url;



    public Search getSearch() {
        return search;
    }

    public void setSearch(Search search) {
        this.search = search;
    }

    @PostConstruct
    public void init() {
        Map<String, String> data = FacesContext.getCurrentInstance().getExternalContext()
                .getRequestParameterMap();

        url = data.get("url");

        if (url != null) {
            String path = data.get("path");
            String tag = data.get("tag");
            search.setPath(path);
            search.setInput(tag);
            // vorladen der session
            search.search();

            action();
        }
    }

    public String getUrl() {

        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getOpenURL(String path) {
        ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
        String userAgent = externalContext.getRequestHeaderMap().get("User-Agent");


        String base = "enterDir.sh";
        if (userAgent.toLowerCase().indexOf("windows") >= 0) {
            base = "enterDir.vbs";
        }


        return base + "?path=" + path;
    }

    public void action() {
        try {
            FacesContext.getCurrentInstance().getExternalContext()
                    .redirect(getUrl());
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
