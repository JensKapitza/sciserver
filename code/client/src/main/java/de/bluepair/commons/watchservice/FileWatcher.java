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

import de.bluepair.sci.client.SHAUtils;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

class FileWatcher implements Runnable {

    private final AtomicBoolean helper = new AtomicBoolean();
    private final Consumer<List<FileWatchEvent>> consumer;
    private final Map<String, Path> items = new TreeMap<>();
    private WatchService service;

    private FileWatchService parentService;

    public FileWatcher(FileWatchService parent, FileSystem fs, Consumer<List<FileWatchEvent>> consumer) {
        this.consumer = consumer;
        this.parentService = parent;
        try {
            service = fs.newWatchService();
        } catch (IOException ex) {
            Logger.getLogger(FileWatcher.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private static Optional<FileWatchEvent> translate(WatchEvent<?> event, Path parent, FileWatchService parentService) {
        Path changed = (Path) event.context();
        int count = event.count();
        if (changed != null) {
            changed = parent.resolve(changed).toAbsolutePath().normalize();
            WatchEvent.Kind<?> kind = event.kind();
            FileWatchEventType myType = null;

            if (kind.equals(StandardWatchEventKinds.OVERFLOW)) {
                myType = FileWatchEventType.OVERFLOW;
            } else if (kind.equals(StandardWatchEventKinds.ENTRY_DELETE)) {
                myType = FileWatchEventType.DELETE;
            } else {
                if (!Files.isReadable(changed)) {
                    return Optional.empty();
                }
                if (kind.equals(StandardWatchEventKinds.ENTRY_MODIFY)) {
                    myType = FileWatchEventType.MODIFY;
                }
                if (kind.equals(StandardWatchEventKinds.ENTRY_CREATE)) {
                    myType = FileWatchEventType.CREATE;
                }

            }
            return Optional.of(new FileWatchEvent(parentService, myType, changed, count));

        }
        return Optional.empty();
    }

    public List<Path> getItems() {
        synchronized (items) {
            return new ArrayList<>(items.values());
        }
    }

    public void reg(Path dir) {
        Path dirPath = dir.toAbsolutePath().normalize();
        if (Files.isReadable(dirPath)) {
            boolean check;
            String key = SHAUtils.sha512(dirPath.toString());
            // hier einen hashwert
            synchronized (items) {
                check = items.get(key) == null;
            }
            if (check) {
                try {
                    WatchKey watchKey = dirPath.register(service,
                            StandardWatchEventKinds.OVERFLOW,
                            StandardWatchEventKinds.ENTRY_MODIFY,
                            StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_DELETE);

                    synchronized (items) {
                        items.put(key, dirPath);
                    }

                    consumer.accept(checkEvents(watchKey));
                } catch (Exception nsf) {
                    // ignored
                }

            }
        }
    }

    @Override
    public void run() {

        try {
            helper.set(true);
            WatchKey watchKey;
            while (isActive()) {
                // hier fehlt was.
                try {
                    watchKey = service.take();
                    // it will be just my watchkey
                } catch (InterruptedException | ClosedWatchServiceException ie) {
                    // ignored
                    break; // fehler beim SERVICE
                }

                // das kann zu schnell sein,also warte immer mind. X sec. bis
                // was neues kommen kann.
                // window adjusting sobald man länger nichts bekommt.
                if (watchKey != null && isActive()) {
                    List<FileWatchEvent> events = checkEvents(watchKey);
                    consumer.accept(events);
                    // hier muss ich ein wenig warten weil evtl. noch events in
                    // der schlange sind
                    // reset the key

                }
            }

        } finally {
            stopService();

        }
    }

    public void stopService() {
        helper.set(false);
        synchronized (items) {
            items.clear();
            if (items.isEmpty()) {
                try {
                    service.close();
                } catch (IOException ex) {
                    Logger.getLogger(FileWatcher.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    public boolean isActive() {
        return helper.get();
    }

    private List<FileWatchEvent> checkEvents(WatchKey watchKey) {

        final Path parent = (Path) watchKey.watchable();

        List<WatchEvent<?>> events = watchKey.pollEvents();

        Function<WatchEvent<?>, Optional<FileWatchEvent>> functionMapper
                = (event) -> translate(event, parent, this.parentService);

        List<FileWatchEvent> result = events.stream()
                .map(functionMapper)
                .filter(Optional::isPresent).map(Optional::get)
                .collect(Collectors.toList());

        boolean valid = watchKey.reset();
        if (!valid) {
            synchronized (items) {
                items.remove(watchKey.watchable().toString());
            }
            // wenn dir gelöscht wurde, oder kill genutzt wurde!
            // System.out.println("Key has been unregisterede");
            // nur stop wenn es mein watchable war

        }
        return result;
        // diese events nun an die fassade reichen.
    }

}
