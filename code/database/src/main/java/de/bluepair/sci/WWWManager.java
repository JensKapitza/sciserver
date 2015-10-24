/**
 * This file is part of database.
 * <p>
 * database is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * database is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * <p>
 * You should have received a copy of the GNU Affero General Public License
 * along with database.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.bluepair.sci;

import de.bluepair.commons.jms.JMSClientAPI;
import de.bluepair.sci.client.SHAUtils;
import de.bluepair.sci.database.File;
import de.bluepair.sci.database.Property;
import de.bluepair.sci.database.Tag;
import de.bluepair.sci.database.User;
import de.bluepair.sci.messages.ApplicationStart;
import de.bluepair.sci.messages.WWWMessage;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApplicationScoped
public class WWWManager {

    private EntityManager manager;


    public static String getTagListAsString(List<Tag> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        return tags.stream().map(t -> t.getTagName())
                .collect(java.util.stream.Collectors.joining(","));
    }

    public static void addUserConfigToMsg(Map<String, Object> answer, String xuser, boolean isAdmin, TypedQuery<Property> propertyTypedQuery) {

        if (isAdmin) {
            propertyTypedQuery.setParameter("key", "%config%");
        } else {
            propertyTypedQuery.setParameter("key", "user.config." + xuser + "%");
        }

        List<Property> itemsc = propertyTypedQuery.getResultList();

        // add user cfg
        itemsc.stream().filter(p -> p.getKey().startsWith("user.config." + xuser)).forEach(p -> answer.put("config" + p.getKey().substring(("user.config." + xuser).length()), p.getValue()));


        // wenn ich admin bin alle anderen werte
        itemsc.stream().filter(p -> !p.getKey().startsWith("user.config." + xuser)).forEach(p -> answer.put("admin." + p.getKey(), p.getValue()));


    }

    private static void addFileToHashMap(File f, Map<String, Object> answerFile, TypedQuery<Property> propertyTypedQuery) {


        answerFile.put("file." + f.getId() + ".provider",
                f.getProvider());
        answerFile.put("file." + f.getId() + ".path",
                f.getFilePath());
        if (f.isSymbolicLink()) {
            answerFile.put("file." + f.getId() + ".linkpath",
                    f.getLinkPath());
        }
        answerFile.put("file." + f.getId() + ".size",
                f.getFileSize());
        answerFile.put("file." + f.getId() + ".owner",
                f.getFileOwner());
        answerFile.put("file." + f.getId() + ".sha512", f.getSha());
        String xtags = getTagListAsString(f.getTagList());

        String sysTags = getTagListAsString(f.getSysTagList());

        if (xtags != null) {
            answerFile.put("file." + f.getId() + ".tags", xtags);
        }
        if (sysTags != null) {
            answerFile.put("file." + f.getId() + ".systags", sysTags);
        }


        String old = (String) answerFile.put("files.ids",
                String.valueOf(f.getId()));
        if (old != null) {
            answerFile.put("files.ids",
                    old + "," + String.valueOf(f.getId()));
        }


        // properties eines provider einstellen
        // evtl. systemabhängig
        String provider = f.getProvider();
        if (!answerFile.containsKey("provider." + provider)) {
            String prefix = "config." + provider;
            answerFile.put("provider." + provider, prefix);
            propertyTypedQuery.setParameter("key", prefix + "%");
            List<Property> items = propertyTypedQuery.getResultList();
            items.forEach(p -> answerFile.put(p.getKey(), p.getValue()));
        }


    }

    public void addTagsStat(Map<String, Object> answer, TypedQuery<Property> propertyTypedQuery, List<String> owners) {


        propertyTypedQuery.setParameter("key", "tag.lastused");
        Property lastUsed = propertyTypedQuery.getSingleResult();


        propertyTypedQuery.setParameter("key", "tag.count.%");

        Comparator<Property> sortX = (e1, e2) -> Long.compare(Long.parseLong(e1.getValue()), Long.parseLong(e2.getValue()));


        List<Property> items = propertyTypedQuery.getResultList().stream().sorted(sortX).limit(10).collect(Collectors.toList());
        answer.put("tag.mostused", items.stream().map(Property::getKey).map(s -> s.substring("tag.count.".length())).collect(Collectors.joining(",")));

        if (lastUsed != null) {
            answer.put("tag.lastused", lastUsed.getValue());
        }

        // test SIZE(f.tagList) >= 1 AND
        TypedQuery<File> q = manager.createQuery("SELECT DISTINCT f FROM File f LEFT JOIN f.tagList t WHERE t.tagName IS NULL AND f.fileOwner = :owner ", File.class);

        StringBuilder builder = new StringBuilder();
        owners.forEach(o -> {
            q.setParameter("owner", o);
            String line = q.getResultList().stream().filter(f -> f.getTagList().isEmpty()).map(File::getFilePath).collect(Collectors.joining(","));
            if (line != null && line.length() > 0) {
                if (builder.length() > 0) {
                    builder.append(",");
                }
                builder.append(line);
            }
        });

        if (builder.length() > 0) {
            answer.put("files.newest", builder.toString());
        }

    }

    public void main(@Observes ApplicationStart start) {
        manager = start.getFactory().createEntityManager();
    }

    public void onMessage(@Observes WWWMessage msg) {
        // nun kann ich www anfrage abarbeiten.
        EntityTransaction tx = manager.getTransaction();
        if (tx.isActive()) {
            tx.rollback();
        }
        manager.clear();

        tx.begin();
        TypedQuery<User> userQuery = manager.createQuery(
                "SELECT u FROM User u WHERE u.userName = :name",
                User.class);


        TypedQuery<Property> propertyTypedQuery = manager.createQuery("SELECT p FROM Property p WHERE p.key LIKE :key", Property.class);
        List<User> users = null;
        String cmd = msg.getString("cmd");


        // defaults for most message commands

        final String xuser = msg.getString("user");
        final boolean isDelete = msg.isEquals("delete", "true");

        if (xuser != null) {
            userQuery.setParameter("name", xuser);
            users = userQuery.getResultList();
        }

        switch (cmd) {

            case "owner":
                // user und pw einloggen!
                String xpass = msg.getString("owner");

                for (User u : users) {
                    if (isDelete) {
                        u.removeOwner(xpass);
                    } else {
                        u.addOwner(xpass);
                    }
                    manager.merge(u);
                }
                break;

            case "config":
                // user und pw einloggen!

                String hash = msg.getString("hash");

                boolean isAdminReq = msg.isEquals("admin", "true");
                // sicherheit ist nicht gegeben
                // AES nutzen?

                if (!users.isEmpty()) {
                    User u = users.get(0);
                    List<Property> items = new ArrayList<>();
                    StringBuilder data = new StringBuilder();

                    msg.getMap().forEach((k, v) -> {

                        if (k.startsWith("config")) {
                            data.append(v);
                            Property p = new Property();

                            p.setValue(String.valueOf(v));
                            if (u.isAdmin() && isAdminReq) {
                                // config ist sonst doppelt drin
                                String keyX = k.substring("config.".length());
                                if (keyX.startsWith("config.admin")) {
                                    // wert dem user entsprechend admin geben.
                                    if (isDelete) {
                                        userQuery.setParameter("name", keyX.substring("config.admin.".length()));
                                        // hier ist der key richtig!
                                    } else {
                                        userQuery.setParameter("name", String.valueOf(v));
                                        //erlaubt ist config.admin.*=USER
                                        keyX = "config.admin." + v;
                                    }


                                    List<User> ux = userQuery.getResultList();
                                    if (!ux.isEmpty()) {

                                        User xu = ux.get(0);
                                        xu.setAdmin(!isDelete);
                                        // mit dieser zeile geht nix mehr weld error!
                                        //        xu.setAdmin(msg.isEquals("delete", "true"));
                                        //reset value
                                        p.setValue(String.valueOf(xu.isAdmin()));
                                        manager.merge(xu);
                                    }

                                }
                                p.setKey(keyX);
                            } else {
                                p.setKey("user.config." + xuser + k.substring("config".length()));
                            }
                            items.add(p);
                        }

                    });


                    char[] chars = data.toString().toCharArray();
                    Arrays.sort(chars);
                    String sorted = new String(chars);


                    if (hash != null && SHAUtils.sha512(u.getPassword() + sorted).equals(hash)) {
                        if (isDelete) {
                            items.stream().map(Property::getKey).map(k -> manager.find(Property.class, k)).forEach(manager::remove);
                        } else {
                            items.forEach(manager::merge);
                        }

                    }


                    Map<String, Object> answer = new HashMap<>();
                    addUserConfigToMsg(answer, xuser, u != null && u.isAdmin(), propertyTypedQuery);

                    answer.put("topic", "master");

                    JMSClientAPI.doSend(answer);


                }


                break;

            case "login":
                // user und pw einloggen!

                xpass = msg.getString("password");
                ArrayList<String> owners = new ArrayList<>();
                owners.add(xuser);


                Map<String, Object> answer = new HashMap<>();

                boolean loginOK = !users.isEmpty()
                        && users.get(0).getPassword().equals(xpass);

                User u = null;
                if (users.isEmpty() && msg.isEquals("create", "true")) {
                    u = new User();

                    u.setPassword(xpass);
                    u.setUserName(xuser);

                    // user.config.admin.email.push
                    Property emailPush = new Property();
                    emailPush.setValue("true");
                    emailPush.setKey("user.config." + xuser + ".email.push");

                    // ich selbst bin owner meiner dateien
                    if (xuser.contains("@")) {
                        u.addOwner(xuser.substring(0, xuser.indexOf("@")));
                    } else {
                        u.addOwner(xuser);
                    }

                    u.setAdmin(manager.createQuery("SELECT COUNT(u) From User u", Long.class).getSingleResult() == 0);

                    manager.persist(u);
                    manager.persist(emailPush);
                    loginOK = true;
                }

                if (!users.isEmpty()) {
                    u = users.get(0);
                }

                if (u != null) {
                    if (u.getOwners() != null) {
                        owners.addAll(Arrays.asList(u.getOwners().split(",")));
                    }

                    if (u.isAdmin()) {
                        answer.put("admin", "true");
                    }
                    answer.put("owner", u.getOwners());
                }

                answer.put("user", xuser);
                answer.put("login", loginOK ? "loginOK" : "loginFAIL");

                if (loginOK) {
                    addUserConfigToMsg(answer, xuser, u != null && u.isAdmin(), propertyTypedQuery);
                }
                // relay
                answer.put("topic", "master");

                // taglisten mitsenden
                addTagsStat(answer, propertyTypedQuery, owners);


                JMSClientAPI.doSend(answer);

                break;

            case "files":
                // lade alle dateien

                // und dessen tags

                String tagLine = msg.getString("tags");
                String dir = msg.getString("path");


                List<String> tags = new ArrayList<>();
                if (tagLine != null) {
                    for (String tag : tagLine.split("AND")) {
                        if (!tag.trim().isEmpty()) {
                            tags.add(tag.trim());
                        }
                    }
                }
                if (dir != null && !dir.contains("%")) {
                    dir = "%" + dir.trim() + "%";
                }

                propertyTypedQuery.setParameter("key", "tag.lastused");

                Property lastUsed = null;
                try {
                    lastUsed = propertyTypedQuery.getSingleResult();
                } catch (Exception e) {

                }
                if (lastUsed == null) {
                    lastUsed = new Property();
                    lastUsed.setKey("tag.lastused");
                    lastUsed.setValue("");
                    manager.persist(lastUsed);
                }

                for (String tagx : tags) {
                    if (tagx.contains(":") || tagx.contains("=") || tagx.contains("%")) {
                        continue; // statistik nicht fuer spezial
                    }
                    propertyTypedQuery.setParameter("key", "tag.count." + tagx);
                    Property p = null;
                    try {
                        p = propertyTypedQuery.getSingleResult();
                    } catch (Exception e) {
                        p = null;
                    }

                    if (p == null) {
                        p = new Property("tag.count." + tagx, "1");
                        manager.persist(p);
                    } else {
                        long newV = Long.parseLong(p.getValue()) + 1;
                        p.setValue(String.valueOf(newV));
                        manager.merge(p);
                    }


                    String[] trx = lastUsed.getValue().split(",");
                    lastUsed.setValue(tagx);
                    for (int i = 0; i < Math.min(10, trx.length); i++) {
                        String k = trx[i];
                        String vv = lastUsed.getValue();
                        if (!vv.contains(k)) {
                            lastUsed.setValue(vv + "," + k);
                        }
                    }

                }

                manager.merge(lastUsed);

                CriteriaBuilder builder = manager.getCriteriaBuilder();
                CriteriaQuery<File> criteriaQuery = builder.createQuery(File.class);
                Root<File> file = criteriaQuery.from(File.class);
                Predicate all = null;
                if (dir != null) {
                    Predicate p1 = builder.like(file.get("filePath"), dir);
                    Predicate p2 = builder.like(file.get("linkPath"), dir);
                    all = builder.or(p1, p2);
                }


                for (String tag : tags) {
                    Predicate fullCondition = null;

                    // wenn ein TAG sonderzeichen hat, dann ist es ein KEYValue Attribute
                    if (tag.contains(":") || tag.contains("=") || tag.contains("%")) {
                        // nun versuche Files zu Treffen die der beschreibung nahe kommen.
                        String sTag = tag;
                        if (sTag.startsWith(":") || sTag.startsWith("=")) {
                            sTag = "%" + sTag.substring(1);
                        }
                        if (sTag.endsWith(":") || sTag.endsWith("=")) {
                            sTag = sTag.substring(0, sTag.length() - 1) + "%";
                        }

                        List<String> tagsY = new ArrayList<>();

// für Tags
                        TypedQuery<String> tagNames = manager.createQuery("SELECT DISTINCT tags.tagName FROM File f INNER JOIN f.tagList tags WHERE tags.tagName LIKE :item", String.class);
                        tagNames.setParameter("item", sTag);
                        tagsY.addAll(tagNames.getResultList());

                        for (String xxTag : tagsY) {
                            // nun SELECT über die Tags Bauen zum Les
                            Predicate tp = builder.isMember(new Tag(xxTag), file.get("tagList"));
                            if (fullCondition == null) {
                                fullCondition = tp;
                            } else {
                                fullCondition = builder.or(fullCondition, tp);
                            }
                        }
                        tagsY.clear();
// für systags

                        Predicate findTagsCondition = null;

                        tagNames = manager.createQuery("SELECT DISTINCT systags.tagName FROM File f INNER JOIN f.sysTagList systags WHERE systags.tagName LIKE :item", String.class);
                        tagNames.setParameter("item", sTag);

                        tagsY.addAll(tagNames.getResultList());

                        for (String xxTag : tagsY) {
                            // nun SELECT über die Tags Bauen zum Les
                            Predicate tp = builder.isMember(new Tag(xxTag), file.get("sysTagList"));
                            if (findTagsCondition == null) {
                                findTagsCondition = tp;
                            } else {
                                findTagsCondition = builder.or(findTagsCondition, tp);
                            }
                        }

                        if (fullCondition == null) {
                            fullCondition = findTagsCondition;
                        } else if (findTagsCondition != null) {
                            fullCondition = builder.or(fullCondition, findTagsCondition);
                        }

                    } else {
                        // suche normal als TAG
                        fullCondition = builder.isMember(new Tag(tag), file.get("tagList"));
                        fullCondition = builder.or(fullCondition, builder.isMember(new Tag(tag), file.get("sysTagList")));
                    }


                    if (all == null) {
                        // dir path search ist an
                        all = fullCondition;
                    } else {
                        // nur via TAGS
                        all = builder.and(all, fullCondition);
                    }


                }


                criteriaQuery = criteriaQuery.distinct(true).select(file);
                if (all != null) {
                    criteriaQuery.where(all);
                }


                List<File> files = manager.createQuery(criteriaQuery).getResultList();
                // nun alles zurücksenden.

                HashMap<String, Object> answerFile = new HashMap<>();
                // relay
                answerFile.put("topic", "master");
                Consumer<File> nestedParameter = xfile -> addFileToHashMap(xfile, answerFile, propertyTypedQuery);
                files.forEach(nestedParameter);

                ArrayList<String> xowners = new ArrayList<>();
                if (xuser != null) {
                    xowners.add(xuser);
                    TypedQuery<String> usersNames = manager.createQuery("SELECT u.owners FROM User u WHERE u.userName =:userName", String.class);
                    usersNames.setParameter("userName", xuser);
                    List<String> xusers = usersNames.getResultList();
                    if (!xusers.isEmpty()) {
                        xusers.stream().flatMap(uxx -> Stream.of(uxx.split(","))).filter(k -> k.length() > 1).forEach(xowners::add);
                    }
                }

                addTagsStat(answerFile, propertyTypedQuery, xowners);


                JMSClientAPI.doSend(answerFile);
                break;
        }


        if (tx.isActive()) {
            tx.commit();
        }


    }
}
