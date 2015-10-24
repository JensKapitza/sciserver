/**
 * This file is part of www.
 * <p>
 * www is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * www is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * <p>
 * You should have received a copy of the GNU Affero General Public License
 * along with www.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.bluepair.sci.www;

import de.bluepair.commons.jms.JMSClientAPI;

import javax.annotation.PostConstruct;
import javax.enterprise.context.SessionScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@SessionScoped
@Named("search")
public class Search implements Serializable {

    private static final long serialVersionUID = 1274669082948022628L;
    private final List<FileItem> files = new CopyOnWriteArrayList<>();
    private String input;
    private String path;

    @Inject
    private User user;
    @Inject
    private EditSession edit;
    private List<String[]> searchHist = new ArrayList<>();

    @PostConstruct
    public void firstUserAction() {
        search();
        files.clear();
    }

    public List<String[]> getSearchHist() {
        return searchHist;
    }

    public void setSearchHist(List<String[]> searchHist) {
        this.searchHist = searchHist;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public EditSession getEdit() {
        return edit;
    }

    public void setEdit(EditSession edit) {
        this.edit = edit;
    }

    public void messages(Map<String, Object> map) {

        String most = (String) map.get("tag.mostused");
        String last = (String) map.get("tag.lastused");
        String xfiles = (String) map.get("files.newest");
        user.updateTagsX(most, last, xfiles);

        if (map.get("files.ids") != null
                && !map.get("files.ids").toString().trim().isEmpty()) {
            List<String> files = Arrays.asList(map.get("files.ids").toString()
                    .split(","));


            if (!files.isEmpty()) {

                for (String id : files) {
                    if (!id.isEmpty()) {
                        FileItem item = new FileItem();
                        item.setId(id);
                        item.setPath((String) map.get("file." + id + ".path"));
                        item.setSha((String) map.get("file." + id + ".sha512"));
                        String tags = (String) map.get("file." + id + ".tags");

                        String sysTags = (String) map.get("file." + id + ".systags");


                        if (tags != null && !tags.isEmpty()) {
                            item.setTags(new ArrayList<String>(Arrays
                                    .asList(tags.split(","))));
                        }


                        if (sysTags != null && !sysTags.isEmpty()) {
                            if (item.getTags() == null) {
                                item.setTags(new ArrayList<>());
                            }

                            Arrays.asList(sysTags.split(",")).stream().map(k -> "(system) " + k).forEach(item.getTags()::add);
                            // kann man nicht löschen!
                        }


                        item.setSize((long) map.get("file." + id + ".size"));
                        item.setLinkPath((String) map.get("file." + id
                                + ".linkpath"));
                        item.setProvider((String) map.get("file." + id
                                + ".provider"));
                        item.setOwner((String) map.get("file." + id + ".owner"));
                        // gibts cfgs zum provider?


                        String baseURL = user.getConfigValue("config." + item.getProvider() + ".baseurl");
                        String sub = user.getConfigValue("config." + item.getProvider() + ".subpath");


                        String replaceWith = user.getConfigValue("config." + item.getProvider() + ".replacewith");
                        String replaceFrom = user.getConfigValue("config." + item.getProvider() + ".replacefrom");


                        if (map.containsKey("provider." + item.getProvider())) {
                            String prefix = (String) map.get("provider." + item.getProvider());
                            if (baseURL == null) {
                                baseURL = (String) map.get(prefix + ".baseurl");
                            }
                            if (sub == null) {
                                sub = (String) map.get(prefix + ".subpath");
                            }
                            if (baseURL == null) {
                                baseURL = "";
                            }
                            if (sub == null) {
                                sub = "";
                            }
                            // letzte vz lediglich
                            String data = baseURL + item.getPath().substring(sub.length(), item.getPath().length() - item.getFileName().length());
                            String dataFull = baseURL + item.getPath().substring(sub.length());
                            if (replaceFrom != null && replaceWith != null) {
                                data = data.replaceFirst(replaceFrom, replaceWith);
                                dataFull = dataFull.replaceFirst(replaceFrom, replaceWith);
                            }

                            item.setInternetShortcutFull(dataFull);
                            item.setInternetShortcut(data);

                        }

                        this.files.add(item);

                    }
                }
            }
        }
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public List<FileItem> getFiles() {
        return files;
    }

    public String search() {
        files.clear();
        edit.setItem(null);
        // alte daten löschen
        user.clearTagsX();
        HashMap<String, Object> map = new HashMap<>();
        map.put("type", "www");
        map.put("cmd", "files");
        ArrayList<String> hist = new ArrayList<>();

        if (user.isActive()) {
            map.put("user", user.getName());
        }
        if (input != null && !input.trim().isEmpty()) {
            map.put("tags", input);
            user.addTagsLastSession(input);
            hist.add(input);
        } else {
            hist.add("");
        }
        if (path != null && !path.trim().isEmpty()) {
            map.put("path", path);
            hist.add(path);
        } else {
            hist.add("");
        }

        // TODO filter aus dem jdk8 nutzen
        Iterator<String[]> it = searchHist.iterator();
        while (it.hasNext()) {
            String[] a = it.next();
            if (a[0].equals(hist.get(0)) && a[1].equals(hist.get(1))) {
                it.remove();
            }
        }
        // will use localhost per default!
        JMSClientAPI.doSend(map, this::messages, 5000);

        if (!hist.get(0).isEmpty() || !hist.get(1).isEmpty()) {
            searchHist.add(0, hist.toArray(new String[0]));
        }


        return "search";
    }

    public FileItem findID(String id) {
        Optional<FileItem> of = files.stream()
                .filter(f -> f.getId().equals(id)).findFirst();
        if (of.isPresent()) {
            return of.get();
        }
        return null;
    }

}
