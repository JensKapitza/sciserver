/**
 * This file is part of client.
 *
 * client is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * client is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with client.  If not, see <http://www.gnu.org/licenses/>.
 */
/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.bluepair.test;

import de.bluepair.commons.file.FileAnalysis;
import java.nio.file.Paths;
import java.util.function.Consumer;

/**
 *
 * @author kapitza
 */
public class SimpleFunction {

    public static void main(String[] args) {
        Consumer<String> c1 = t -> {
            System.out.println(t);
        };
        Consumer<String> c2 = t -> {
            System.out.println(t + " -");
        };
        Consumer<String> c3 = c1.andThen(c2);

        c3.accept("gfhsdfh");

        // calctest
        //
        FileAnalysis a = new FileAnalysis(Paths.get("/home/kapitza/masterarbeit/sciserve_v3/provider/firefox-38.0.5.tar.bz2"), true);

        a.sha512(55555).forEach((k, v) -> {

            System.out.println(k + ":" + v);
        });

    }
}
