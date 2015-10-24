/**
 * This file is part of provider.
 *
 * provider is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * provider is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with provider.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.bluepair.sci.provider.transmit;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FilePartInputStream extends InputStream {

    private final List<long[]> ranges = new ArrayList<>();
    private SeekableByteChannel channel;
    private long[] currentRange = null;
    private long counter = -1;

    public FilePartInputStream(SeekableByteChannel channel) throws IOException {
        this(channel, new long[]{0, channel.size()});

    }

    public FilePartInputStream(SeekableByteChannel channel, List<long[]> ranges) {

        this.channel = channel;
        this.ranges.addAll(ranges);
    }

    public FilePartInputStream(SeekableByteChannel channel, Range... ranges) {

        this.channel = channel;
        for (Range r : ranges) {
            this.ranges.add(new long[]{r.start, r.length});
        }
    }

    public FilePartInputStream(SeekableByteChannel channel, long[]... ranges) {
        this(channel, Arrays.asList(ranges));
    }

    public void checkRanges() throws IOException {
        if (currentRange != null) {
            if (counter >= currentRange[1]) {
                counter = -1;
            }
        }

        if (counter == -1) {
            if (!ranges.isEmpty()) {
                currentRange = ranges.remove(0);
                // reset channel
                channel.position(currentRange[0]);
                counter = 0;
            } else {
                currentRange = null;
            }
        }
    }

    public boolean hasMore() throws IOException {
        checkRanges();
        return (currentRange != null && counter != -1);
    }

    @Override
    public synchronized int read() throws IOException {
        if (!hasMore()) {
            throw new EOFException("limit reached");
        }
        ByteBuffer buffer = ByteBuffer.allocate(1);

        if (channel.read(buffer) != 1) {
            throw new EOFException("buffer read faild");
        }
        buffer.flip();
        int b = buffer.get();
        counter += 1;
        checkRanges();
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (b == null || b.length < off + len) {
            throw new IOException("buffer does not match size");
        }
        checkRanges();

        if (currentRange == null || counter == -1) {
            return -1; // ende
        }

        long mLen = (currentRange[1] - counter);
        // das sollte nicht sein nach einem rangecheck
        if (mLen == 0) {
            return -1;
        }

        int xlen = Math.min(len - off, (int) mLen);

        ByteBuffer bb = ByteBuffer.wrap(b);
        bb.position(off);
        bb.limit(xlen);
        channel.read(bb);
        counter += xlen;

        checkRanges();

        return xlen;

    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    public static class Range {

        private long start;
        private long length;

        public Range(long start, long length) {
            this.start = start;
            this.length = length;
        }

    }

}
