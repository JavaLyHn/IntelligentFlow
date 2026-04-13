package com.lyhn.coreworkflowjava.workflow.engine.integration.plugins.tts;

import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.List;

@Slf4j
public class WavAudioMerger {

    private static final int WAV_HEADER_SIZE = 44;
    private static final int RIFF_OFFSET = 0;
    private static final int FILE_SIZE_OFFSET = 4;
    private static final int DATA_SIZE_OFFSET = 40;

    public byte[] merge(List<byte[]> audioChunks) {
        if (audioChunks == null || audioChunks.isEmpty()) {
            throw new IllegalArgumentException("No audio chunks to merge");
        }

        if (audioChunks.size() == 1) {
            return audioChunks.get(0);
        }

        log.info("[WavAudioMerger] Merging {} audio chunks", audioChunks.size());

        WavFormat referenceFormat = parseWavHeader(audioChunks.get(0));
        log.debug("[WavAudioMerger] Reference format: sampleRate={}, bitsPerSample={}, channels={}",
                referenceFormat.sampleRate, referenceFormat.bitsPerSample, referenceFormat.channels);

        int totalDataSize = 0;
        for (byte[] chunk : audioChunks) {
            totalDataSize += (chunk.length - WAV_HEADER_SIZE);
        }

        byte[] merged = new byte[WAV_HEADER_SIZE + totalDataSize];

        System.arraycopy(audioChunks.get(0), RIFF_OFFSET, merged, RIFF_OFFSET, WAV_HEADER_SIZE);

        int offset = WAV_HEADER_SIZE;
        for (int i = 0; i < audioChunks.size(); i++) {
            byte[] chunk = audioChunks.get(i);
            int dataSize = chunk.length - WAV_HEADER_SIZE;
            System.arraycopy(chunk, WAV_HEADER_SIZE, merged, offset, dataSize);
            offset += dataSize;

            if (i > 0) {
                WavFormat chunkFormat = parseWavHeader(chunk);
                if (chunkFormat.sampleRate != referenceFormat.sampleRate ||
                        chunkFormat.bitsPerSample != referenceFormat.bitsPerSample ||
                        chunkFormat.channels != referenceFormat.channels) {
                    log.warn("[WavAudioMerger] Chunk {} has different format: sampleRate={}, bitsPerSample={}, channels={}",
                            i, chunkFormat.sampleRate, chunkFormat.bitsPerSample, chunkFormat.channels);
                }
            }
        }

        writeLittleEndianInt(merged, FILE_SIZE_OFFSET, merged.length - 8);
        writeLittleEndianInt(merged, DATA_SIZE_OFFSET, totalDataSize);

        log.info("[WavAudioMerger] Merged audio: totalSize={} bytes, dataSection={} bytes",
                merged.length, totalDataSize);

        return merged;
    }

    private WavFormat parseWavHeader(byte[] wavData) {
        try {
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(wavData));
            byte[] riff = new byte[4];
            dis.readFully(riff);

            dis.skipBytes(4);

            byte[] wave = new byte[4];
            dis.readFully(wave);

            byte[] fmt = new byte[4];
            dis.readFully(fmt);

            int fmtSize = readLittleEndianInt(wavData, 16);
            dis.skipBytes(fmtSize);

            byte[] dataMarker = new byte[4];
            dis.readFully(dataMarker);

            int channels = readLittleEndianShort(wavData, 22);
            int sampleRate = readLittleEndianInt(wavData, 24);
            int bitsPerSample = readLittleEndianShort(wavData, 34);

            return new WavFormat(channels, sampleRate, bitsPerSample);

        } catch (IOException e) {
            log.warn("[WavAudioMerger] Failed to parse WAV header, using defaults");
            return new WavFormat(1, 24000, 16);
        }
    }

    private int readLittleEndianInt(byte[] data, int offset) {
        return (data[offset] & 0xFF) |
                ((data[offset + 1] & 0xFF) << 8) |
                ((data[offset + 2] & 0xFF) << 16) |
                ((data[offset + 3] & 0xFF) << 24);
    }

    private int readLittleEndianShort(byte[] data, int offset) {
        return (data[offset] & 0xFF) |
                ((data[offset + 1] & 0xFF) << 8);
    }

    private void writeLittleEndianInt(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) ((value >> 8) & 0xFF);
        data[offset + 2] = (byte) ((value >> 16) & 0xFF);
        data[offset + 3] = (byte) ((value >> 24) & 0xFF);
    }

    private record WavFormat(int channels, int sampleRate, int bitsPerSample) {}
}
