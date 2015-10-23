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
package de.bluepair.test;

import java.nio.file.Paths;

import de.bluepair.commons.watchservice.FileWatchService;

public class TestWatchSHA {
	public static void main(String[] args) throws Exception {
		FileWatchService ws = new FileWatchService(Paths.get("./"), true);
		ws.startService();
		ws.publishUpdateAll();
		System.out.println(ws.allSHAAttributes());

	}
}
