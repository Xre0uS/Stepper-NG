package com.xreous.stepperng.util;

import java.io.*;
import java.util.*;


// Based on https://stackoverflow.com/a/11158499 by aioobe
public class ReplacingInputStream extends FilterInputStream {

    ArrayList<Integer> inQueue = new ArrayList<>();
    ArrayDeque<Integer> bufferQueue = new ArrayDeque<>();
    ArrayDeque<Integer> outQueue = new ArrayDeque<>();
    final List<Replacement> replacements;

    public ReplacingInputStream(InputStream in,
                                   List<Replacement> replacements) {
        super(in);
        this.replacements = replacements;
    }

    private void readAhead() throws IOException {
        if (replacements.isEmpty()) {
            int next = super.read();
            if (next != -1) outQueue.offer(next);
            return;
        }
        List<Replacement> potentialMatches = new ArrayList<>(replacements);
        do{
            int position = inQueue.size();
            int next = !bufferQueue.isEmpty() ? bufferQueue.poll() : super.read();
            inQueue.add(next);
            if(next == -1) break;
            potentialMatches.removeIf(potential ->
                    potential.match.length < inQueue.size() || next != potential.match[position]
            );

            // Check if any potential match is now fully matched (all bytes verified by the filter above)
            Replacement fullMatch = null;
            int inSize = inQueue.size();
            for (Replacement potential : potentialMatches) {
                if (potential.match.length == inSize) {
                    fullMatch = potential;
                    break;
                }
            }

            if(fullMatch != null){
                inQueue.clear();
                byte[] replacement = fullMatch.replace;
                for (byte b : replacement) {
                    outQueue.offer((int) b);
                }
                return;
            }
        } while (!potentialMatches.isEmpty());

        if(!inQueue.isEmpty())
            outQueue.offer(inQueue.remove(0));
        while (!inQueue.isEmpty())
            bufferQueue.offer(inQueue.remove(0));

    }

    @Override
    public int read() throws IOException {
        if (outQueue.isEmpty()) {
            readAhead();
        }

        if (outQueue.isEmpty()) {
            return -1;
        }
        return outQueue.poll();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (b == null) throw new NullPointerException();
        if (off < 0 || len < 0 || len > b.length - off) throw new IndexOutOfBoundsException();
        if (len == 0) return 0;
        int count = 0;
        while (count < len) {
            int val = read();
            if (val == -1) break;
            b[off + count] = (byte) val;
            count++;
        }
        return count == 0 ? -1 : count;
    }

    public static class Replacement {
        public final byte[] match, replace;
        public Replacement(byte[] match, byte[] replace){
            this.match = match;
            this.replace = replace;
        }

        @Override
        public String toString() {
            return "Replacement{" +
                    "match=" + new String(match) +
                    ", replace=" + new String(replace) +
                    '}';
        }
    }
}