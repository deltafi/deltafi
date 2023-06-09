/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.deltafi.common.reader;

import java.io.*;

/**
 * Buffered reader that tracks the number of bytes read and bounds the
 * readLine method by the maxLineSize setting to limit the characters
 * read into memory.
 */
public class BoundedLineReader extends BufferedReader {

    static final String LINE_OVERFLOW = "The current line will not fit within the max size limit";

    private static final int UNSET = -2;
    /** Maximum bytes to read when looking for a line of text */
    private long maxLineSize;
    private long bytesRead = 0;
    private int nextChar = UNSET;

    public BoundedLineReader(Reader in, long maxLineSize) {
        super(in);
        setMaxLineSize(maxLineSize);
    }

    public BoundedLineReader(Reader in, int sz, long maxLineSize) {
        super(in, sz);
        setMaxLineSize(maxLineSize);
    }

    /**
     * Counts the number of bytes in the next line of text. A line is considered to be terminated by any one
     * of a line feed ('\n'), a carriage return ('\r'), a carriage return
     * followed immediately by a line feed, or by reaching the end-of-file
     * (EOF).
     * @return the number of bytes in the next line of data including the terminating characters
     * @throws IOException If an I/O error occurs or the line is not terminated before reading the maximum number bytes as set by {@link BoundedLineReader#maxLineSize}
     */
    public long countBytesInNextLine() throws IOException {
        return doReadLine(null);
    }

    /**
     * Reads a line of text.  A line is considered to be terminated by any one
     * of a line feed ('\n'), a carriage return ('\r'), a carriage return
     * followed immediately by a line feed, or by reaching the end-of-file
     * (EOF).
     * @return  A String containing the contents of the line, including
     * any line-termination characters, or null if the end of the
     * stream has been reached without reading any characters
     * @throws IOException If an I/O error occurs or the line is not terminated before reading the maximum number bytes as set by {@link BoundedLineReader#maxLineSize}
     */
    @Override
    public String readLine() throws IOException {
        StringBuilder[] stringBuilder = new StringBuilder[]{new StringBuilder()};
        doReadLine(stringBuilder);
        return stringBuilder[0] != null ? stringBuilder[0].toString() : null;
    }

    /**
     * Get the total bytes that have been read by this reader
     * @return number of bytes read
     */
    public long getBytesRead() {
        return bytesRead;
    }

    /**
     * Set the maxLineSize to a new value
     * @param maxLineSize new maxLineSize to use
     */
    public void setMaxLineSize(long maxLineSize) {
        if (maxLineSize < 0) {
            throw new IllegalArgumentException("maxLineSize cannot be negative");
        }

        this.maxLineSize = maxLineSize;
    }

    /*
    This takes an array of StringBuilders so the method can reassign the
    actual StringBuilder to null if EOF is reached, otherwise an empty string
    would be returned.
     */
    private long doReadLine(StringBuilder[] stringBuilder) throws IOException {
        // if the nextChar is already set from '\n' look ahead, use it
        int curChar = nextChar != UNSET ? nextChar : super.read();

        // the EOF was reached
        if (curChar == -1) {
            // reassign the StringBuilder to null to prevent returning an empty string
            if (stringBuilder != null) {
                stringBuilder[0] = null;
            }
            return -1;
        }

        if (stringBuilder != null) {
            stringBuilder[0].append((char) curChar);
        }

        long currentLineSize = 0;
        while (curChar != -1) {
            bytesRead++;
            checkSize(++currentLineSize);

            if (curChar == '\n') {
                return currentLineSize;
            } else if (curChar == '\r') {
                // check if the next character is '\n' if so it should be considered part of the current line
                // otherwise the nextChar will be used on the next doReadLine call instead of reading from the buffer
                nextChar = super.read();
                if (nextChar == '\n') {
                    // include this \n in the bytesRead for the current line and UNSET the nextChar so the next doReadLine reads from the buffer
                    checkSize(++currentLineSize);
                    bytesRead++;
                    nextChar = UNSET;
                }
                return currentLineSize;
            } else {
                curChar = super.read();

                if (stringBuilder != null) {
                    stringBuilder[0].append((char) curChar);
                }
            }
        }

        return currentLineSize;
    }

    private void checkSize(long currentLineSize) throws IOException {
        if (currentLineSize > maxLineSize) {
            throw new IOException(LINE_OVERFLOW);
        }
    }
}
