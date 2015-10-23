/**
 * This file is part of client.
 * <p>
 * client is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * client is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * <p>
 * You should have received a copy of the GNU Affero General Public License
 * along with client.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.bluepair.commons.watchservice;

import de.bluepair.commons.file.FileAnalysis;

import java.io.File;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileWatchService extends TimerTask {

    public static final String NO_DUPES = "de.bluepair.watchservice.nodupes";
    public static final String SHEDULE_TIME = "de.bluepair.watchservice.polling";
    public static final String FORCE_CALC_ON_READONLY = "de.bluepair.watchservice.readonly.force";
    private final AtomicBoolean forceHold = new AtomicBoolean(true);
    private final Timer timer = new Timer("SERVICE_FILEWATCHER");

    private final List<FileWatchEvent> oldEvents = new CopyOnWriteArrayList<>();
    private final List<FileWatchEvent> pushEvents = new CopyOnWriteArrayList<>();
    private final ConcurrentLinkedQueue<FileWatchEvent> sendCoreEvents = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final Map<Path, FileWatchEvent> finishEvents = new ConcurrentHashMap<>();
    private final ConcurrentMap<FileSystem, FileWatcher> watchers = new ConcurrentHashMap<>();
    private BlockingQueue<Runnable> worklist = new LinkedBlockingQueue<>();
    private Thread worker;
    private long sheduleTime;
    private Consumer<FileWatchEvent> listener;
    private boolean recursive;
    private List<String> linuxSpezialDirs;
    private boolean linuxOS = false;

    public FileWatchService(Path dir, boolean recursive) {
        this(dir, recursive, Long.parseLong(System.getProperty(SHEDULE_TIME, "5000")));
    }

    public FileWatchService(FileWatchFilter filter, long delay) {
        this(filter.getDirectory(), false, delay);
        listener = filter;
    }

    public FileWatchService(FileWatchFilter filter) {
        this(filter.getDirectory(), false);
        listener = filter;
    }

    public FileWatchService(Path dir, boolean recursive, long delay) {
        this.recursive = recursive;

        worker = new Thread("eventworker for watchservice") {
            @Override
            public void run() {
                while (true) {
                    try {
                        Runnable r = worklist.take();
                        r.run();
                    } catch (Exception e) {
                        if (e instanceof InterruptedException && Thread.interrupted()) {
                            break;
                        }
                        // fehlerausgabe ist zwar nicht schön aber
                        Logger.getLogger(FileWatchService.class.getName()).log(Level.WARNING, "error on exec worklistitem", e);
                    }
                }
            }
        };

        // starte den dienst früh da evtl. andere submit nutzen

        worker.setDaemon(true);
        worker.start();

        // als demon sollte ein kill erlaubt sein und events schnell beendet werden.

        sheduleTime = delay;
        Logger.getLogger(FileWatchService.class.getName()).log(Level.INFO,
                "ON READONLY FILES you can force Checksum calculation with -D" + FORCE_CALC_ON_READONLY + "=true");
        linuxOS = System.getProperty("os.name").equalsIgnoreCase("linux");
        if (linuxOS) {
            linuxSpezialDirs = Arrays.asList("/dev", "/proc", "/sys", "/run");

            Logger.getLogger(FileWatchService.class.getName()).log(Level.INFO, "LINUX OS detected");

            Logger.getLogger(FileWatchService.class.getName()).log(Level.INFO,
                    "Filter DUPS are ENABLED now with -D" + NO_DUPES + "=true");

            Logger.getLogger(FileWatchService.class.getName()).log(Level.INFO,
                    "if you have troubles with inotify errors (limit reached) fix it with\n"
                            + " sudo sysctl fs.inotify.max_user_watches=<some random high number> #"
                            + " more:  http://unix.stackexchange.com/questions/13751/kernel-inotify-watch-limit-reached \n"
                            + " for watchlimit type: echo <some random high number> > /proc/sys/fs/inotify/max_user_instances \n\n"
                            + "prove the above with: \n" + "cat /proc/sys/fs/inotify/max_user_watches\n"
                            + "cat /proc/sys/fs/inotify/max_user_instances");

            System.setProperty(FileWatchService.NO_DUPES, "true");

        }
        addWatcherRecursive(dir);
        // alle kinder wenn rec an ist.

    }

    public void submit(Runnable task) {
        try {
            worklist.put(task);
        } catch (InterruptedException e) {
            Logger.getLogger(FileWatchService.class.getName()).log(Level.WARNING, "error on insert worklistitem", e);
        }
    }

    public Timer getTimer() {
        return timer;
    }

    @Override
    public void run() {
        publishEvents();

        //control threads 4events
        publishEvent();

    }

    private void eventConsumer(List<FileWatchEvent> events) {
        events.forEach(this::insertEvent);
    }

    private void publishEvent() {
        while (!sendCoreEvents.isEmpty()) {
            final FileWatchEvent first = sendCoreEvents.remove();
            if (listener != null) {
                Runnable r = () -> {
                    try {
                        listener.accept(first);
                    } catch (Exception e) {
                        Logger.getLogger(FileWatchService.class.getName()).log(Level.INFO, "Error on event: " + first,
                                e);

                    }
                };

                submit(r);
            }

        }
    }

    private void publishEvents() {
        // finish wenn wir in new kein weiteres event haben
        final Date now = new Date();

        synchronized (pushEvents) {
            Map<Path, FileWatchEvent> stay = new HashMap<>();

            finishEvents.forEach((k, v) -> {
                if (v.eventBefor(now) && countIsZero(pushEvents, v) && countIsZero(oldEvents, v)) {
                    sendCoreEvents.add(v);
                } else {
                    stay.put(k, v);
                }
            });

            finishEvents.clear();
            finishEvents.putAll(stay);

            if (oldEvents.isEmpty()) {
                oldEvents.addAll(pushEvents);
                pushEvents.clear();
            } else {
                oldEvents.parallelStream().map(this::mapToCopy).forEach(sendCoreEvents::add);
                oldEvents.clear();
            }
        }
    }

    private boolean countIsZero(Collection<FileWatchEvent> s, FileWatchEvent v) {
        return s.parallelStream().filter(v::equalsPath).count() == 0;
    }

    private FileWatchEvent mapToCopy(FileWatchEvent oldEvent) {
        if (oldEvent.equalsType(FileWatchEventType.MODIFY)) {
            boolean copyDetected = oldEvent.eventBefor(oldEvent.getLastModifiedTime());

            FileWatchEvent copy = oldEvent;
            if (copyDetected) {
                copy = new FileWatchEvent(this, FileWatchEventType.ONCOPY, oldEvent);

            }

            FileWatchEvent fin = new FileWatchEvent(this, FileWatchEventType.FINISH, oldEvent);
            fin.setEventTime(new Date(System.currentTimeMillis() + (sheduleTime / 2)));
            FileWatchEvent f = finishEvents.put(fin.getResolvedPath(), fin);
            if (f != null) {
                fin.addSuppressed(f);
            }

            return copy;
        } else {
            return oldEvent;
        }
    }

    public Map<String, String> allSHAAttributes() {

        List<File> dirs = getDirectories().parallelStream().map(Path::toFile).collect(Collectors.toList());

        Stream<File> items = dirs.parallelStream()
                .flatMap(p -> p.list() == null ? Stream.empty() : Stream.of(p.listFiles()));

        // es bring nichts parallel zu machen.
        Stream<FileAnalysis> files = items.parallel().map(File::toPath).filter(Files::isReadable)
                .map(f -> new FileAnalysis(f, true));

        List<Map<String, String>> all = files.parallel().map(a -> {
            Map<String, String> map = a.readAttributes("sha512", null);
            map.put("path", a.getResolvedPath().toString());
            return map;
        }).collect(Collectors.toList());

        HashMap<String, String> map = new HashMap<>();
        all.forEach(m -> {
            String path = m.get("path");
            m.values().forEach(e -> {
                if (!e.equals(path)) {
                    map.put(e, path);
                }
            });

        });
        // return
        return map;

    }

    public List<FileAnalysis> pathForHash(String... keySet) {
        List<File> dirs = getDirectories().parallelStream().map(Path::toFile).collect(Collectors.toList());

        Stream<File> items = dirs.parallelStream()
                .flatMap(p -> p.list() == null ? Stream.empty() : Stream.of(p.listFiles()));

        // es fehlt noch der basis dir.

        List<String> keySetList = Arrays.asList(keySet);
        // es bring nichts parallel zu machen.
        return items.parallel().map(File::toPath).filter(Files::isReadable).map(f -> new FileAnalysis(f, true))
                .parallel().filter(f -> {
                    Map<String, String> map = f.readAttributes("sha512", null);
                    Optional<String> opt = keySetList.stream().filter(k -> map.containsValue(k)).findAny();
                    return (opt.isPresent());
                }).collect(Collectors.toList());

    }

    public void publishUpdateAll() {
        publishUpdateAll(false);
    }

    public void publishUpdateAll(Path normalize, boolean force) {

        File p = normalize.toFile();
        Stream<File> items = p.list() == null ? Stream.empty() : Stream.of(p.listFiles());

        // es bring nichts parallel zu machen.
        Stream<FileWatchEvent> files = items.parallel().map(File::toPath).filter(Files::isReadable).map(f -> {
            FileWatchEvent ev = new FileWatchEvent(this, FileWatchEventType.UPDATE, f);
            ev.setUserData(force ? "FORCE_CALC" : null);
            return ev;
        });

        // alle dateien und dirs sind nun drin.
        // wegen set nur einmal.
        // es ist schneller das Array zu fixen als
        // ständig einzeln zu arbeiten
        List<FileWatchEvent> list = files.parallel().collect(Collectors.toList());

        synchronized (pushEvents) {
            pushEvents.addAll(list);

        }

    }

    public void publishUpdateAll(boolean force) {
        List<File> dirs = getDirectories().parallelStream().map(Path::toFile).collect(Collectors.toList());

        Stream<File> items = dirs.parallelStream()
                .flatMap(p -> p.list() == null ? Stream.empty() : Stream.of(p.listFiles()));

        // es bring nichts parallel zu machen.
        Stream<FileWatchEvent> files = Stream.concat(items, dirs.stream()).parallel().map(File::toPath)
                .filter(Files::isReadable).map(f -> {
                    FileWatchEvent ev = new FileWatchEvent(this, FileWatchEventType.UPDATE, f);
                    ev.setUserData(force ? "FORCE_CALC" : null);
                    return ev;
                });

        // alle dateien und dirs sind nun drin.
        // wegen set nur einmal.
        // es ist schneller das Array zu fixen als
        // ständig einzeln zu arbeiten
        List<FileWatchEvent> list = files.parallel().collect(Collectors.toList());

        boolean shouldShrink;

        synchronized (pushEvents) {
            pushEvents.addAll(list);
            shouldShrink = pushEvents.parallelStream()
                    .filter(f -> f.equalsType(FileWatchEventType.DELETE) || f.equalsType(FileWatchEventType.MODIFY))
                    .findFirst().isPresent();
        }

        if (shouldShrink) {
            Logger.getLogger(FileWatchService.class.getName()).log(Level.INFO,
                    "maybe we can shrink the events but it is not implemented now");
        }
        // DELETE und MODIFY testen lasen?
        // evtl. shrinken??
    }

    private void insertEvent(FileWatchEvent event) {
        if (event != null) {

            boolean rmDupes = false;
            switch (event.getType()) {
                case MODIFY:
                case FINISH:
                case UPDATE:
                    event.setEventTime(new Date(event.getLastModifiedTime()));
                case DELETE:

                    for (FileWatchEvent xe : pushEvents) {
                        if (event.equalsPath(xe)) {
                            rmDupes = true;
                            break;
                        }
                    }


                    break;
                case CREATE:
                    if (recursive && event.isFolder()) {
                        addWatcherRecursive(event.getResolvedPath());
                    }

                case ONCOPY:
                case OVERFLOW:
                    break;
                default:
                    break;
            }

            synchronized (pushEvents) {
                if (rmDupes) {
                    ArrayList<FileWatchEvent> feL = new ArrayList<>(pushEvents.size());
                    for (FileWatchEvent xe : pushEvents) {
                        if (event.equalsPath(xe)) {
                            event.addSuppressed(xe);
                        } else {
                            feL.add(xe);
                        }
                    }

                    pushEvents.clear();
                    pushEvents.addAll(feL);


                }
                pushEvents.add(event);
            }
        }
    }

    public final void addWatcherRecursive(Path dir) {
        addWatcherRecursive(dir == null ? null : dir.toFile());
    }

    public final void addWatcherRecursive(File p) {
        Objects.requireNonNull(p, "File must not be null!");
        Path dir = p.toPath();
        if (recursive && notKnownSpezial(dir)) {
            File[] files = p.listFiles();
            if (files != null) {
                Arrays.asList(files).forEach(this::addWatcherRecursive);
            }
        }
        addWatcher(dir);
    }

    public final void addWatcher(Path p) {
        if (Files.isDirectory(p) && !Files.isSymbolicLink(p)) {
            Path dir = p.toAbsolutePath().normalize();
            FileSystem fs = dir.getFileSystem();
            boolean start = false;
            FileWatcher w = watchers.get(fs);

            // passiert nicht oft,
            // aber wenn dann nochmal sperren!
            if (w == null || !w.isActive()) {
                synchronized (watchers) {
                    w = watchers.get(fs);
                    if (w == null) {
                        w = new FileWatcher(this, fs, this::eventConsumer);
                        watchers.put(fs, w);

                        // erste eintrag
                        w.reg(dir);

                        // nicht via watchservice weil ich sonst events verpasse!
                        // starten des erzeugten dienstes!
                        Thread t = new Thread(w, "FileWatcher @" + fs.toString());
                        t.start();


                        start = true;
                    }
                }
            } else {
                w.reg(dir);
            }

            // ok nun ein update evtl. habe ich was verpasse!
            File[] items = dir.toFile().listFiles();
            if (items != null) {
                for (File file : items) {
                    FileWatchEvent event = new FileWatchEvent(this, FileWatchEventType.UPDATE, file.toPath());
                    inject(event);
                }
            }


        }
    }

    public synchronized void addListener(Consumer<FileWatchEvent> listener) {
        if (this.listener == null) {
            this.listener = listener;
        } else {
            this.listener = this.listener.andThen(listener);
        }
    }

    public List<Path> getDirectories() {
        List<Path> xwatchers = watchers.values().parallelStream().flatMap(f -> f.getItems().stream())
                .collect(Collectors.toList());
        List<Path> list = new ArrayList<>(xwatchers);
        // heimat immer mit liefernt, es kann sein, dass ich das brauche fuer
        // die configs etc.
        list.add(Paths.get("./"));
        return list;
    }

    public void startService() {
        timer.scheduleAtFixedRate(this, 0, sheduleTime);
        forceHold.set(true);
    }

    public void setSheduleTime(long sheduleTime) {
        this.sheduleTime = sheduleTime;
        cancel();
        timer.purge();
        startService();
    }

    public void stopService() {
        watchers.values().parallelStream().forEach(FileWatcher::stopService);
        forceHold.set(false);
        worker.interrupt();
        cancel();
        timer.cancel();
        timer.purge();

    }

    public boolean isActive() {
        return forceHold.get() && !watchers.isEmpty();
    }

    private boolean notKnownSpezial(Path dir) {
        // ist es eine DATEI dann OK
        // else
        if (!Files.isDirectory(dir)) {
            return true;
        }

        // wenn es ein DIR ist
        // ist es ein mount unter LINUX?
        if (linuxOS) {
            String key = dir.normalize().toString();
            return !linuxSpezialDirs.contains(key);
        }

        // alles ok wenn ich kein OS erkannt habe
        return true;
    }

    public void inject(FileWatchEvent ev) {
        insertEvent(ev);
    }


}
