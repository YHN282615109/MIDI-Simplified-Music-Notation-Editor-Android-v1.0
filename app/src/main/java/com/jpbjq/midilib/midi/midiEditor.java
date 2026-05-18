package com.jpbjq.midilib.midi;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import com.jpbjq.midi.R;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class midiEditor extends AppCompatActivity {

    static {
        System.loadLibrary("native-lib");
    }

    EditText[] notation_editors = new EditText[16];
    ImageButton play_button;
    ImageButton stop_button;
    Button export_button;
    CheckBox loop_checkbox;
    Button[] track_buttons = new Button[16];
    Handler handler = new Handler();
    NotationPlayer notationPlayer;
    int currentTrack = 0;

    private static final int REQUEST_WRITE_PERMISSION = 1001;
    private boolean isExportRequested = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_midi_editor);
        setTitle("16音轨简谱编辑器");

        initializeViews();
        setupTrackButtons();
        setupPlaybackControls();
        setupExportButton();
        setDefaultNotation();
    }

    private void initializeViews() {
        play_button = findViewById(R.id.midieditor_play);
        stop_button = findViewById(R.id.midieditor_stop);
        export_button = findViewById(R.id.export_button);
        loop_checkbox = findViewById(R.id.loop_checkbox);

        int[] notationEditorIds = {
                R.id.notation_editor_1, R.id.notation_editor_2, R.id.notation_editor_3, R.id.notation_editor_4,
                R.id.notation_editor_5, R.id.notation_editor_6, R.id.notation_editor_7, R.id.notation_editor_8,
                R.id.notation_editor_9, R.id.notation_editor_10, R.id.notation_editor_11, R.id.notation_editor_12,
                R.id.notation_editor_13, R.id.notation_editor_14, R.id.notation_editor_15, R.id.notation_editor_16
        };

        for (int i = 0; i < 16; i++) {
            notation_editors[i] = findViewById(notationEditorIds[i]);
        }

        int[] trackButtonIds = {
                R.id.track_btn_1, R.id.track_btn_2, R.id.track_btn_3, R.id.track_btn_4,
                R.id.track_btn_5, R.id.track_btn_6, R.id.track_btn_7, R.id.track_btn_8,
                R.id.track_btn_9, R.id.track_btn_10, R.id.track_btn_11, R.id.track_btn_12,
                R.id.track_btn_13, R.id.track_btn_14, R.id.track_btn_15, R.id.track_btn_16
        };

        for (int i = 0; i < 16; i++) {
            track_buttons[i] = findViewById(trackButtonIds[i]);
        }

        notationPlayer = new NotationPlayer(this);
    }

    private void setupTrackButtons() {
        for (int i = 0; i < 16; i++) {
            final int trackIndex = i;
            track_buttons[i].setOnClickListener(v -> showTrack(trackIndex));
        }
        showTrack(0);
    }

    private void setupPlaybackControls() {
        play_button.setOnClickListener(v -> playAllTracks());
        stop_button.setOnClickListener(v -> {
            notationPlayer.stopPlayback();
            Toast.makeText(midiEditor.this, "播放已停止", Toast.LENGTH_SHORT).show();
        });
    }

    private void setupExportButton() {
        export_button.setOnClickListener(v -> requestExportPermission());
    }

    private void setDefaultNotation() {
        // 示例简谱，展示各种语法功能
        notation_editors[0].setText("[0,320,C大调]5-351'--76-1'-5---5-123-212--5-351'--76-1'-5---5-234--7.1--");

        // 清空其他音轨
        for (int i = 1; i < 16; i++) {
            notation_editors[i].setText("");
        }
    }

    private void showTrack(int trackIndex) {
        for (int i = 0; i < 16; i++) {
            notation_editors[i].setVisibility(View.GONE);
        }
        notation_editors[trackIndex].setVisibility(View.VISIBLE);
        currentTrack = trackIndex;

        for (int i = 0; i < 16; i++) {
            if (i == trackIndex) {
                track_buttons[i].setBackgroundColor(0xFFFF0000);
                track_buttons[i].setTextColor(0xFFFFFFFF);
            } else {
                track_buttons[i].setBackgroundColor(0x00000000);
                track_buttons[i].setTextColor(0xFF000000);
            }
        }
    }

    private void playAllTracks() {
        notationPlayer.stopPlayback();

        boolean hasContent = false;
        for (int i = 0; i < 16; i++) {
            String notation = notation_editors[i].getText().toString().trim();
            if (!notation.isEmpty()) {
                hasContent = true;
                break;
            }
        }

        if (!hasContent) {
            Toast.makeText(this, "请至少在一个音轨中输入简谱", Toast.LENGTH_SHORT).show();
            return;
        }

        for (int i = 0; i < 16; i++) {
            String notation = notation_editors[i].getText().toString().trim();
            if (!notation.isEmpty()) {
                notationPlayer.parseNotation(notation, i);
            } else {
                notationPlayer.clearTrack(i);
            }
        }

        notationPlayer.startPlayback(loop_checkbox.isChecked());
        Toast.makeText(this, "开始播放所有音轨", Toast.LENGTH_SHORT).show();
    }

    private void requestExportPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportToMidi();
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                isExportRequested = true;
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_WRITE_PERMISSION);
            } else {
                exportToMidi();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_WRITE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (isExportRequested) {
                    exportToMidi();
                    isExportRequested = false;
                }
            } else {
                Toast.makeText(this, "需要存储权限才能导出文件", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void exportToMidi() {
        boolean hasContent = false;
        for (int i = 0; i < 16; i++) {
            String notation = notation_editors[i].getText().toString().trim();
            if (!notation.isEmpty()) {
                hasContent = true;
                break;
            }
        }

        if (!hasContent) {
            Toast.makeText(this, "请至少在一个音轨中输入简谱", Toast.LENGTH_SHORT).show();
            return;
        }

        for (int i = 0; i < 16; i++) {
            String notation = notation_editors[i].getText().toString().trim();
            if (!notation.isEmpty()) {
                notationPlayer.parseNotation(notation, i);
            } else {
                notationPlayer.clearTrack(i);
            }
        }

        try {
            File midiFile;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                midiFile = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC),
                        "composition_" + System.currentTimeMillis() + ".mid");
            } else {
                File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
                if (!musicDir.exists()) {
                    musicDir.mkdirs();
                }
                midiFile = new File(musicDir, "composition_" + System.currentTimeMillis() + ".mid");
            }

            MidiExporter.exportToMidi(notationPlayer.noteEventsList, notationPlayer.trackInstruments,
                    notationPlayer.trackTempos, midiFile.getAbsolutePath());

            Toast.makeText(this, "MIDI文件已导出到: " + midiFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
//            shareMidiFile(midiFile);
        } catch (Exception e) {
            Toast.makeText(this, "导出失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

//    private void shareMidiFile(File midiFile) {
//        try {
//            Uri fileUri = FileProvider.getUriForFile(this,
//                    getApplicationContext().getPackageName() + ".provider",
//                    midiFile);
//
//            Intent shareIntent = new Intent(Intent.ACTION_SEND);
//            shareIntent.setType("audio/midi");
//            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
//            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
//
//            startActivity(Intent.createChooser(shareIntent, "分享MIDI文件"));
//        } catch (Exception e) {
//            Toast.makeText(this, "分享失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
//        }
//    }

    class NotationPlayer {
        private midiViewer viewer;
        private Handler handler;
        List<List<NoteEvent>> noteEventsList;
        private boolean looping;
        private boolean isPlaying;
        private final Map<String, Integer> KEY_MAP = new HashMap<>();
        int[] trackInstruments = new int[16];
        int[] trackTempos = new int[16];

        public NotationPlayer(Context context) {
            viewer = new midiViewer(context);
            viewer.init();
            handler = new Handler();

            noteEventsList = new ArrayList<>();
            for (int i = 0; i < 16; i++) {
                noteEventsList.add(new ArrayList<>());
                trackInstruments[i] = 0;
                trackTempos[i] = 120;
            }

            initKeyMap();
        }

        public void clearTrack(int track) {
            if (track >= 0 && track < 16) {
                noteEventsList.get(track).clear();
            }
        }

        private void initKeyMap() {
            KEY_MAP.put("C大调", 0);
            KEY_MAP.put("G大调", 7);
            KEY_MAP.put("D大调", 2);
            KEY_MAP.put("A大调", 9);
            KEY_MAP.put("E大调", 4);
            KEY_MAP.put("B大调", 11);
            KEY_MAP.put("F#大调", 6);
            KEY_MAP.put("C#大调", 1);
            KEY_MAP.put("F大调", -5);
            KEY_MAP.put("降B大调", -2);
            KEY_MAP.put("降E大调", -9);
            KEY_MAP.put("降A大调", -4);
            KEY_MAP.put("降D大调", -11);
            KEY_MAP.put("降G大调", -6);

            KEY_MAP.put("A小调", 0);
            KEY_MAP.put("E小调", 7);
            KEY_MAP.put("B小调", 2);
            KEY_MAP.put("升F小调", 9);
            KEY_MAP.put("升C小调", 4);
            KEY_MAP.put("升G小调", 11);
            KEY_MAP.put("升D小调", 6);
            KEY_MAP.put("升A小调", 1);
            KEY_MAP.put("D小调", -5);
            KEY_MAP.put("G小调", -2);
            KEY_MAP.put("C小调", -9);
            KEY_MAP.put("F小调", -4);
            KEY_MAP.put("降B小调", -11);
            KEY_MAP.put("降E小调", -6);
        }

        private int getKeyOffset(String key) {
            Integer offset = KEY_MAP.get(key);
            return offset != null ? offset : 0;
        }

        public void parseNotation(String notation, int track) {
            List<NoteEvent> noteEvents = noteEventsList.get(track);
            noteEvents.clear();

            if (notation.trim().isEmpty()) {
                return;
            }

            int instrument = 0;
            int tempo = 120;
            String key = "C大调";
            int keyOffset = getKeyOffset(key);

            Pattern pattern = Pattern.compile("\\[(\\d+),(\\d+),([^]]+)\\]");
            Matcher matcher = pattern.matcher(notation);
            if (matcher.find()) {
                try {
                    instrument = Integer.parseInt(matcher.group(1));
                    tempo = Integer.parseInt(matcher.group(2));
                    key = matcher.group(3);
                    keyOffset = getKeyOffset(key);
                    notation = notation.substring(matcher.end()).trim();
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
            }

            trackInstruments[track] = instrument;
            trackTempos[track] = tempo;

            int volume = 127;

            // 预处理：移除竖线分隔符（仅用于阅读，不影响演奏）
            notation = notation.replace("|", "");

            // 解析简谱
            parseJianpuNotation(notation, noteEvents, instrument, tempo, keyOffset, volume);
        }

        private void parseJianpuNotation(String notation, List<NoteEvent> noteEvents,
                                         int instrument, int tempo, int keyOffset, int volume) {
            int position = 0;
            int length = notation.length();

            while (position < length) {
                try {
                    char currentChar = notation.charAt(position);

                    if (currentChar == '[') {
                        position = parseChord(notation, position, noteEvents, instrument, tempo, keyOffset, volume);
                    } else if (currentChar == '(') {
                        position = parseHalvedDuration(notation, position, noteEvents, instrument, tempo, keyOffset, volume);
                    } else if (Character.isDigit(currentChar)) {
                        position = parseSingleNote(notation, position, noteEvents, instrument, tempo, keyOffset, volume);
                    } else if (currentChar == '0') {
                        position = parseRest(notation, position, noteEvents, instrument, tempo);
                    } else if (currentChar == '-') {
                        position = parseExtension(notation, position, noteEvents, instrument, tempo);
                    } else {
                        position++;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    position++; // 跳过出错的位置继续解析
                }
            }
        }

        private int parseChord(String notation, int startPos, List<NoteEvent> noteEvents,
                               int instrument, int tempo, int keyOffset, int volume) {
            int endPos = notation.indexOf(']', startPos);
            if (endPos == -1) {
                return startPos + 1;
            }

            String chordContent = notation.substring(startPos + 1, endPos);
            List<NoteEvent> chordNotes = new ArrayList<>();

            int pos = 0;
            while (pos < chordContent.length()) {
                char c = chordContent.charAt(pos);
                if (Character.isDigit(c)) {
                    pos = parseNoteInChord(chordContent, pos, chordNotes, instrument, tempo, keyOffset, volume);
                } else {
                    pos++;
                }
            }

            if (!chordNotes.isEmpty()) {
                NoteEvent firstNote = chordNotes.get(0);
                for (NoteEvent note : chordNotes) {
                    note.duration = 4;
                    note.hasDot = false;
                    noteEvents.add(note);
                }
            }

            return endPos + 1;
        }

        private int parseNoteInChord(String chord, int startPos, List<NoteEvent> chordNotes,
                                     int instrument, int tempo, int keyOffset, int volume) {
            int pos = startPos;
            char noteChar = chord.charAt(pos);

            if (!Character.isDigit(noteChar)) {
                return pos + 1;
            }

            int noteValue = Character.getNumericValue(noteChar);

            // 添加验证：确保音符值在1-7范围内
            if (noteValue < 1 || noteValue > 7) {
                return pos + 1; // 跳过无效音符
            }

            pos++;

            boolean sharp = false;
            if (pos < chord.length() && chord.charAt(pos) == '#') {
                sharp = true;
                pos++;
            }

            int lowOctaveCount = 0;
            int highOctaveCount = 0;
            while (pos < chord.length()) {
                char c = chord.charAt(pos);
                if (c == '.') {
                    lowOctaveCount++;
                    pos++;
                } else if (c == '\'') {
                    highOctaveCount++;
                    pos++;
                } else {
                    break;
                }
            }

            int midiNote = convertToMidiNote(noteValue, 4 - lowOctaveCount + highOctaveCount, keyOffset);
            if (sharp) {
                midiNote += 1;
            }

            chordNotes.add(new NoteEvent(midiNote, 4, false, volume, instrument, tempo));
            return pos;
        }

        private int parseHalvedDuration(String notation, int startPos, List<NoteEvent> noteEvents,
                                        int instrument, int tempo, int keyOffset, int volume) {
            int endPos = notation.indexOf(')', startPos);
            if (endPos == -1) {
                return startPos + 1;
            }

            String halvedContent = notation.substring(startPos + 1, endPos);
            List<NoteEvent> halvedNotes = new ArrayList<>();

            int pos = 0;
            while (pos < halvedContent.length()) {
                char c = halvedContent.charAt(pos);
                if (Character.isDigit(c)) {
                    pos = parseNoteWithHalvedDuration(halvedContent, pos, halvedNotes, instrument, tempo, keyOffset, volume);
                } else if (c == '/') {
                    if (!halvedNotes.isEmpty()) {
                        NoteEvent lastNote = halvedNotes.get(halvedNotes.size() - 1);
                        lastNote.duration *= 2;
                    }
                    pos++;
                } else {
                    pos++;
                }
            }

            noteEvents.addAll(halvedNotes);
            return endPos + 1;
        }

        private int parseNoteWithHalvedDuration(String content, int startPos, List<NoteEvent> notes,
                                                int instrument, int tempo, int keyOffset, int volume) {
            NoteEvent note = parseBasicNote(content, startPos, instrument, tempo, keyOffset, volume);
            if (note != null) {
                note.duration = 8;
                notes.add(note);
                return startPos + getNoteLength(content, startPos);
            }
            return startPos + 1;
        }

        private int parseSingleNote(String notation, int startPos, List<NoteEvent> noteEvents,
                                    int instrument, int tempo, int keyOffset, int volume) {
            NoteEvent note = parseBasicNote(notation, startPos, instrument, tempo, keyOffset, volume);
            if (note != null) {
                int endPos = startPos + getNoteLength(notation, startPos);
                String durationStr = notation.substring(startPos + 1, endPos);

                int dashCount = 0;
                boolean hasDot = false;
                for (char c : durationStr.toCharArray()) {
                    if (c == '-') {
                        dashCount++;
                    } else if (c == '.') {
                        hasDot = true;
                    }
                }

                note.duration = getDurationFromDashCount(dashCount);
                note.hasDot = hasDot;
                noteEvents.add(note);
                return endPos;
            }
            return startPos + 1;
        }

        private NoteEvent parseBasicNote(String notation, int startPos,
                                         int instrument, int tempo, int keyOffset, int volume) {
            if (startPos >= notation.length() || !Character.isDigit(notation.charAt(startPos))) {
                return null;
            }

            char noteChar = notation.charAt(startPos);
            int noteValue = Character.getNumericValue(noteChar);

            // 添加验证：确保音符值在1-7范围内
            if (noteValue < 1 || noteValue > 7) {
                return null; // 无效音符
            }

            int pos = startPos + 1;

            boolean sharp = false;
            if (pos < notation.length() && notation.charAt(pos) == '#') {
                sharp = true;
                pos++;
            }

            int lowOctaveCount = 0;
            int highOctaveCount = 0;
            while (pos < notation.length()) {
                char c = notation.charAt(pos);
                if (c == '.') {
                    lowOctaveCount++;
                    pos++;
                } else if (c == '\'') {
                    highOctaveCount++;
                    pos++;
                } else {
                    break;
                }
            }

            int octave = 4 - lowOctaveCount + highOctaveCount;
            int midiNote = convertToMidiNote(noteValue, octave, keyOffset);
            if (sharp) {
                midiNote += 1;
            }

            return new NoteEvent(midiNote, 4, false, volume, instrument, tempo);
        }

        private int getNoteLength(String notation, int startPos) {
            int pos = startPos + 1;
            while (pos < notation.length()) {
                char c = notation.charAt(pos);
                if (c == '#' || c == '.' || c == '\'' || c == '-') {
                    pos++;
                } else {
                    break;
                }
            }
            return pos - startPos;
        }

        private int getDurationFromDashCount(int dashCount) {
            switch (dashCount) {
                case 0: return 4;
                case 1: return 2;
                case 2: return 1;
                default: return 1;
            }
        }

        private int parseRest(String notation, int startPos, List<NoteEvent> noteEvents,
                              int instrument, int tempo) {
            int pos = startPos + 1;
            int dashCount = 0;
            boolean hasDot = false;

            while (pos < notation.length()) {
                char c = notation.charAt(pos);
                if (c == '-') {
                    dashCount++;
                    pos++;
                } else if (c == '.') {
                    hasDot = true;
                    pos++;
                } else {
                    break;
                }
            }

            int duration = getDurationFromDashCount(dashCount);
            noteEvents.add(new NoteEvent(-1, duration, hasDot, 0, instrument, tempo));
            return pos;
        }

        private int parseExtension(String notation, int startPos, List<NoteEvent> noteEvents,
                                   int instrument, int tempo) {
            int pos = startPos;
            int dashCount = 0;

            while (pos < notation.length() && notation.charAt(pos) == '-') {
                dashCount++;
                pos++;
            }

            if (!noteEvents.isEmpty()) {
                NoteEvent lastNote = noteEvents.get(noteEvents.size() - 1);
                if (lastNote.note >= 0) {
                    lastNote.duration = Math.max(lastNote.duration, getDurationFromDashCount(dashCount));
                }
            }

            return pos;
        }

        private int convertToMidiNote(int noteValue, int octave, int keyOffset) {
            try {
                int[] baseNotes = {60, 62, 64, 65, 67, 69, 71}; // 1-7对应的MIDI音符

                // 确保noteValue在有效范围内 (1-7)
                if (noteValue < 1 || noteValue > 7) {
                    return 60; // 中央C作为默认值
                }

                int baseNote = baseNotes[noteValue - 1];
                baseNote += keyOffset;
                baseNote += (octave - 4) * 12;

                // 限制在MIDI有效范围内 (0-127)
                return Math.max(0, Math.min(127, baseNote));
            } catch (Exception e) {
                e.printStackTrace();
                return 60; // 出错时返回中央C
            }
        }

        public void startPlayback(boolean loop) {
            this.looping = loop;
            if (!isPlaying) {
                startPlayback();
            }
        }

        private void startPlayback() {
            isPlaying = true;
            long maxDuration = 0;
            for (int track = 0; track < 16; track++) {
                List<NoteEvent> events = noteEventsList.get(track);
                if (!events.isEmpty()) {
                    long trackDuration = calculateTrackDuration(events, trackTempos[track]);
                    maxDuration = Math.max(maxDuration, trackDuration);
                }
            }

            for (int track = 0; track < 16; track++) {
                final int currentTrack = track;
                List<NoteEvent> events = noteEventsList.get(track);
                if (events.isEmpty()) continue;

                if (viewer != null && viewer.remoteService != null) {
                    try {
                        viewer.remoteService.setProgram(currentTrack, trackInstruments[currentTrack]);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                long currentTime = 0;
                for (NoteEvent event : events) {
                    final NoteEvent finalEvent = event;

                    if (event.note >= 0) {
                        handler.postDelayed(() -> {
                            if (isPlaying && viewer != null && viewer.remoteService != null) {
                                try {
                                    viewer.remoteService.noteOn(currentTrack, finalEvent.note, finalEvent.volume);

                                    int noteDuration = calculateNoteDuration(finalEvent.duration, finalEvent.hasDot, trackTempos[currentTrack]);
                                    handler.postDelayed(() -> {
                                        if (viewer != null && viewer.remoteService != null) {
                                            try {
                                                viewer.remoteService.noteOff(currentTrack, finalEvent.note);
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    }, noteDuration);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }, currentTime);
                    }

                    currentTime += calculateNoteDuration(event.duration, event.hasDot, trackTempos[currentTrack]);
                }
            }

            handler.postDelayed(() -> {
                isPlaying = false;
                if (looping) {
                    startPlayback();
                } else {
                    runOnUiThread(() -> Toast.makeText(midiEditor.this, "播放完成", Toast.LENGTH_SHORT).show());
                }
            }, maxDuration);
        }

        private int calculateNoteDuration(int durationType, boolean hasDot, int tempo) {
            double quarterNoteMs = 60000.0 / tempo;
            double baseDurationMs = quarterNoteMs * (4.0 / durationType);
            if (hasDot) {
                baseDurationMs *= 1.5;
            }
            return (int) baseDurationMs;
        }

        private long calculateTrackDuration(List<NoteEvent> events, int tempo) {
            long totalDuration = 0;
            for (NoteEvent event : events) {
                totalDuration += calculateNoteDuration(event.duration, event.hasDot, tempo);
            }
            return totalDuration;
        }

        public void stopPlayback() {
            isPlaying = false;
            handler.removeCallbacksAndMessages(null);
            if (viewer != null) {
                viewer.stopAllNotes();
            }
        }
    }

    static class NoteEvent {
        int note;
        int duration;
        boolean hasDot;
        int volume;
        int instrument;
        int tempo;

        NoteEvent(int note, int duration, boolean hasDot, int volume, int instrument, int tempo) {
            this.note = note;
            this.duration = duration;
            this.hasDot = hasDot;
            this.volume = volume;
            this.instrument = instrument;
            this.tempo = tempo;
        }
    }

    static class MidiExporter {
        private static final int HEADER_SIZE = 14;
        private static final int TRACK_HEADER_SIZE = 8;
        private static final int META_EVENT = 0xFF;
        private static final int END_OF_TRACK = 0x2F;
        private static final int SET_TEMPO = 0x51;
        private static final int TIME_SIGNATURE = 0x58;
        private static final int NOTE_ON = 0x90;
        private static final int NOTE_OFF = 0x80;
        private static final int PROGRAM_CHANGE = 0xC0;
        private static final int CONTROL_CHANGE = 0xB0;
        private static final int VOLUME_CC = 0x07;
        private static final int EXPRESSION_CC = 0x0B;
        private static final int PPQN = 480;

        public static void exportToMidi(List<List<NoteEvent>> noteEventsList, int[] trackInstruments,
                                        int[] trackTempos, String filePath) throws Exception {
            FileOutputStream fos = new FileOutputStream(filePath);

            int actualTrackCount = 0;
            for (int i = 0; i < noteEventsList.size(); i++) {
                if (!noteEventsList.get(i).isEmpty()) {
                    actualTrackCount++;
                }
            }

            if (actualTrackCount == 0) {
                fos.close();
                throw new Exception("没有可导出的音轨内容");
            }

            int baseTempo = getMostCommonTempo(trackTempos);
            writeMidiHeader(fos, 0, 1);
            writeMultiChannelTrack(fos, noteEventsList, trackInstruments, trackTempos, baseTempo);

            fos.close();
        }
        private static int mapVolumeToMidiVelocity(int volume) {
            // 确保音量在合理范围内
            if (volume <= 0) return 0;
            // 可以将输入音量（0-127）映射到不同范围
            return Math.min(127, Math.max(1, volume));
        }

        // 修改导出时的音量设置
//        private static final int DEFAULT_VOLUME = 127;  // 默认100而非127
        private static int getMostCommonTempo(int[] trackTempos) {
            Map<Integer, Integer> tempoCount = new HashMap<>();
            for (int tempo : trackTempos) {
                if (tempo > 0) {
                    // 替换 getOrDefault 方法
                    Integer count = tempoCount.get(tempo);
                    if (count == null) {
                        count = 0;
                    }
                    tempoCount.put(tempo, count + 1);
                }
            }

            if (tempoCount.isEmpty()) return 120;

            int mostCommon = 120;
            int maxCount = 0;
            for (Map.Entry<Integer, Integer> entry : tempoCount.entrySet()) {
                if (entry.getValue() > maxCount) {
                    mostCommon = entry.getKey();
                    maxCount = entry.getValue();
                }
            }
            return mostCommon;
        }

        private static void writeMidiHeader(FileOutputStream fos, int format, int numTracks) throws Exception {
            byte[] header = new byte[HEADER_SIZE];
            header[0] = 'M'; header[1] = 'T'; header[2] = 'h'; header[3] = 'd';
            header[4] = 0; header[5] = 0; header[6] = 0; header[7] = 6;
            header[8] = 0; header[9] = (byte) format;
            header[10] = 0; header[11] = (byte) numTracks;
            header[12] = (byte) ((PPQN >> 8) & 0xFF);
            header[13] = (byte) (PPQN & 0xFF);

            fos.write(header);
        }

        private static void writeMultiChannelTrack(FileOutputStream fos, List<List<NoteEvent>> noteEventsList,
                                                   int[] trackInstruments, int[] trackTempos, int baseTempo) throws Exception {
            ByteArrayOutputStream trackData = new ByteArrayOutputStream();

            writeTempoEvent(trackData, baseTempo);
            writeTimeSignature(trackData);

            for (int channel = 0; channel < Math.min(16, trackInstruments.length); channel++) {
                if (!noteEventsList.get(channel).isEmpty()) {
                    writeControlChange(trackData, channel, VOLUME_CC, 127);
                    writeControlChange(trackData, channel, EXPRESSION_CC, 127);
                    // 添加：设置声像居中（可选）
                    writeControlChange(trackData, channel, 0x0A, 64); // Pan
                    // 添加：设置混响发送量
                    writeControlChange(trackData, channel, 0x5B, 40); // Reverb

                    writeProgramChange(trackData, channel, trackInstruments[channel]);
                }
            }

            List<MidiEvent> allEvents = new ArrayList<>();

            for (int channel = 0; channel < noteEventsList.size(); channel++) {
                List<NoteEvent> events = noteEventsList.get(channel);
                if (events.isEmpty()) continue;

                int currentTime = 0;
                for (NoteEvent event : events) {
                    if (event.note >= 0) {
                        int noteDuration = calculateNoteDurationInTicks(event.duration, event.hasDot, trackTempos[channel]);
//                        int midiVelocity = mapVolumeToMidiVelocity(event.volume);
                        // 修改这里：将音量映射到最大
                        int midiVelocity = 127;  // 直接设为最大，而不是使用 mapVolumeToMidiVelocity



                        allEvents.add(new MidiEvent(
                                convertTimeToBaseTempo(currentTime, trackTempos[channel], baseTempo),
                                channel, event.note, midiVelocity, true
                        ));

                        allEvents.add(new MidiEvent(
                                convertTimeToBaseTempo(currentTime + noteDuration, trackTempos[channel], baseTempo),
                                channel, event.note, 0, false
                        ));
                    }
                    currentTime += calculateNoteDurationInTicks(event.duration, event.hasDot, trackTempos[channel]);
                }
            }

            Collections.sort(allEvents, new Comparator<MidiEvent>() {
                @Override
                public int compare(MidiEvent e1, MidiEvent e2) {
                    return Integer.compare(e1.time, e2.time);
                }
            });

            int lastTime = 0;
            for (MidiEvent event : allEvents) {
                int deltaTime = event.time - lastTime;
                writeVarLength(trackData, deltaTime);

                if (event.isNoteOn) {
                    trackData.write(NOTE_ON | event.channel);
                    trackData.write(event.note);
                    trackData.write(event.velocity);
                } else {
                    trackData.write(NOTE_OFF | event.channel);
                    trackData.write(event.note);
                    trackData.write(event.velocity);
                }

                lastTime = event.time;
            }

            writeVarLength(trackData, 0);
            trackData.write(META_EVENT);
            trackData.write(END_OF_TRACK);
            trackData.write(0x00);

            writeTrackHeader(fos, trackData.toByteArray());
            fos.write(trackData.toByteArray());
        }

        private static int convertTimeToBaseTempo(int timeInOriginalTempo, int originalTempo, int baseTempo) {
            if (originalTempo == baseTempo) return timeInOriginalTempo;
            return (int) (timeInOriginalTempo * ((double) baseTempo / originalTempo));
        }

//        private static int mapVolumeToMidiVelocity(int volume) {
//            if (volume <= 0) return 0;
//            return 127;
//        }

        private static void writeControlChange(ByteArrayOutputStream data, int channel, int controller, int value) throws Exception {
            writeVarLength(data, 0);
            data.write(CONTROL_CHANGE | channel);
            data.write(controller);
            data.write(value & 0x7F);
        }

        private static void writeProgramChange(ByteArrayOutputStream data, int channel, int program) throws Exception {
            writeVarLength(data, 0);
            data.write(PROGRAM_CHANGE | channel);
            data.write(program & 0x7F);
        }

        private static void writeTempoEvent(ByteArrayOutputStream data, int tempo) throws Exception {
            writeVarLength(data, 0);
            data.write(META_EVENT);
            data.write(SET_TEMPO);
            data.write(0x03);
            int microsPerQuarter = 60000000 / tempo;
            data.write((microsPerQuarter >> 16) & 0xFF);
            data.write((microsPerQuarter >> 8) & 0xFF);
            data.write(microsPerQuarter & 0xFF);
        }

        private static void writeTimeSignature(ByteArrayOutputStream data) throws Exception {
            writeVarLength(data, 0);
            data.write(META_EVENT);
            data.write(TIME_SIGNATURE);
            data.write(0x04);
            data.write(0x04);
            data.write(0x02);
            data.write(0x18);
            data.write(0x08);
        }

        private static void writeTrackHeader(FileOutputStream fos, byte[] trackData) throws Exception {
            byte[] header = new byte[TRACK_HEADER_SIZE];
            header[0] = 'M'; header[1] = 'T'; header[2] = 'r'; header[3] = 'k';
            int length = trackData.length;
            header[4] = (byte) ((length >> 24) & 0xFF);
            header[5] = (byte) ((length >> 16) & 0xFF);
            header[6] = (byte) ((length >> 8) & 0xFF);
            header[7] = (byte) (length & 0xFF);

            fos.write(header);
        }

        private static void writeVarLength(ByteArrayOutputStream data, int value) throws Exception {
            if (value < 0) value = 0;

            if (value == 0) {
                data.write(0);
                return;
            }

            int buffer = value & 0x7F;
            value >>= 7;

            while (value > 0) {
                buffer <<= 8;
                buffer |= 0x80;
                buffer += (value & 0x7F);
                value >>= 7;
            }

            while (true) {
                data.write(buffer & 0xFF);
                if ((buffer & 0x80) != 0) {
                    buffer >>= 8;
                } else {
                    break;
                }
            }
        }

        private static int calculateNoteDurationInTicks(int durationType, boolean hasDot, int tempo) {
            int quarterNoteTicks = PPQN;
            double baseTicks = quarterNoteTicks * (4.0 / durationType);
            if (hasDot) {
                baseTicks *= 1.5;
            }
            return (int) baseTicks;
        }
    }

    static class MidiEvent {
        int time;
        int channel;
        int note;
        int velocity;
        boolean isNoteOn;

        MidiEvent(int time, int channel, int note, int velocity, boolean isNoteOn) {
            this.time = time;
            this.channel = channel;
            this.note = note;
            this.velocity = velocity;
            this.isNoteOn = isNoteOn;
        }
    }

    @Override
    protected void onDestroy() {
        if (notationPlayer != null) {
            notationPlayer.stopPlayback();
        }
        super.onDestroy();
    }
}