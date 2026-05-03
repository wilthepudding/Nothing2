package com.example.robloxpianoauto;

import java.io.ByteArrayOutputStream;
import java.util.*;

public class MidiSongParser {
    private static class NoteOn {
        long tick;
        int note;
        NoteOn(long tick, int note) { this.tick = tick; this.note = note; }
    }
    private static class TempoChange {
        long tick;
        int microsPerQuarter;
        TempoChange(long tick, int microsPerQuarter) { this.tick = tick; this.microsPerQuarter = microsPerQuarter; }
    }
    private static class ParseResult {
        int division = 480;
        ArrayList<NoteOn> notes = new ArrayList<>();
        ArrayList<TempoChange> tempos = new ArrayList<>();
    }

    private final byte[] data;
    private int pos = 0;

    private MidiSongParser(byte[] data) { this.data = data; }

    public static boolean isMidi(byte[] bytes) {
        return bytes != null && bytes.length >= 4 && bytes[0] == 'M' && bytes[1] == 'T' && bytes[2] == 'h' && bytes[3] == 'd';
    }

    public static String convertToSongText(byte[] bytes) throws Exception {
        MidiSongParser p = new MidiSongParser(bytes);
        ParseResult r = p.parse();
        return buildSongText(r);
    }

    private ParseResult parse() throws Exception {
        ParseResult result = new ParseResult();
        expect("MThd");
        int headerLen = readInt();
        int format = readShort();
        int tracks = readShort();
        int division = readShort();
        result.division = division;
        pos += Math.max(0, headerLen - 6);

        if ((division & 0x8000) != 0) {
            throw new Exception("MIDI SMPTE não suportado neste app.");
        }
        if (format < 0 || tracks <= 0) throw new Exception("Arquivo MIDI inválido.");

        for (int i = 0; i < tracks && pos < data.length; i++) {
            if (!peek("MTrk")) break;
            expect("MTrk");
            int len = readInt();
            int end = Math.min(data.length, pos + len);
            parseTrack(end, result);
            pos = end;
        }
        if (result.notes.isEmpty()) throw new Exception("Não encontrei notas nesse MIDI.");
        return result;
    }

    private void parseTrack(int end, ParseResult result) throws Exception {
        long tick = 0;
        int runningStatus = -1;
        while (pos < end) {
            tick += readVarLen(end);
            int b = readByte(end);
            int status;
            int firstData = -1;
            if ((b & 0x80) != 0) {
                status = b;
                if (status < 0xF0) runningStatus = status;
            } else {
                // Alguns MIDIs exportados pelo Online Sequencer aparecem com eventos
                // estranhos/sistema no meio da trilha. Em vez de falhar com
                // "running status inválido", ignoramos o byte solto e seguimos tentando
                // recuperar as próximas notas válidas.
                if (runningStatus < 0) {
                    continue;
                }
                status = runningStatus;
                firstData = b;
            }

            if (status == 0xFF) {
                int type = readByte(end);
                int len = readVarLen(end);
                if (type == 0x51 && len == 3 && pos + 3 <= end) {
                    int tempo = ((data[pos] & 0xFF) << 16) | ((data[pos + 1] & 0xFF) << 8) | (data[pos + 2] & 0xFF);
                    result.tempos.add(new TempoChange(tick, tempo));
                }
                pos = Math.min(end, pos + len);
                if (type == 0x2F) return;
                continue;
            }
            if (status == 0xF0 || status == 0xF7) {
                int len = readVarLen(end);
                pos = Math.min(end, pos + len);
                runningStatus = -1;
                continue;
            }

            // Eventos de sistema que não são notas. Se não tratarmos isso, o parser
            // consome bytes errados e depois acusa running status inválido em MIDIs
            // perfeitamente baixados.
            if (status >= 0xF0) {
                int systemLen;
                switch (status) {
                    case 0xF1:
                    case 0xF3:
                        systemLen = 1;
                        break;
                    case 0xF2:
                        systemLen = 2;
                        break;
                    default:
                        systemLen = 0;
                        break;
                }
                pos = Math.min(end, pos + systemLen);
                runningStatus = -1;
                continue;
            }

            int high = status & 0xF0;
            int channel = status & 0x0F;
            int dataLen = (high == 0xC0 || high == 0xD0) ? 1 : 2;
            int d1 = firstData >= 0 ? firstData : readByte(end);
            int d2 = dataLen == 2 ? readByte(end) : 0;

            // Canal 10/índice 9 geralmente é bateria. Ignora para não bagunçar o piano.
            if (channel == 9) continue;

            // Note On com velocidade > 0.
            if (high == 0x90 && d2 > 0) {
                result.notes.add(new NoteOn(tick, d1));
            }
        }
    }

    private static String buildSongText(ParseResult r) throws Exception {
        Collections.sort(r.tempos, (a, b) -> Long.compare(a.tick, b.tick));
        if (r.tempos.isEmpty() || r.tempos.get(0).tick != 0) {
            r.tempos.add(0, new TempoChange(0, 500000));
        }
        Collections.sort(r.notes, (a, b) -> Long.compare(a.tick, b.tick));

        TreeMap<Long, LinkedHashSet<String>> groups = new TreeMap<>();
        for (NoteOn n : r.notes) {
            String key = noteToKey(n.note);
            if (key == null) continue;
            long ms = tickToMs(n.tick, r.division, r.tempos);
            // Agrupa notas quase simultâneas para formar acordes.
            ms = Math.round(ms / 15.0) * 15L;
            groups.computeIfAbsent(ms, k -> new LinkedHashSet<>()).add(key);
        }
        if (groups.isEmpty()) throw new Exception("Nenhuma nota do MIDI coube no alcance do piano.");

        ArrayList<Long> times = new ArrayList<>(groups.keySet());
        StringBuilder out = new StringBuilder();
        out.append("# Convertido de MIDI pelo JJSAP\n");
        out.append("# As notas foram dobradas por oitava para caber nas teclas 1 2 3 4 5 6 7 8 9 0 Q E R T Y U P.\n");

        long first = times.get(0);
        if (first > 80) out.append("-:").append(Math.min(first, 3000)).append('\n');

        for (int i = 0; i < times.size(); i++) {
            long now = times.get(i);
            long next = (i + 1 < times.size()) ? times.get(i + 1) : now + 500;
            long wait = next - now;
            if (wait < 45) wait = 45;
            if (wait > 5000) wait = 5000;

            LinkedHashSet<String> set = groups.get(now);
            String chord = chordText(set);
            out.append(chord).append(':').append(wait).append('\n');
        }
        return out.toString();
    }

    private static String chordText(LinkedHashSet<String> set) {
        if (set.size() == 1) return set.iterator().next();
        StringBuilder sb = new StringBuilder("[");
        for (String k : set) sb.append(k);
        sb.append(']');
        return sb.toString();
    }

    private static long tickToMs(long tick, int division, ArrayList<TempoChange> tempos) {
        long micros = 0;
        long lastTick = 0;
        int tempo = 500000;
        for (TempoChange tc : tempos) {
            if (tc.tick <= 0) { tempo = tc.microsPerQuarter; continue; }
            if (tc.tick >= tick) break;
            micros += ((tc.tick - lastTick) * (long) tempo) / division;
            lastTick = tc.tick;
            tempo = tc.microsPerQuarter;
        }
        micros += ((tick - lastTick) * (long) tempo) / division;
        return micros / 1000;
    }

    private static String noteToKey(int midiNote) {
        // Piano mapeado como C4 até E5, usando as pretas da imagem.
        while (midiNote < 60) midiNote += 12;
        while (midiNote > 76) midiNote -= 12;
        switch (midiNote) {
            case 60: return "1"; // C
            case 61: return "Q"; // C#
            case 62: return "2"; // D
            case 63: return "E"; // D#
            case 64: return "3"; // E
            case 65: return "4"; // F
            case 66: return "R"; // F#
            case 67: return "5"; // G
            case 68: return "T"; // G#
            case 69: return "6"; // A
            case 70: return "Y"; // A#
            case 71: return "7"; // B
            case 72: return "8"; // C
            case 73: return "U"; // C#
            case 74: return "9"; // D
            case 75: return "P"; // D#
            case 76: return "0"; // E
            default: return null;
        }
    }

    private boolean peek(String s) {
        if (pos + s.length() > data.length) return false;
        for (int i = 0; i < s.length(); i++) if (data[pos + i] != (byte) s.charAt(i)) return false;
        return true;
    }
    private void expect(String s) throws Exception {
        if (!peek(s)) throw new Exception("Esperado " + s + " no MIDI.");
        pos += s.length();
    }
    private int readByte(int end) throws Exception {
        if (pos >= end || pos >= data.length) throw new Exception("Fim inesperado do MIDI.");
        return data[pos++] & 0xFF;
    }
    private int readShort() throws Exception {
        if (pos + 2 > data.length) throw new Exception("Fim inesperado do MIDI.");
        int v = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
        pos += 2;
        return v;
    }
    private int readInt() throws Exception {
        if (pos + 4 > data.length) throw new Exception("Fim inesperado do MIDI.");
        int v = ((data[pos] & 0xFF) << 24) | ((data[pos + 1] & 0xFF) << 16) | ((data[pos + 2] & 0xFF) << 8) | (data[pos + 3] & 0xFF);
        pos += 4;
        return v;
    }
    private int readVarLen(int end) throws Exception {
        int value = 0;
        for (int i = 0; i < 4; i++) {
            int b = readByte(end);
            value = (value << 7) | (b & 0x7F);
            if ((b & 0x80) == 0) return value;
        }
        return value;
    }
}
