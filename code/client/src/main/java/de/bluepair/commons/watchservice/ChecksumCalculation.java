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

import de.bluepair.commons.file.FileAnalysis;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;

public class ChecksumCalculation implements Runnable {

    private long lastModifiedTime;
    private FileAnalysis aThis;

    private BiConsumer<Object, Map<String, String>> listener;
    private boolean forceBL;
    private Long blSize;

    public ChecksumCalculation(long lastModifiedTime, FileAnalysis aThis,
                               BiConsumer<Object, Map<String, String>> listener, Long blSize, boolean forceBL) {
        this.lastModifiedTime = lastModifiedTime;
        this.aThis = aThis;
        this.listener = listener;
        this.forceBL = forceBL;
        this.blSize = blSize;
    }

    @Override
    public void run() {

        if (!aThis.exists() || aThis.isFolder()) {
            return; // keine datei mehr
        }

        // check modification time
        // aufhören wenn ich änderungen gefunden hab!
        // wenn restart erzwungen wird nochmal neu starten und dannach erst
        // berechnen abbrechen.
        final String pid = UUID.randomUUID().toString();
        boolean callListener = false;
        // hier wird geschaut ob ich schon fertig bin nicht doppelt rechnen!
        try {
            long checkTime = aThis.readLongAttribute("sha512-time", -1);
            if (checkTime == lastModifiedTime && aThis.readAttribute("sha512-lock").isEmpty()) {
                // die loop in den EVENTS beenden!
                Map<String, String> sha512 = aThis.readAttributes("sha512", null);
                // auch wenn ich evtl. öfter sende
                // das wird sich dann schnell beenden
                if (sha512 != null && !sha512.isEmpty()) {
                    listener.accept(aThis, sha512);
                    return;
                }
            }

        } catch (NumberFormatException ex) {
            // dont care if there is no cache key/value
        }

        boolean forceCalc = System.getProperty(FileWatchService.FORCE_CALC_ON_READONLY, "false")
                .equalsIgnoreCase("true");

        if (!aThis.isWriteable() && !forceCalc) {
            return;
        }
        Map<String, String> sha512 = Collections.emptyMap();
        // diese on datei speere brauche ich damit ich meine eigenen EVENTS
        // erkenne

        if (aThis.isWriteable()) {
            String lock = aThis.readAttribute("sha512-lock");
            String lockTime = aThis.readAttribute("sha512-locktime");
            // hole und setze sperre
            if (lock.isEmpty() || shouldKill(lockTime)) {
                // alle alten attribute killen
                aThis.readAttributeNames().forEach(aThis::deleteAttribute);
                aThis.putAttribute("sha512-locktime", getLockTime());
                aThis.putAttribute("sha512-lock", pid);
            }
        }

        if (isLockOwner(forceCalc, pid)) {
            sha512 = aThis.sha512(lastModifiedTime, blSize, forceBL);
            if (aThis.exists() && sha512 != null && !sha512.isEmpty() && aThis.isWriteable()
                    && isLockOwner(forceCalc, pid)) {
                // zurückschreiben der informationen in die attribute
                // ACHTUNG ICH BEKOMME MICH SELBST ALS EVENT
                sha512.put("sha512-time", String.valueOf(lastModifiedTime));
                aThis.writeAttributes(sha512);
                callListener = true;
            }
        }

        // erzeugten lock löschen.
        if (aThis.exists() && isLockOwner(forceCalc, pid) && aThis.isWriteable()) {
            // erzeugt ein modify event evtl. looping
            // daher nur machen wenn nötig!
            aThis.deleteAttribute("sha512-lock");
            aThis.deleteAttribute("sha512-locktime");
            // wir starten dadurch zumindest unter Linux ein EVENT daher
            // ist ein lisenercall nicht notwendig
            // dups meiden.

            if (System.getProperty(FileWatchService.NO_DUPES, "false").equalsIgnoreCase("true")) {
                callListener = false;
            }

        }

        if (callListener) {
            listener.accept(aThis, sha512);
        }

    }

    private boolean isLockOwner(boolean force, String pid) {
        String lockNow = aThis.readAttribute("sha512-lock");
        Objects.requireNonNull(pid, "THREAD PID need not to be null");
        return pid.equals(lockNow) || (force && lockNow.isEmpty());
    }

    private boolean shouldKill(String lockTime) {
        try {
            long time = Long.parseLong(lockTime);
            return time < getLockTime();
        } catch (Exception e) {
            return true;
        }
    }

    private long getLockTime() {
        long size = aThis.getFileSize();
        long last = aThis.getLastModifiedTime();

        // wenn ich 1byte je sec. lese dann kill ich den thread und
        // schreibe neu.
        // unlocken
        return last + size * 1000;

    }

}
