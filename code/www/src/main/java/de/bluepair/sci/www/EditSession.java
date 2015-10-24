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

import javax.enterprise.context.SessionScoped;
import javax.faces.application.FacesMessage;
import javax.faces.component.UIComponent;
import javax.faces.component.UIInput;
import javax.faces.context.FacesContext;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SessionScoped
@Named("edit")
public class EditSession implements Serializable {
    private static final long serialVersionUID = 1L;

    private String input;
    private String keyInput;
    private String valueInput;

    private String seperatorInput;
    private String runInput;

    private String runOutput;
    private boolean waitfor;

    private FileItem item;
    private String[] tags;
    @Inject
    private User user;
    private String[] owners;

    public static void sendOwner(String owner, String user, boolean add) {

        Map<String, Object> map = new HashMap<String, Object>();
        map.put("type", "www");
        map.put("cmd", "owner");
        map.put("owner", owner);
        if (!add) {
            map.put("delete", "true");
        }
        map.put("user", user);

        JMSClientAPI.doSend(map);

    }

    public List<String> itemTags() {
        if (item != null) {
            return item.getTags().stream().filter(t -> !t.startsWith("(system)")).collect(Collectors.toList());
        }
        return null;
    }

    public String getRunOutput() {
        return runOutput;
    }

    public void setRunOutput(String runOutput) {
        this.runOutput = runOutput;
    }

    public String getRunInput() {
        return runInput;
    }

    public void setRunInput(String runInput) {
        this.runInput = runInput;
    }

    public boolean isWaitfor() {
        return waitfor;
    }

    public void setWaitfor(boolean waitfor) {
        this.waitfor = waitfor;
    }

    public String getSeperatorInput() {
        return seperatorInput;
    }

    public void setSeperatorInput(String seperatorInput) {
        this.seperatorInput = seperatorInput;
    }


    private void handleAnswerCMD(Map<String, Object> answer) {
        String app = (String) answer.get("app");

        if (runInput != null && app != null && runInput.startsWith(app)) {
            runOutput = (String) answer.get("output");
        }
        runInput = null;
    }

    public String runCMD() {
        String sep = " ";
        if (seperatorInput != null && !seperatorInput.isEmpty()) {
            sep = seperatorInput;
        }
        if (runInput != null) {
            String[] cmd = runInput.split(sep);

            // arg0 ist der befehl
            // rest args

            HashMap<String, Object> msg = new HashMap<>();
            msg.put("app", cmd[0]);

            for (int i = 1; i < cmd.length; i++) {
                msg.put("arg." + i, cmd[i]);
            }
            msg.put("cmd", "exec");
            msg.put("to", getItem().getProvider());
            // provider erreichen
            msg.put("topic", "master");

            msg.put("path", getItem().getPath());
            msg.put("sha512", getItem().getSha());
// brauche masterkey wegen provider


            // waitfor
            msg.put("waitfor", String.valueOf(waitfor));

            runOutput = "executed";
            JMSClientAPI.doSend(msg, this::handleAnswerCMD, 2000);
        }


        return null;
    }

    public String getKeyInput() {
        return keyInput;
    }

    public void setKeyInput(String keyInput) {
        this.keyInput = keyInput;
    }

    public String getValueInput() {
        return valueInput;
    }

    public void setValueInput(String valueInput) {
        this.valueInput = valueInput;
    }

    public String[] getTags() {
        return tags;
    }

    public void setTags(String[] tags) {
        this.tags = tags;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String[] getOwners() {
        return owners;
    }

    public void setOwners(String[] owners) {
        this.owners = owners;
    }


    public String deleteConfig() {
        if (owners != null) {
            for (String owner : owners) {
                user.removeConfig(owner);
            }
        }


        return null;
    }

    public String deleteOwner() {
        if (owners != null) {
            for (String owner : owners) {
                user.getOwner().remove(owner);
                sendOwner(owner, user.getName(), false);
            }
        }

        return null;
    }

    public FileItem getItem() {
        return item;
    }

    public void setItem(FileItem item) {
        this.item = item;
        setTags(null);
        setInput(null);
        setRunInput(null);
        setRunOutput(null);
    }

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public void validateInput(FacesContext context, UIComponent cmp, Object value) {

        UIInput in = (UIInput) cmp;
        if (value != null && (value.toString().contains("=") || value.toString().contains(":") || value.toString().contains("%"))) {
            in.setValid(false);
            FacesMessage msg = new FacesMessage("ungültige Eingabe");
            msg.setSeverity(FacesMessage.SEVERITY_ERROR);
            context.addMessage(in.getClientId(context), msg);
        } else {
            in.setValid(true);
        }

    }

    public String doAddTag() {

        if (input != null && !input.isEmpty()) {
            // sende an das JSM
            // für sha
            FileItem item = getItem();
            List<String> tags = item.getTags();
            if (!tags.contains(input)) {
                tags.add(input);
                sendTAGtoJMS(item.getSha(), input, true);
            }

            input = null;

        }

        if (keyInput != null && !keyInput.isEmpty() && valueInput != null && !valueInput.isEmpty()) {
            // sende an das JSM
            // für sha
            FileItem item = getItem();
            List<String> tags = item.getTags();
            String xTag = keyInput + "=" + valueInput;
            if (!tags.contains(xTag)) {
                tags.add(xTag);
                sendTAGtoJMS(item.getSha(), xTag, true);
            }

            keyInput = null;
            valueInput = null;
        }

        return null;
    }

    public String searchTag() {

        if (tags != null && tags.length > 0) {
            String xtags = Arrays.asList(
                    tags
            ).stream().collect(Collectors.joining(" AND "));
            String url = "redirect.xhtml?url=search.xhtml&tag=" + xtags;

            try {
                FacesContext.getCurrentInstance().getExternalContext()
                        .redirect(url);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public String deleteTag() {

        for (String tag : tags) {
            // sende an das JSM
            // für sha
            List<String> tags = item.getTags();
            if (tags.contains(tag)) {
                tags.remove(tag);
                sendTAGtoJMS(item.getSha(), tag, false);
            }

        }
        return null;
    }

    public void sendTAGtoJMS(String sha, String input, boolean addTOList) {
        HashMap<String, Object> map = new HashMap<>();

        map.put("type", "tag");
        map.put("sha512", sha);
        map.put("tag", input);
        if (!addTOList) {
            map.put("delete", "true");
        }
        JMSClientAPI.doSend(map);

    }
}
