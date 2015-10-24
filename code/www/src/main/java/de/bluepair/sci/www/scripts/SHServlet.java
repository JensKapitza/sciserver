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
package de.bluepair.sci.www.scripts;

import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Created by kapitza on 23.08.15.
 */
@WebServlet(name = "SHServlet", urlPatterns = {"/enterDir.url", "/enterDir.vbs", "/enterDir", "/enterDir.sh"})
public class SHServlet extends HttpServlet {

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doGet(request, response);
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
// hier kann ich nun den EXec ans system binden.

        String os = request.getParameter("os");
        if (os == null) {
            // detect from ua
            String userAgent = request.getHeader("User-Agent");


            if (userAgent.toLowerCase().indexOf("windows") >= 0) {
                os = "windows";
            } else if (userAgent.toLowerCase().indexOf("mac") >= 0) {
                os = "mac";
            } else if (userAgent.toLowerCase().indexOf("x11") >= 0) {
                os = "unix";
            } else if (userAgent.toLowerCase().indexOf("android") >= 0) {
                os = "android";
            } else if (userAgent.toLowerCase().indexOf("iphone") >= 0) {
                os = "iphone";
            } else {
                os = "unknown";
            }

        }

        switch (os) {
            case "windows": {
                String urx = request.getRequestURL().toString();

                if (urx.endsWith(".vbs")) {
                    response.setContentType("text/vbscript");

                    String script = "Dim SH, txtFolderToOpen, objFSO\n" +


                            "Set SH = WScript.CreateObject(\"WScript.Shell\")\n" +
                            "txtFolderToOpen = \"\"\"" + request.getParameter("path") + "\"\"\"\n" +
                            "SH.Run txtFolderToOpen\n" +
                            "SH.Run \"cmd /C del \"\"\" & Wscript.ScriptFullName & \"\"\"\"\n" +
                            "Set SH = Nothing\n";


                    response.getWriter().write(script);

                } else {
                    // version 2
                    response.setContentType("application/x-mswinurl");
                    response.getWriter().write("[InternetShortcut]\nURL=file:///" + request.getParameter("path").replaceAll("\"", "\\\\\"") + "\n\n");
                }
            }
            break;
            case "unix":
            case "linux":
            case "android": // need test
            {
                response.setContentType("application/x-sh");
                response.getWriter().write("#!/bin/sh\nxdg-open \"" + request.getParameter("path").replaceAll("\"", "\\\\\"") + "\"\n[ -f \"$0\" ] && rm \"$0\"\n");

            }
            break;
            case "mac":
            case "iphone": // need test
            {
                response.setContentType("application/x-sh");
                response.getWriter().write("#!/bin/sh\nopen \"" + request.getParameter("path").replaceAll("\"", "\\\\\"") + "\"\n[ -f \"$0\" ] && rm \"$0\"\n");

            }
            break;
        }


    }
}
