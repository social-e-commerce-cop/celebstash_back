package com.celebstash.backend.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class AudioPreviewUtilTest {

    /** MPEG-1 Layer III, 128 kbps, 44.1 kHz: 417-byte frames of 1152 samples (~26 ms each). */
    private static byte[] mp3(int seconds, boolean withId3) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (withId3) {
            out.writeBytes(new byte[]{'I', 'D', '3', 4, 0, 0, 0, 0, 0, 20});
            out.writeBytes(new byte[20]);
        }
        int frames = (int) Math.ceil(seconds * 44100.0 / 1152);
        for (int i = 0; i < frames; i++) {
            byte[] frame = new byte[417];
            frame[0] = (byte) 0xFF;
            frame[1] = (byte) 0xFB;
            frame[2] = (byte) 0x90;
            frame[3] = (byte) 0x00;
            out.writeBytes(frame);
        }
        return out.toByteArray();
    }

    /** 8 kHz mono 16-bit PCM WAV. */
    private static byte[] wav(int seconds) {
        int byteRate = 8000 * 2;
        int dataLength = byteRate * seconds;
        ByteBuffer b = ByteBuffer.allocate(44 + dataLength).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + dataLength).put("WAVE".getBytes(StandardCharsets.US_ASCII));
        b.put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(8000).putInt(byteRate).putShort((short) 2).putShort((short) 16);
        b.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(dataLength);
        return b.array();
    }

    /** AAC-LC in ADTS framing, 44.1 kHz, one raw block (1024 samples) per 200-byte frame. */
    private static byte[] aac(int seconds) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int frames = (int) Math.ceil(seconds * 44100.0 / 1024);
        int len = 200;
        for (int i = 0; i < frames; i++) {
            byte[] frame = new byte[len];
            frame[0] = (byte) 0xFF;
            frame[1] = (byte) 0xF1;
            frame[2] = (byte) ((1 << 6) | (4 << 2)); // profile LC, sampling index 4 (44.1 kHz)
            frame[3] = (byte) ((2 << 6) | ((len >> 11) & 0x03));
            frame[4] = (byte) ((len >> 3) & 0xFF);
            frame[5] = (byte) (((len & 0x07) << 5) | 0x1F);
            frame[6] = (byte) 0xFC;
            out.writeBytes(frame);
        }
        return out.toByteArray();
    }

    @Test
    void detectsSupportedFormatsFromBytes() {
        assertEquals(AudioPreviewUtil.AudioFormat.MP3, AudioPreviewUtil.detectFormat(mp3(3, true)));
        assertEquals(AudioPreviewUtil.AudioFormat.WAV, AudioPreviewUtil.detectFormat(wav(3)));
        assertEquals(AudioPreviewUtil.AudioFormat.AAC, AudioPreviewUtil.detectFormat(aac(3)));
    }

    @Test
    void rejectsNonAudioAndContainerFormats() {
        assertNull(AudioPreviewUtil.detectFormat("not audio at all, just text".getBytes(StandardCharsets.UTF_8)));
        byte[] m4a = new byte[64];
        System.arraycopy("    ftypM4A ".getBytes(StandardCharsets.US_ASCII), 0, m4a, 0, 12);
        assertNull(AudioPreviewUtil.detectFormat(m4a));
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        assertNull(AudioPreviewUtil.detectFormat(png));
    }

    @Test
    void measuresDuration() {
        assertEquals(30, AudioPreviewUtil.durationSeconds(mp3(30, true)));
        assertEquals(30, AudioPreviewUtil.durationSeconds(wav(30)));
        assertEquals(30, AudioPreviewUtil.durationSeconds(aac(30)));
    }

    @Test
    void mp3PreviewContainsOnlyFiveSeconds() {
        byte[] full = mp3(180, true);
        byte[] preview = AudioPreviewUtil.trimToSeconds(full, 5);

        assertEquals(AudioPreviewUtil.AudioFormat.MP3, AudioPreviewUtil.detectFormat(preview));
        assertEquals(5, AudioPreviewUtil.durationSeconds(preview));
        assertTrue(preview.length < full.length / 30, "preview must be a small fraction of the full track");
    }

    @Test
    void wavPreviewIsAValidFiveSecondFile() {
        byte[] full = wav(60);
        byte[] preview = AudioPreviewUtil.trimToSeconds(full, 5);

        assertEquals(AudioPreviewUtil.AudioFormat.WAV, AudioPreviewUtil.detectFormat(preview));
        assertEquals(5, AudioPreviewUtil.durationSeconds(preview));
        assertEquals(44 + 5 * 16000, preview.length);
        int riffSize = ByteBuffer.wrap(preview, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        assertEquals(preview.length - 8, riffSize, "RIFF size must describe the trimmed file");
    }

    @Test
    void aacPreviewContainsOnlyFiveSeconds() {
        byte[] preview = AudioPreviewUtil.trimToSeconds(aac(120), 5);
        assertEquals(AudioPreviewUtil.AudioFormat.AAC, AudioPreviewUtil.detectFormat(preview));
        assertEquals(5, AudioPreviewUtil.durationSeconds(preview));
    }

    @Test
    void shortTrackPreviewIsTheWholeTrack() {
        byte[] full = mp3(3, false);
        assertArrayEquals(full, AudioPreviewUtil.trimToSeconds(full, 5));
    }

    @Test
    void trimmingUnsupportedBytesFails() {
        assertThrows(IllegalArgumentException.class,
                () -> AudioPreviewUtil.trimToSeconds("plain text".getBytes(StandardCharsets.UTF_8), 5));
    }
}
