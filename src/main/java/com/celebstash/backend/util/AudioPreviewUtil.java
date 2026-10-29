package com.celebstash.backend.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Detects uploaded audio formats and cuts a genuine N-second preview out of them on the server.
 *
 * <p>Previews must never be the full file with a client-side timer on top — anyone could read the
 * whole stream. Only the formats that can be cut on a frame boundary without re-encoding are
 * accepted for music uploads: MP3 (MPEG audio frames), AAC in ADTS framing (.aac) and PCM WAV.
 * Container formats such as M4A, FLAC or OGG need a demuxer to trim, so they are rejected at upload
 * time instead of being served unprotected.
 */
public final class AudioPreviewUtil {

    public enum AudioFormat {
        MP3("audio/mpeg", ".mp3"),
        AAC("audio/aac", ".aac"),
        WAV("audio/wav", ".wav");

        private final String contentType;
        private final String extension;

        AudioFormat(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }

        public String getContentType() {
            return contentType;
        }

        public String getExtension() {
            return extension;
        }
    }

    // MPEG audio bitrate tables (kbps), indexed [versionGroup][layer][bitrateIndex].
    // versionGroup 0 = MPEG-1, 1 = MPEG-2/2.5. layer index 1..3 = Layer I..III.
    private static final int[][][] BITRATES = {
            {
                    {},
                    {0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448, -1},
                    {0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384, -1},
                    {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, -1},
            },
            {
                    {},
                    {0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256, -1},
                    {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, -1},
                    {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, -1},
            },
    };

    private static final int[][] MPEG_SAMPLE_RATES = {
            {44100, 48000, 32000}, // MPEG-1
            {22050, 24000, 16000}, // MPEG-2
            {11025, 12000, 8000},  // MPEG-2.5
    };

    private static final int[] ADTS_SAMPLE_RATES = {
            96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350,
    };

    private AudioPreviewUtil() {
    }

    /** Identifies the audio format from the file's bytes; returns null when it is not supported. */
    public static AudioFormat detectFormat(byte[] data) {
        if (data == null || data.length < 12) {
            return null;
        }
        if (startsWith(data, 0, "RIFF") && startsWith(data, 8, "WAVE")) {
            return WavInfo.parse(data) != null ? AudioFormat.WAV : null;
        }
        int offset = skipId3v2(data);
        int sync = findFrameSync(data, offset);
        if (sync < 0) {
            return null;
        }
        if (isAdtsHeader(data, sync)) {
            return AudioFormat.AAC;
        }
        if (mpegFrameLength(data, sync) > 0) {
            return AudioFormat.MP3;
        }
        return null;
    }

    /** Duration of the whole file in seconds, or null when it cannot be determined. */
    public static Integer durationSeconds(byte[] data) {
        AudioFormat format = detectFormat(data);
        if (format == null) {
            return null;
        }
        double seconds = switch (format) {
            case WAV -> {
                WavInfo wav = WavInfo.parse(data);
                yield wav.byteRate > 0 ? (double) wav.availableDataLength(data) / wav.byteRate : 0;
            }
            case MP3 -> scanMpeg(data, Double.MAX_VALUE).seconds;
            case AAC -> scanAdts(data, Double.MAX_VALUE).seconds;
        };
        return seconds > 0 ? (int) Math.round(seconds) : null;
    }

    /**
     * Returns a new, independently playable file holding only the first {@code seconds} seconds.
     *
     * @throws IllegalArgumentException when the bytes are not a supported audio format
     */
    public static byte[] trimToSeconds(byte[] data, int seconds) {
        AudioFormat format = detectFormat(data);
        if (format == null) {
            throw new IllegalArgumentException("Unsupported audio format; a preview cannot be generated.");
        }
        double limit = Math.max(1, seconds);
        return switch (format) {
            case WAV -> trimWav(data, limit);
            case MP3 -> {
                int start = findFrameSync(data, skipId3v2(data));
                yield Arrays.copyOfRange(data, start, scanMpeg(data, limit).endOffset);
            }
            case AAC -> {
                int start = findFrameSync(data, skipId3v2(data));
                yield Arrays.copyOfRange(data, start, scanAdts(data, limit).endOffset);
            }
        };
    }

    // ---------------------------------------------------------------------------------------------
    // MPEG audio (MP3)

    private record ScanResult(int endOffset, double seconds) {
    }

    private static ScanResult scanMpeg(byte[] data, double limitSeconds) {
        int pos = findFrameSync(data, skipId3v2(data));
        double elapsed = 0;
        while (pos >= 0 && pos + 4 <= data.length && elapsed < limitSeconds) {
            int frameLength = mpegFrameLength(data, pos);
            if (frameLength <= 0) {
                // Lost sync (padding, trailing ID3v1 tag, junk) — try to resynchronise.
                int next = findFrameSync(data, pos + 1);
                if (next < 0) {
                    break;
                }
                pos = next;
                continue;
            }
            if (pos + frameLength > data.length) {
                break;
            }
            elapsed += mpegFrameSeconds(data, pos);
            pos += frameLength;
        }
        return new ScanResult(Math.min(Math.max(pos, 0), data.length), elapsed);
    }

    /** Frame length in bytes for a valid MPEG audio header at {@code pos}, otherwise -1. */
    private static int mpegFrameLength(byte[] data, int pos) {
        if (pos + 4 > data.length) {
            return -1;
        }
        int b1 = data[pos + 1] & 0xFF;
        int b2 = data[pos + 2] & 0xFF;
        if ((data[pos] & 0xFF) != 0xFF || (b1 & 0xE0) != 0xE0) {
            return -1;
        }
        int versionBits = (b1 >> 3) & 0x03; // 00 = 2.5, 01 = reserved, 10 = 2, 11 = 1
        int layerBits = (b1 >> 1) & 0x03;   // 01 = III, 10 = II, 11 = I, 00 = reserved (ADTS)
        int bitrateIndex = (b2 >> 4) & 0x0F;
        int sampleRateIndex = (b2 >> 2) & 0x03;
        int padding = (b2 >> 1) & 0x01;
        if (versionBits == 1 || layerBits == 0 || bitrateIndex == 0 || bitrateIndex == 15 || sampleRateIndex == 3) {
            return -1;
        }
        int layer = 4 - layerBits; // 1..3
        boolean mpeg1 = versionBits == 3;
        int bitrate = BITRATES[mpeg1 ? 0 : 1][layer][bitrateIndex] * 1000;
        int sampleRate = MPEG_SAMPLE_RATES[mpeg1 ? 0 : (versionBits == 2 ? 1 : 2)][sampleRateIndex];
        if (bitrate <= 0) {
            return -1;
        }
        if (layer == 1) {
            return (12 * bitrate / sampleRate + padding) * 4;
        }
        int coefficient = (layer == 3 && !mpeg1) ? 72 : 144;
        return coefficient * bitrate / sampleRate + padding;
    }

    private static double mpegFrameSeconds(byte[] data, int pos) {
        int b1 = data[pos + 1] & 0xFF;
        int b2 = data[pos + 2] & 0xFF;
        int versionBits = (b1 >> 3) & 0x03;
        int layer = 4 - ((b1 >> 1) & 0x03);
        boolean mpeg1 = versionBits == 3;
        int sampleRate = MPEG_SAMPLE_RATES[mpeg1 ? 0 : (versionBits == 2 ? 1 : 2)][(b2 >> 2) & 0x03];
        int samples = layer == 1 ? 384 : (layer == 3 && !mpeg1 ? 576 : 1152);
        return (double) samples / sampleRate;
    }

    // ---------------------------------------------------------------------------------------------
    // AAC in ADTS framing

    private static boolean isAdtsHeader(byte[] data, int pos) {
        if (pos + 7 > data.length) {
            return false;
        }
        int b1 = data[pos + 1] & 0xFF;
        return (data[pos] & 0xFF) == 0xFF && (b1 & 0xF6) == 0xF0 && adtsFrameLength(data, pos) > 7;
    }

    private static int adtsFrameLength(byte[] data, int pos) {
        return ((data[pos + 3] & 0x03) << 11) | ((data[pos + 4] & 0xFF) << 3) | ((data[pos + 5] & 0xE0) >> 5);
    }

    private static ScanResult scanAdts(byte[] data, double limitSeconds) {
        int pos = findFrameSync(data, skipId3v2(data));
        double elapsed = 0;
        while (pos >= 0 && pos + 7 <= data.length && elapsed < limitSeconds) {
            if (!isAdtsHeader(data, pos)) {
                int next = findFrameSync(data, pos + 1);
                if (next < 0) {
                    break;
                }
                pos = next;
                continue;
            }
            int frameLength = adtsFrameLength(data, pos);
            if (pos + frameLength > data.length) {
                break;
            }
            int sampleRateIndex = ((data[pos + 2] & 0xFF) >> 2) & 0x0F;
            int blocks = (data[pos + 6] & 0x03) + 1;
            if (sampleRateIndex < ADTS_SAMPLE_RATES.length) {
                elapsed += (1024.0 * blocks) / ADTS_SAMPLE_RATES[sampleRateIndex];
            }
            pos += frameLength;
        }
        return new ScanResult(Math.min(Math.max(pos, 0), data.length), elapsed);
    }

    // ---------------------------------------------------------------------------------------------
    // WAV

    private static final class WavInfo {
        int byteRate;
        int blockAlign;
        int dataOffset;
        int dataLength;
        int fmtOffset;
        int fmtLength;

        static WavInfo parse(byte[] data) {
            WavInfo info = new WavInfo();
            int pos = 12;
            while (pos + 8 <= data.length) {
                String id = new String(data, pos, 4, java.nio.charset.StandardCharsets.US_ASCII);
                int size = ByteBuffer.wrap(data, pos + 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                if (size < 0) {
                    return null;
                }
                if ("fmt ".equals(id) && pos + 8 + 16 <= data.length) {
                    ByteBuffer fmt = ByteBuffer.wrap(data, pos + 8, 16).order(ByteOrder.LITTLE_ENDIAN);
                    fmt.getShort(); // audio format
                    fmt.getShort(); // channels
                    fmt.getInt();   // sample rate
                    info.byteRate = fmt.getInt();
                    info.blockAlign = fmt.getShort();
                    info.fmtOffset = pos;
                    info.fmtLength = 8 + size + (size & 1);
                } else if ("data".equals(id)) {
                    info.dataOffset = pos + 8;
                    info.dataLength = size;
                    break;
                }
                pos += 8 + size + (size & 1);
            }
            if (info.byteRate <= 0 || info.dataOffset == 0 || info.fmtLength == 0) {
                return null;
            }
            return info;
        }

        int availableDataLength(byte[] data) {
            return Math.max(0, Math.min(dataLength, data.length - dataOffset));
        }
    }

    private static byte[] trimWav(byte[] data, double seconds) {
        WavInfo wav = WavInfo.parse(data);
        long wanted = (long) (wav.byteRate * seconds);
        int align = Math.max(1, wav.blockAlign);
        int length = (int) Math.min(wav.availableDataLength(data), wanted - (wanted % align));

        // RIFF header + the original fmt chunk + a data chunk sized to the preview.
        ByteBuffer out = ByteBuffer.allocate(12 + wav.fmtLength + 8 + length).order(ByteOrder.LITTLE_ENDIAN);
        out.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        out.putInt(4 + wav.fmtLength + 8 + length);
        out.put("WAVE".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        out.put(data, wav.fmtOffset, Math.min(wav.fmtLength, data.length - wav.fmtOffset));
        out.position(12 + wav.fmtLength);
        out.put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        out.putInt(length);
        out.put(data, wav.dataOffset, length);
        return out.array();
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers

    private static int skipId3v2(byte[] data) {
        if (data.length >= 10 && startsWith(data, 0, "ID3")) {
            int size = ((data[6] & 0x7F) << 21) | ((data[7] & 0x7F) << 14) | ((data[8] & 0x7F) << 7) | (data[9] & 0x7F);
            boolean footer = (data[5] & 0x10) != 0;
            return Math.min(data.length, 10 + size + (footer ? 10 : 0));
        }
        return 0;
    }

    private static int findFrameSync(byte[] data, int from) {
        for (int i = Math.max(0, from); i + 1 < data.length; i++) {
            if ((data[i] & 0xFF) == 0xFF && (data[i + 1] & 0xE0) == 0xE0) {
                if (mpegFrameLength(data, i) > 0 || isAdtsHeader(data, i)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static boolean startsWith(byte[] data, int offset, String ascii) {
        if (data.length < offset + ascii.length()) {
            return false;
        }
        for (int i = 0; i < ascii.length(); i++) {
            if (data[offset + i] != (byte) ascii.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
