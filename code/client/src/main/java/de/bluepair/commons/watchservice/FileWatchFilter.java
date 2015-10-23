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
package de.bluepair.commons.watchservice;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

public class FileWatchFilter implements Consumer<FileWatchEvent> {

    private Consumer<FileWatchEvent> consumer;
    private Path path;

    public FileWatchFilter(Path file, Consumer<FileWatchEvent> consumer) throws IOException {
        Objects.requireNonNull(file, "file must exist");

        Objects.requireNonNull(getDirectory(), "parent must exist");
        if (!Files.isDirectory(getDirectory())) {
            throw new IOException("parent directory must exist");
        }

        Objects.requireNonNull(consumer, "consumer must exist");
        this.path = file;
        this.consumer = consumer;
    }

    @Override
    public void accept(FileWatchEvent event) {
        if (event.equalsPath(path)) {
            consumer.accept(event);
        }
    }

    public Path getPath() {
        return path;
    }

    public final Path getDirectory() {
        return path.getParent();
    }

}
