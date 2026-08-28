package app.tuxguitar.io.ascii;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.tuxguitar.io.base.TGFileFormatException;


/**
 * Parser for ASCII guitar tablature.
 *
 * Ported conceptually from tab2gp5/txt2gp5.py.
 *
 * Responsibilities:
 * - sanitize ASCII TAB input
 * - detect TAB lines
 * - group six guitar strings into blocks
 * - normalize blocks
 * - detect string order
 * - detect tuning
 * - detect note/chord columns
 * - detect bar columns
 * - split blocks into musical measure segments
 *
 * The TuxGuitar song model itself is created by AsciiTabSongBuilder.
 */
public class AsciiTabParser {


    private static final String DEBUG_PROPERTY = "tuxguitar.ascii.debug";
    private static final String DEBUG_ENV = "TUXGUITAR_ASCII_DEBUG";

    private static final int BAR_COLUMN_THRESHOLD = 4;

//    private static final Pattern TIME_PATTERN =
//            Pattern.compile(
//                    "(?i)\\btime\\s*:\\s*(\\d+)\\s*/\\s*(\\d+)");

    private static final Pattern TIME_PATTERN =
            Pattern.compile(
                    "(?i)\\b(?:"
                    + "time"
                    + "|time\\s*[-_ ]?\\s*signature"
                    + "|timesignature"
                    + "|meter"
                    + "|metre"
                    + ")\\s*[:=]?\\s*"
                    + "(\\d+)\\s*/\\s*(\\d+)");                    

    private static final Pattern TEMPO_PATTERN =
            Pattern.compile(
                    "(?i)\\btempo\\s*:\\s*(\\d+)");  

    private static final Pattern TUNING_PATTERN =
            Pattern.compile(
                    "(?i)\\btuning\\b\\s*[:=]?\\s*\\(([^)]*)\\)");

    /*
     * Labelled TAB line:
     *
     * e|------0---|
     * B|------1---|
     */
    private static final Pattern TAB_LABEL_PATTERN =
            Pattern.compile(
                    "^\\s*[eEbBgGdDaAcC]\\s{0,3}\\|(?=[^|]*[0-9hps/\\\\~xX*().\\-|])");

    /*
     * Count/ruler lines which must not be interpreted as TAB.
     *
     * | 1 . 2 . 3 . |
     * | 3 |
     */
    private static final Pattern COUNT_LINE_PATTERN =
            Pattern.compile(
                    "^\\s*\\|\\s*[\\d.\\s]+\\s*\\|\\s*$");

    /*
     * Example:
     *
     * 10 | . | . |
     */
    private static final Pattern RULER_LINE_PATTERN =
            Pattern.compile(
                    "^\\s*\\d+\\s*\\|[ .|]*$");

    /*
     * TAB headers which can be sanitized:
     *
     * E|
     * E-
     * e|
     */
    private static final Pattern HEADER_PATTERN =
            Pattern.compile(
                    "^\\s*([A-Ga-g](?:[#b])?)\\s*(\\||-)");

    private static final int[] STANDARD_TUNING =
            new int[]{64, 59, 55, 50, 45, 40};

    private static final String[] STANDARD_NAMES =
            new String[]{"E", "B", "G", "D", "A", "E"};


    /*
     * ----------------------------------------------------------------------
     * Parsed metadata
     * ----------------------------------------------------------------------
     */

    private String title = "";
    private String artist = "";
    private String album = "";
    private String author = "";
    private String date = "";
    private String copyright = "";
    private String writer = "";
    private String transcriber = "";
    private String comments = "";

    private int timeNumerator = 4;
    private int timeDenominator = 4;
    private int tempo = 120;   
    private String[] explicitTuning;


    /*
     * ----------------------------------------------------------------------
     * Debug
     * ----------------------------------------------------------------------
     */

    private static boolean isDebugEnabled() {

        String env = System.getenv(DEBUG_ENV);

        return Boolean.getBoolean(DEBUG_PROPERTY)
                || "1".equals(env)
                || "true".equalsIgnoreCase(env)
                || "yes".equalsIgnoreCase(env);
    }

    private static void debug(String message) {

        if (isDebugEnabled()) {
            System.err.println("[ASCII-IMPORT] " + message);
        }
    }

    private static void debugException(
            String message,
            Throwable throwable) {

        if (isDebugEnabled()) {

            System.err.println(
                    "[ASCII-IMPORT] "
                    + message
                    + ": "
                    + throwable.getClass().getName()
                    + ": "
                    + throwable.getMessage());

            throwable.printStackTrace(System.err);
        }
    }

    private static String printable(String text) {

        if (text == null) {
            return "<null>";
        }

        String result =
                text.replace("\t", "\\t");

        if (result.length() > 180) {
            result =
                    result.substring(0, 177)
                    + "...";
        }

        return result;
    }

    /*
     * ----------------------------------------------------------------------
     * Main parser entry point
     * ----------------------------------------------------------------------
     */

    /**
     * Parse an ASCII TAB stream.
     *
     * Non-TAB text is ignored.
     * Valid TAB lines are collected and grouped into blocks of six strings.
     */
    public List<TabBlock> parseTabBlocks(
            InputStream inputStream)
            throws IOException {

        debug("parseTabBlocks(): START");

        List<String> rawLines =
                readLines(inputStream);

        debug(
                "parseTabBlocks(): raw lines="
                + rawLines.size());
                
        detectMetadata(rawLines);        

        /*
         * Equivalent to the Python sanitizer step before TAB filtering.
         */
        List<String> sanitizedLines =
                sanitizeLines(rawLines);

        /*
         * tab2gp5 filters TAB lines first and then groups them in sixes.
         */
        List<String> tabLines =
                new ArrayList<String>();

        for (int i = 0;
                i < sanitizedLines.size();
                i++) {

            String line =
                    sanitizedLines.get(i);

            boolean tab =
                    isTabLine(line);

            debug(
                    "line "
                    + (i + 1)
                    + ": "
                    + (tab ? "TAB" : "skip")
                    + " | "
                    + printable(line));

            if (tab) {
                tabLines.add(line);
            }
        }

        debug(
                "parseTabBlocks(): TAB lines="
                + tabLines.size());

        List<TabBlock> blocks =
                new ArrayList<TabBlock>();

        int index = 0;

        while (index + 5 < tabLines.size()) {

            List<String> rawBlock =
                    new ArrayList<String>();

            for (int i = 0; i < 6; i++) {
                rawBlock.add(
                        tabLines.get(index + i));
            }

            try {

                TabBlock block =
                        createTabBlock(rawBlock);

                blocks.add(block);

                debug(
                        "block #"
                        + blocks.size()
                        + " CREATED"
                        + ", width="
                        + block.getWidth()
                        + ", mapping="
                        + Arrays.toString(
                                block.getStringMapping())
                        + ", tuning="
                        + Arrays.toString(
                                block.getTuning())
                        + ", chordColumns="
                        + block.getChordColumns()
                        + ", barColumns="
                        + block.getBarColumns()
                        + ", measures="
                        + block.getMeasures());

                index += 6;

            } catch (Exception e) {

                /*
                 * Same tolerant concept as Python:
                 * shift by one line and try again.
                 */
                debugException(
                        "block creation failed at filtered TAB line "
                        + (index + 1),
                        e);

                index++;
            }
        }

        if (index < tabLines.size()) {

            debug(
                    "parseTabBlocks(): "
                    + (tabLines.size() - index)
                    + " TAB line(s) left without complete six-line block");
        }

        debug(
                "parseTabBlocks(): END, blocks="
                + blocks.size());

        return blocks;
    }

    /*
     * ----------------------------------------------------------------------
     * Input
     * ----------------------------------------------------------------------
     */

    private List<String> readLines(
            InputStream inputStream)
            throws IOException {

        List<String> lines =
                new ArrayList<String>();

        try {

            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    inputStream,
                                    "UTF-8"));

            String line;

            while ((line = reader.readLine())
                    != null) {

                lines.add(line);
            }

        } catch (IOException e) {

            debugException(
                    "readLines() failed",
                    e);

            throw e;
        }

        debug(
                "readLines(): completed, lineCount="
                + lines.size());

        return lines;
    }
    
    /*
     * ----------------------------------------------------------------------
     * Metadata
     * ----------------------------------------------------------------------
     */


    private String metadataValue(
            String line,
            String... names) {

        for (String name : names) {

            Pattern pattern =
                    Pattern.compile(
                            "(?i)^\\s*"
                            + Pattern.quote(name)
                            + "\\s*[:=]\\s*(.+?)\\s*$");

            Matcher matcher =
                    pattern.matcher(line);

            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }

        return null;
    }     

    private void detectMetadata(List<String> lines) {

        /*
         * Defaults for every newly parsed file.
         */
        this.timeNumerator = 4;
        this.timeDenominator = 4;
        this.tempo = 120;
        this.explicitTuning = null;

        for (String line : lines) {

            Matcher timeMatcher =
                    TIME_PATTERN.matcher(line);

            if (timeMatcher.find()) {

                int numerator =
                        Integer.parseInt(
                                timeMatcher.group(1));

                int denominator =
                        Integer.parseInt(
                                timeMatcher.group(2));

                if (numerator > 0
                        && denominator > 0) {

                    this.timeNumerator = numerator;
                    this.timeDenominator = denominator;

                    debug(
                            "metadata: time="
                            + this.timeNumerator
                            + "/"
                            + this.timeDenominator);
                }
            }

            Matcher tempoMatcher =
                    TEMPO_PATTERN.matcher(line);

            if (tempoMatcher.find()) {

                int value =
                        Integer.parseInt(
                                tempoMatcher.group(1));

                if (value >= 20
                        && value <= 400) {

                    this.tempo = value;

                    debug(
                            "metadata: tempo="
                            + this.tempo);
                }
            }

            Matcher tuningMatcher =
                    TUNING_PATTERN.matcher(line);

            if (tuningMatcher.find()) {

                String[] tuning =
                        parseTuningNames(
                                tuningMatcher.group(1));

                if (tuning != null) {
                    this.explicitTuning = tuning;

                    debug(
                            "metadata: tuning="
                            + Arrays.toString(
                                    this.explicitTuning)
                            + " (low to high)");
                }
            }
            
            /*
             * ----------------------------------------------------------
             * Text metadata
             * ----------------------------------------------------------
             */

            String value;

            /*
             * Title / song name
             */
            value = metadataValue(
                    line,
                    "title",
                    "song",
                    "name");

            if (value != null) {
                this.title = value;
            }

            /*
             * Artist / performer
             */
            value = metadataValue(
                    line,
                    "artist",
                    "performer");

            if (value != null) {
                this.artist = value;
            }

            /*
             * Album
             */
            value = metadataValue(
                    line,
                    "album");

            if (value != null) {
                this.album = value;
            }

            /*
             * Author / composer
             */
            value = metadataValue(
                    line,
                    "author",
                    "composer");

            if (value != null) {
                this.author = value;
            }

            /*
             * Date
             */
            value = metadataValue(
                    line,
                    "date");

            if (value != null) {
                this.date = value;
            }

            /*
             * Copyright
             */
            value = metadataValue(
                    line,
                    "copyright");

            if (value != null) {
                this.copyright = value;
            }

            /*
             * Tab creator
             */
            value = metadataValue(
                    line,
                    "writer",
                    "tab creator",
                    "tabbed by");

            if (value != null) {
                this.writer = value;
            }

            /*
             * Transcriber
             */
            value = metadataValue(
                    line,
                    "transcriber",
                    "transcribed by",
                    "transcription by");

            if (value != null) {
                this.transcriber = value;
            }

            /*
             * Comments
             */
            value = metadataValue(
                    line,
                    "comments",
                    "comment");

            if (value != null) {
                this.comments = value;
            }            
            
            
            
            
            
            
        }

        debug(
                "metadata final: time="
                + this.timeNumerator
                + "/"
                + this.timeDenominator
                + ", tempo="
                + this.tempo);
    }    
    

    /*
     * ----------------------------------------------------------------------
     * Sanitizer
     * ----------------------------------------------------------------------
     */

    private List<String> sanitizeLines(
            List<String> rawLines) {

        List<String> result =
                new ArrayList<String>();

        for (String line : rawLines) {
            result.add(
                    sanitizeLine(line));
        }

        return result;
    }

    /**
     * Conservative equivalent of the Python "safe" sanitizer.
     */
    private String sanitizeLine(
            String line) {

        if (line == null) {
            return "";
        }

        String result =
                line.replace("\t", "    ");

        /*
         * Normalize common Unicode dash characters.
         */
        result =
                result.replace('\u2013', '-')
                      .replace('\u2014', '-')
                      .replace('\u2012', '-');

        Matcher matcher =
                HEADER_PATTERN.matcher(result);

        if (matcher.find()) {

            String body =
                    result.substring(
                            matcher.end());

            /*
             * Protect prose such as:
             *
             * E Major ...
             */
            if (body.matches("^\\s*[A-Za-z].*")
                    && !body.matches(".*[0-9].*")
                    && !body.matches(".*[-|].*[-|].*")) {

                return result;
            }

            String note =
                    matcher.group(1);

            result =
                    matcher.replaceFirst(
                            Matcher.quoteReplacement(
                                    note + "|"));
        }

        return result;
    }

    /*
     * ----------------------------------------------------------------------
     * TAB line detection
     * ----------------------------------------------------------------------
     */

    private boolean isTabLine(
            String line) {

        if (line == null) {
            return false;
        }

        String text =
                line.trim();

        if (text.length() == 0) {
            return false;
        }

        if (COUNT_LINE_PATTERN.matcher(text)
                .matches()) {

            return false;
        }

        if (RULER_LINE_PATTERN.matcher(text)
                .matches()) {

            return false;
        }

        if (TAB_LABEL_PATTERN.matcher(text)
                .find()) {

            return true;
        }

        int firstPipe =
                text.indexOf('|');

        int lastPipe =
                text.lastIndexOf('|');

        if (firstPipe >= 0
                && lastPipe > firstPipe) {

            String middle =
                    text.substring(
                            firstPipe + 1,
                            lastPipe);

            String stripped =
                    middle.trim();

            if (stripped.length() < 6) {
                return false;
            }

            int hyphens =
                    countChar(
                            middle,
                            '-');

            if (hyphens
                    >= Math.max(
                            5,
                            middle.length() / 4)) {

                return true;
            }

            if (middle.matches(".*\\d.*")
                    && hyphens >= 3) {

                return true;
            }
        }

        return false;
    }

    private int countChar(
            String text,
            char character) {

        int count = 0;

        for (int i = 0;
                i < text.length();
                i++) {

            if (text.charAt(i)
                    == character) {

                count++;
            }
        }

        return count;
    }

    /*
     * ----------------------------------------------------------------------
     * Block construction
     * ----------------------------------------------------------------------
     */

    private TabBlock createTabBlock(
            List<String> rawLines)
            throws TGFileFormatException {

        if (rawLines == null
                || rawLines.size() != 6) {

            throw new TGFileFormatException(
                    "Expected exactly six TAB lines");
        }

        debug(
                "createTabBlock(): raw lines:");

        for (int i = 0;
                i < rawLines.size();
                i++) {

            debug(
                    "    raw["
                    + i
                    + "]="
                    + printable(
                            rawLines.get(i)));
        }

        List<String> normalizedLines =
                normalizeTabBlock(rawLines);

        int[] stringMapping =
                detectRowToStringMapping(
                        rawLines);

        int[] tuning =
                detectTuningFromLabels(
                        rawLines,
                        stringMapping);

        List<Integer> chordColumns =
                findChordColumns(
                        normalizedLines);

        List<Integer> barColumns =
                findBarColumns(
                        normalizedLines);

        List<MeasureSegment> measures =
                detectMeasures(
                        normalizedLines,
                        chordColumns,
                        barColumns);

        int width =
                getWidth(
                        normalizedLines);

        debug(
                "createTabBlock(): width="
                + width);

        debug(
                "createTabBlock(): mapping="
                + Arrays.toString(
                        stringMapping));

        debug(
                "createTabBlock(): tuning="
                + Arrays.toString(
                        tuning));

        debug(
                "createTabBlock(): chord columns="
                + chordColumns);

        debug(
                "createTabBlock(): bar columns="
                + barColumns);

        debug(
                "createTabBlock(): measures="
                + measures);

        return new TabBlock(
                normalizedLines,
                stringMapping,
                tuning,
                chordColumns,
                barColumns,
                measures,
                width);
    }

    /*
     * ----------------------------------------------------------------------
     * Normalization
     * ----------------------------------------------------------------------
     */

    /**
     * Equivalent to normalize_tab_block() in txt2gp5.
     *
     * Important:
     * We remove only the outer TAB delimiters.
     * Internal pipes are deliberately preserved because they represent
     * musical bar lines.
     */
    private List<String> normalizeTabBlock(
            List<String> rawLines) {

        List<String> result =
                new ArrayList<String>();

        int maxWidth = 0;

        for (String line : rawLines) {

            String normalized = extractTabContent(line);

            result.add(normalized);

            maxWidth = Math.max(maxWidth, normalized.length());
        }

        /*
         * A 6-line TAB block is considered to end at the visual line end.
         *
         * If the source does not have a closing '|', the block end still
         * represents the end of the final measure.
         *
         * We therefore normalize all rows to the same width and append a
         * virtual closing bar to every row.
         */
        maxWidth = Math.max(1, maxWidth);

        /*
         * Pad using '-' like the Python implementation.
         */
        /*
         * Pad all six rows to a common visual width.
         *
         * Then append a virtual closing bar.
         *
         * This means both forms become identical internally:
         *
         * E|----------------|
         *
         * and
         *
         * E|----------------
         */
        for (int i = 0; i < result.size(); i++) {

            String value = result.get(i);

            StringBuilder builder = new StringBuilder(value);

            while (builder.length() < maxWidth) {

                builder.append('-');
            }

            /*
             * Internal representation always ends in a bar.
             */
            if (builder.length() == 0 || builder.charAt(
                            builder.length() - 1)
                            != '|') {

                builder.append('|');
            }

            result.set(i, builder.toString());
        }

        return result;
    }

    private String extractTabContent(
            String line) {
            
        if (line == null) {
            return "";
        }

        int firstPipe =
                line.indexOf('|');

        if (firstPipe < 0) {
            return line.trim();
        }

        String content = line.substring(firstPipe + 1);

        /*
         * For:
         *
         * E|-----||
         *
         * strip all trailing TAB delimiter pipes, but preserve genuine
         * internal bar lines.
         */
        /*
         * Nur nachlaufende Whitespaces entfernen.
         */
        int end =
                content.length();

        while (end > 0
                && Character.isWhitespace(
                        content.charAt(end - 1))) {

            end--;
        }

        content =
                content.substring(
                        0,
                        end);

        /*
         * Mehrfache Schlussstriche normalisieren:
         *
         * -----||
         * wird
         * -----|
         */
        while (content.endsWith("||")) {
            content =
                    content.substring(
                            0,
                            content.length() - 1);
        }

        return content;
    }

    private int getWidth(
            List<String> lines) {

        int width = 0;

        for (String line : lines) {
            width =
                    Math.max(
                            width,
                            line.length());
        }

        return width;
    }

    /*
     * ----------------------------------------------------------------------
     * String ordering
     * ----------------------------------------------------------------------
     */

    /*
     * ----------------------------------------------------------------------
     * String ordering
     * ----------------------------------------------------------------------
     */

    private int[] detectRowToStringMapping(
            List<String> rawLines) {

        /*
         * ASCII TAB is normally written with the highest string
         * at the top:
         *
         * e|----------------
         * B|----------------
         * G|----------------
         * D|----------------
         * A|----------------
         * E|----------------
         *
         */
         
        boolean lowStringFirst =
                false;

        if (lowStringFirst) {

            debug(
                    "detectRowToStringMapping(): "
                    + "LOW string first (manual)");

            return new int[]{
                    6, 5, 4, 3, 2, 1
            };
        }

        debug(
                "detectRowToStringMapping(): "
                + "HIGH string first (default)");

        return new int[]{
                1, 2, 3, 4, 5, 6
        };
    }


    private String extractHead(
            String line) {

        if (line == null) {
            return "";
        }

        String text =
                line.trim();

        if (text.length() == 0) {
            return "";
        }

        int pipe =
                text.indexOf('|');

        if (pipe > 0) {
            return text.substring(
                    0,
                    pipe + 1);
        }

        return text.substring(
                0,
                Math.min(
                        text.length(),
                        3));
    }

    /*
     * ----------------------------------------------------------------------
     * Tuning
     * ----------------------------------------------------------------------
     */
         
    private int[] detectTuningFromLabels(
            List<String> rawLines,
            int[] rowMapping) {

        if (this.explicitTuning != null) {
            return tuningFromLowToHighNames(
                    this.explicitTuning);
        }

        String[] labels =
                new String[6];

        for (int row = 0;
                row < 6;
                row++) {

            String label =
                    parseLabelNote(
                            rawLines.get(row));

            int stringNumber =
                    rowMapping[row];

            labels[stringNumber - 1] =
                    (label != null)
                            ? label
                            : STANDARD_NAMES[
                                    stringNumber - 1];
        }

        debug(
                "detectTuningFromLabels(): labels="
                + Arrays.toString(labels));

        int[] tuning =
                copy(STANDARD_TUNING);

        /*
         * Move each standard string to the nearest occurrence
         * of the detected pitch class.
         *
         * This handles Standard, Drop D, DADGAD, Open G,
         * Open D, Double Drop D, Eb Standard, etc.
         * without maintaining a table of known tunings.
         */
        for (int i = 0;
                i < 6;
                i++) {

            Integer standardPitch =
                    noteToSemitone(
                            STANDARD_NAMES[i]);

            Integer targetPitch =
                    noteToSemitone(
                            labels[i]);

            if (standardPitch == null
                    || targetPitch == null) {

                continue;
            }

            int delta =
                    (targetPitch
                    - standardPitch
                    + 12)
                    % 12;

            /*
             * Select nearest pitch-class occurrence.
             *
             * Example:
             * low E -> D = -2 instead of +10.
             */
            if (delta > 6) {
                delta -= 12;
            }

            tuning[i] +=
                    delta;
        }

        debug(
                "detectTuningFromLabels(): tuning="
                + Arrays.toString(tuning));

        return tuning;
    }     

    /**
     * Parse exactly six pitch names inside the tuning parentheses.
     *
     * Examples (low string to high string):
     *
     * DADGBE
     * D A D G B E
     * Eb Ab Db Gb Bb Eb
     * D A D F# A D
     */
    private String[] parseTuningNames(
            String value) {

        if (value == null) {
            return null;
        }

        List<String> names =
                new ArrayList<String>();

        int index = 0;

        while (index < value.length()) {

            char character =
                    value.charAt(index);

            if (Character.isWhitespace(character)
                    || character == ',') {

                index++;
                continue;
            }

            char upper =
                    Character.toUpperCase(character);

            if ((upper < 'A' || upper > 'G')
                    && upper != 'H') {

                return null;
            }

            StringBuilder name =
                    new StringBuilder();

            name.append(upper);
            index++;

            if (index < value.length()) {

                char accidental =
                        value.charAt(index);

                if (accidental == '#') {
                    name.append('#');
                    index++;
                } else if (accidental == 'b'
                        && Character.isUpperCase(character)) {

                    name.append('b');
                    index++;
                }
            }

            if (noteToSemitone(name.toString()) == null) {
                return null;
            }

            names.add(name.toString());
        }

        if (names.size() != 6) {
            return null;
        }

        return names.toArray(
                new String[names.size()]);
    }

    private int[] tuningFromLowToHighNames(
            String[] names) {

        int[] tuning =
                copy(STANDARD_TUNING);

        for (int stringIndex = 0;
                stringIndex < 6;
                stringIndex++) {

            String targetName =
                    names[5 - stringIndex];

            Integer standardPitch =
                    noteToSemitone(
                            STANDARD_NAMES[stringIndex]);

            Integer targetPitch =
                    noteToSemitone(targetName);

            int delta =
                    (targetPitch.intValue()
                    - standardPitch.intValue()
                    + 12)
                    % 12;

            if (delta > 6) {
                delta -= 12;
            }

            tuning[stringIndex] += delta;
        }

        debug(
                "explicit tuning: "
                + Arrays.toString(tuning));

        return tuning;
    }
     

    private int[] detectTuningFromLabelsOld(
            List<String> rawLines,
            int[] rowMapping) {

        String[] labels =
                new String[6];

        for (int row = 0;
                row < 6;
                row++) {

            String label =
                    parseLabelNote(
                            rawLines.get(row));

            int stringNumber = rowMapping[row];

            labels[stringNumber - 1] = label;
        }

        /*
         * Missing labels fall back to standard note names.
         */
        for (int i = 0; i < 6; i++) {

            if (labels[i] == null) {
                labels[i] = STANDARD_NAMES[i];
            }
        }

        debug(
                "detectTuningFromLabels(): labels="
                + Arrays.toString(
                        labels));

        /*
         * Standard
         */
        if (matches(labels, new String[]{ "E", "B", "G", "D", "A", "E" })) {

            return copy( STANDARD_TUNING);
        }

        /*
         * Drop D
         */
        if (matches(labels,new String[]{ "E", "B", "G", "D", "A", "D"})) {

            return new int[]{ 64, 59, 55,50, 45, 38};
        }

        /*
         * DADGAD
         */
        if (matches(labels,new String[]{"D", "A", "G","D", "A", "D"})) {

            return new int[]{62, 57, 55,50, 45, 38};
        }

        /*
         * Open G
         */
        if (matches(labels,new String[]{"D", "B", "G","D", "G", "D" })) {

            return new int[]{62, 59, 55,50, 43, 38};
        }

        /*
         * Open D
         */
        if (matches(labels,new String[]{"D", "A", "F#","D", "A", "D"})) {

            return new int[]{62, 57, 54,50, 45, 38};
        }

        /*
         * Double Drop D
         */
        if (matches(labels,new String[]{"D", "B", "G","D", "A", "D" })) {

            return new int[]{62, 59, 55,50, 45, 38};
        }

        /*
         * Eb standard
         */
        if (matches(labels,new String[]{"Eb", "Bb", "Gb","Db", "Ab", "Eb"})) {

            return new int[]{63, 58, 54,49, 44, 39};
        }

        /*
         * Generic fallback:
         * move each standard string to the nearest matching pitch class.
         */
        int[] tuning = copy(STANDARD_TUNING);

        for (int i = 0; i < 6; i++) {

            Integer from =
                    noteToSemitone(
                            STANDARD_NAMES[i]);

            Integer to =
                    noteToSemitone(
                            labels[i]);

            if (from != null
                    && to != null) {

                int delta =
                        (to.intValue()
                        - from.intValue()
                        + 12)
                        % 12;

                if (delta > 6) {
                    delta -= 12;
                }

                tuning[i] += delta;
            }
        }

        return tuning;
    }

    private String parseLabelNote(
            String line) {

        if (line == null) {
            return null;
        }

        Pattern pattern =
                Pattern.compile(
                        "^\\s*([A-Ga-g](?:[#b])?)\\s*[|\\-\\s]");

        Matcher matcher =
                pattern.matcher(line);

        if (!matcher.find()) {
            return null;
        }

        String note =
                matcher.group(1);

        if (note.length() == 1) {
            return note.toUpperCase();
        }

        return note.substring(0, 1)
                .toUpperCase()
                + note.substring(1);
    }

    private boolean matches(
            String[] actual,
            String[] expected) {

        if (actual.length
                != expected.length) {

            return false;
        }

        for (int i = 0;
                i < actual.length;
                i++) {

            if (!expected[i]
                    .equals(actual[i])) {

                return false;
            }
        }

        return true;
    }

    private Integer noteToSemitone(
            String note) {

        if ("C".equals(note)) {
            return Integer.valueOf(0);
        }

        if ("C#".equals(note)
                || "Db".equals(note)) {
            return Integer.valueOf(1);
        }

        if ("D".equals(note)) {
            return Integer.valueOf(2);
        }

        if ("D#".equals(note)
                || "Eb".equals(note)) {
            return Integer.valueOf(3);
        }

        if ("E".equals(note)) {
            return Integer.valueOf(4);
        }

        if ("F".equals(note)) {
            return Integer.valueOf(5);
        }

        if ("F#".equals(note)
                || "Gb".equals(note)) {
            return Integer.valueOf(6);
        }

        if ("G".equals(note)) {
            return Integer.valueOf(7);
        }

        if ("G#".equals(note)
                || "Ab".equals(note)) {
            return Integer.valueOf(8);
        }

        if ("A".equals(note)) {
            return Integer.valueOf(9);
        }

        if ("A#".equals(note)
                || "Bb".equals(note)) {
            return Integer.valueOf(10);
        }

        if ("B".equals(note)
                || "H".equals(note)) {
            return Integer.valueOf(11);
        }

        return null;
    }

    private int[] copy(
            int[] source) {

        int[] result =
                new int[source.length];

        System.arraycopy(
                source,
                0,
                result,
                0,
                source.length);

        return result;
    }

    /*
     * ----------------------------------------------------------------------
     * Chord columns
     * ----------------------------------------------------------------------
     */

    /**
     * Equivalent to find_chord_and_bar_columns() from txt2gp5.
     *
     * Multi-digit fret numbers are only counted at the first digit.
     */
    private List<Integer> findChordColumns(
            List<String> lines) {

        List<Integer> result =
                new ArrayList<Integer>();

        int width =
                getWidth(lines);

        for (int col = 0;
                col < width;
                col++) {

            boolean found = false;

            for (String line : lines) {

                if (col >= line.length()) {
                    continue;
                }

                char ch =
                        line.charAt(col);

                if (Character.isDigit(ch)
                        && (col == 0
                        || !Character.isDigit(
                                line.charAt(
                                        col - 1)))) {

                    found = true;
                    break;
                }

                if (ch == 'x'
                        || ch == 'X') {

                    found = true;
                    break;
                }
            }

            if (found) {
                result.add(
                        Integer.valueOf(col));
            }
        }

        return result;
    }

    /*
     * ----------------------------------------------------------------------
     * Bar columns
     * ----------------------------------------------------------------------
     */

    /**
     * A column is a musical bar line if at least four of the six guitar
     * strings contain "|" at the same horizontal position.
     *
     * This mirrors is_bar_column(..., threshold=4) from tab2gp5.
     */
    private boolean isBarColumn(
            List<String> lines,
            int column) {

        int count = 0;

        for (String line : lines) {

            if (column < line.length()
                    && line.charAt(column)
                    == '|') {

                count++;
            }
        }

        return count
                >= BAR_COLUMN_THRESHOLD;
    }

    private List<Integer> findBarColumns(
            List<String> lines) {

        List<Integer> result =
                new ArrayList<Integer>();

        int width =
                getWidth(lines);

        for (int col = 0;
                col < width;
                col++) {

            if (isBarColumn(
                    lines,
                    col)) {

                result.add(
                        Integer.valueOf(col));
            }
        }

        return result;
    }

    /*
     * ----------------------------------------------------------------------
     * Measures
     * ----------------------------------------------------------------------
     */

    /**
     * Equivalent to the "bounds / pairs / filtered" part of build_song()
     * in txt2gp5.
     */
    private List<MeasureSegment> detectMeasures(
            List<String> lines,
            List<Integer> chordColumns,
            List<Integer> barColumns) {

        List<MeasureSegment> measures =
                new ArrayList<MeasureSegment>();

        int width =
                getWidth(lines);

        if (width <= 0) {
            return measures;
        }

        List<Integer> bounds =
                new ArrayList<Integer>();

        bounds.add(
                Integer.valueOf(0));

        for (Integer bar :
                barColumns) {

            int value =
                    bar.intValue();

            if (value > 0
                    && value < width) {

                if (bounds.get(
                        bounds.size() - 1)
                        .intValue()
                        != value) {

                    bounds.add(
                            Integer.valueOf(
                                    value));
                }
            }
        }

        if (bounds.get(
                bounds.size() - 1)
                .intValue()
                != width) {

            bounds.add(
                    Integer.valueOf(width));
        }

        /*
         * Remove duplicate/non-increasing boundaries.
         */
        List<Integer> cleanBounds =
                new ArrayList<Integer>();

        for (Integer bound :
                bounds) {

            if (cleanBounds.isEmpty()
                    || bound.intValue()
                    > cleanBounds.get(
                            cleanBounds.size() - 1)
                            .intValue()) {

                cleanBounds.add(bound);
            }
        }

        for (int i = 0;
                i < cleanBounds.size() - 1;
                i++) {

            int start =
                    cleanBounds.get(i)
                            .intValue();

            int end =
                    cleanBounds.get(i + 1)
                            .intValue();

            boolean hasOnset =
                    containsChordColumn(
                            chordColumns,
                            start,
                            end);

            boolean onlyBars =
                    true;

            for (int col = start;
                    col < end;
                    col++) {

                if (!isBarColumn(
                        lines,
                        col)) {

                    onlyBars = false;
                    break;
                }
            }

            /*
             * Same logic as Python:
             *
             * if has_onset or not only_bars:
             *     filtered.append((a,b))
             */
            if (hasOnset
                    || !onlyBars) {

                measures.add(
                        new MeasureSegment(
                                start,
                                end));
            }
        }

        /*
         * A valid block without internal bars still represents one measure.
         */
        if (measures.isEmpty()
                && width > 0) {

            measures.add(
                    new MeasureSegment(
                            0,
                            width));
        }

        return measures;
    }

    private boolean containsChordColumn(
            List<Integer> chordColumns,
            int start,
            int end) {

        for (Integer value :
                chordColumns) {

            int column =
                    value.intValue();

            if (column >= start
                    && column < end) {

                return true;
            }
        }

        return false;
    }
    
    public int getTimeNumerator() {
        return this.timeNumerator;
    }

    public int getTimeDenominator() {
        return this.timeDenominator;
    }

    public int getTempo() {
        return this.tempo;
    }
    
    public String getTitle() {
        return this.title;
    }

    public String getArtist() {
        return this.artist;
    }

    public String getAlbum() {
        return this.album;
    }

    public String getAuthor() {
        return this.author;
    }

    public String getDate() {
        return this.date;
    }

    public String getCopyright() {
        return this.copyright;
    }

    public String getWriter() {
        return this.writer;
    }

    public String getTranscriber() {
        return this.transcriber;
    }

    public String getComments() {
        return this.comments;
    }    
    

    /*
     * ----------------------------------------------------------------------
     * Public data classes
     * ----------------------------------------------------------------------
     */

    /**
     * One six-string ASCII TAB block.
     *
     * A block may contain one or more musical measures.
     */
    public static class TabBlock {

        private final List<String> lines;
        private final int[] stringMapping;
        private final int[] tuning;

        private final List<Integer> chordColumns;
        private final List<Integer> barColumns;

        private final List<MeasureSegment> measures;

        private final int width;

        public TabBlock(
                List<String> lines,
                int[] stringMapping,
                int[] tuning,
                List<Integer> chordColumns,
                List<Integer> barColumns,
                List<MeasureSegment> measures,
                int width) {

            this.lines =
                    lines;

            this.stringMapping =
                    stringMapping;

            this.tuning =
                    tuning;

            this.chordColumns =
                    chordColumns;

            this.barColumns =
                    barColumns;

            this.measures =
                    measures;

            this.width =
                    width;
        }

        public List<String> getLines() {
            return this.lines;
        }

        public int[] getStringMapping() {
            return this.stringMapping;
        }

        public int[] getTuning() {
            return this.tuning;
        }

        public List<Integer> getChordColumns() {
            return this.chordColumns;
        }

        public List<Integer> getBarColumns() {
            return this.barColumns;
        }

        public List<MeasureSegment> getMeasures() {
            return this.measures;
        }

        public int getWidth() {
            return this.width;
        }
    }

    /**
     * Horizontal range of one musical measure within a TabBlock.
     *
     * startColumn is inclusive.
     * endColumn is exclusive.
     */
    public static class MeasureSegment {

        private final int startColumn;
        private final int endColumn;

        public MeasureSegment(
                int startColumn,
                int endColumn) {

            this.startColumn =
                    startColumn;

            this.endColumn =
                    endColumn;
        }

        public int getStartColumn() {
            return this.startColumn;
        }

        public int getEndColumn() {
            return this.endColumn;
        }

        public int getWidth() {
            return Math.max(
                    0,
                    this.endColumn
                    - this.startColumn);
        }

        @Override
        public String toString() {

            return "["
                    + this.startColumn
                    + ","
                    + this.endColumn
                    + ")";
        }
    }
}
