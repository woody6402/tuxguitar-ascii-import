package app.tuxguitar.io.ascii;

import java.util.List;

import app.tuxguitar.io.base.TGFileFormatException;
import app.tuxguitar.song.factory.TGFactory;
import app.tuxguitar.song.managers.TGSongManager;
import app.tuxguitar.song.models.TGBeat;
import app.tuxguitar.song.models.TGChannel;
import app.tuxguitar.song.models.TGDuration;
import app.tuxguitar.song.models.TGMeasure;
import app.tuxguitar.song.models.TGMeasureHeader;
import app.tuxguitar.song.models.TGNote;
import app.tuxguitar.song.models.TGSong;
import app.tuxguitar.song.models.TGString;
import app.tuxguitar.song.models.TGTrack;
import app.tuxguitar.song.models.TGVoice;

/**
 * Converts the neutral result of AsciiTabQuantizer into a TuxGuitar TGSong.
 *
 * Important:
 *
 * This class does NOT interpret ASCII positions and does NOT quantize.
 *
 * Pipeline:
 *
 * AsciiTabParser
 *      ->
 * TabBlock / MeasureSegment
 *      ->
 * AsciiTabQuantizer
 *      ->
 * QuantizedMeasure / QuantizedEvent
 *      ->
 * THIS CLASS
 *      ->
 * TGSong / TGMeasure / TGBeat / TGNote
 *
 * The parsing/quantization behaviour is therefore kept separate from
 * the TuxGuitar song model.
 */
public class AsciiTabSongBuilder {

    private static final String DEBUG_PROPERTY =
            "tuxguitar.ascii.debug";

    private static final String DEBUG_ENV =
            "TUXGUITAR_ASCII_DEBUG";

    private static final int DEFAULT_PROGRAM = 25;
    private static final int DEFAULT_VOLUME = 100;
    private static final int DEFAULT_BALANCE = 64;

    private static final int DEFAULT_TIME_NUMERATOR = 4;
    private static final int DEFAULT_TIME_DENOMINATOR = 4;
    private static final int DEFAULT_TEMPO = 120;

    private final TGFactory factory;
    private final TGSongManager manager;
    private final AsciiTabQuantizer quantizer;

    public AsciiTabSongBuilder(TGFactory factory) {

        this.factory =
                factory;

        this.manager =
                new TGSongManager(factory);

        /*
         * Same defaults as txt2gp5:
         *
         * leading silence tolerance = 2
         * quarter_units = 4
         * bases = 16,32,64
         */
        this.quantizer =
                new AsciiTabQuantizer();
    }

    /*
     * ----------------------------------------------------------------------
     * Debug
     * ----------------------------------------------------------------------
     */

    private static boolean isDebugEnabled() {

        String env =
                System.getenv(DEBUG_ENV);

        return Boolean.getBoolean(DEBUG_PROPERTY)
                || "1".equals(env)
                || "true".equalsIgnoreCase(env)
                || "yes".equalsIgnoreCase(env);
    }

    private static void debug(String message) {

        if (isDebugEnabled()) {

            System.err.println(
                    "[ASCII-BUILDER] "
                    + message);
        }
    }

    /*
     * ----------------------------------------------------------------------
     * Compatibility entry point
     * ----------------------------------------------------------------------
     */

    /**
     * Compatibility overload.
     *
     * If an older reader still calls buildSong(blocks,title),
     * use normal defaults.
     */
    public TGSong buildSong(
            List<AsciiTabParser.TabBlock> blocks,
            String title)
            throws TGFileFormatException {

        return buildSong(
                blocks,
                title,
                DEFAULT_TIME_NUMERATOR,
                DEFAULT_TIME_DENOMINATOR,
                DEFAULT_TEMPO,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    /*
     * ----------------------------------------------------------------------
     * Main entry point
     * ----------------------------------------------------------------------
     */

    public TGSong buildSong(
            List<AsciiTabParser.TabBlock> blocks,
            String fallbackTitle,
            int timeNumerator,
            int timeDenominator,
            int tempo,
            String title,
            String artist,
            String album,
            String author,
            String date,
            String copyright,
            String writer,
            String transcriber,
            String comments)
            throws TGFileFormatException {     


        if (blocks == null
                || blocks.isEmpty()) {

            throw new TGFileFormatException(
                    "No tab blocks found");
        }

        if (timeNumerator <= 0) {
            timeNumerator =
                    DEFAULT_TIME_NUMERATOR;
        }

        if (timeDenominator <= 0) {
            timeDenominator =
                    DEFAULT_TIME_DENOMINATOR;
        }

        if (tempo <= 0) {
            tempo =
                    DEFAULT_TEMPO;
        }

        debug(
                "buildSong(): START, blocks="
                + blocks.size());

        debug(
                "buildSong(): time="
                + timeNumerator
                + "/"
                + timeDenominator
                + ", tempo="
                + tempo);

        /*
         * --------------------------------------------------------------
         * Song
         * --------------------------------------------------------------
         */

        TGSong song =
                factory.newSong();
                

        manager.setProperties(
                song,
                title,
                artist,
                album,
                author,
                date,
                copyright,
                writer,
                transcriber,
                comments);                
                

        song.setName(
                title != null
                        ? title
                        : "ASCII Tablature");

        /*
         * --------------------------------------------------------------
         * Track
         * --------------------------------------------------------------
         */

        TGTrack track =
                manager.addTrack(song);

        track.setName(
                "Guitar");

        debug(
                "track created: number="
                + track.getNumber());

        configureStrings(
                track,
                blocks.get(0));

        configureChannel(
                song,
                track);

        /*
         * --------------------------------------------------------------
         * Measures
         * --------------------------------------------------------------
         */

        int measureIndex =
                0;

        for (int blockIndex = 0;
                blockIndex < blocks.size();
                blockIndex++) {

            AsciiTabParser.TabBlock block =
                    blocks.get(blockIndex);

            List<AsciiTabParser.MeasureSegment> segments =
                    block.getMeasures();

            debug(
                    "block #"
                    + (blockIndex + 1)
                    + ": segments="
                    + segments.size()
                    + " "
                    + segments);

            for (int segmentIndex = 0;
                    segmentIndex < segments.size();
                    segmentIndex++) {

                AsciiTabParser.MeasureSegment segment =
                        segments.get(segmentIndex);

                /*
                 * Important:
                 *
                 * Configure each header BEFORE adding the following
                 * measure. TGSongManager uses previous measure lengths
                 * to calculate subsequent starts.
                 *
                 * If we created 28 default 4/4 measures first and only
                 * afterwards changed them to 2/4, all starts would remain
                 * based on the wrong 4/4 length.
                 */
                TGMeasure measure =
                        getOrCreateMeasure(
                                song,
                                track,
                                measureIndex);

                configureMeasureHeader(
                        measure.getHeader(),
                        timeNumerator,
                        timeDenominator,
                        tempo);

                debug(
                        "block #"
                        + (blockIndex + 1)
                        + " segment #"
                        + (segmentIndex + 1)
                        + " "
                        + segment
                        + " -> measure #"
                        + (measureIndex + 1)
                        + ", start="
                        + measure.getStart());

                /*
                 * ------------------------------------------------------
                 * THIS is now the ONLY quantization call.
                 * ------------------------------------------------------
                 */
                AsciiTabQuantizer.QuantizedMeasure
                        quantizedMeasure =
                        this.quantizer.quantizeMeasure(
                                block,
                                segment,
                                timeNumerator,
                                timeDenominator);

                /*
                 * Skip only a trailing empty measure at the end
                 * of an ASCII TAB block.
                 */
                boolean isLastSegmentOfBlock =
                        (segmentIndex == segments.size() - 1);

                if (isLastSegmentOfBlock
                        && !hasNoteEvents(quantizedMeasure)) {

                    debug(
                            "skip trailing empty measure: "
                            + segment);

                    continue;
                }                                

                debugQuantizedMeasure(
                        measureIndex,
                        quantizedMeasure);

                populateMeasure(
                        measure,
                        quantizedMeasure);

                measureIndex++;
            }
        }

        /*
         * Let TuxGuitar normalize beat ordering.
         */
        manager.orderBeats(song);

        debug("AFTER orderBeats:");

        for (int mi = 0; mi < track.countMeasures(); mi++) {

            TGMeasure m = track.getMeasure(mi);

            debug(
                    "  measure #"
                    + (mi + 1)
                    + " start="
                    + m.getStart()
                    + " beats="
                    + m.countBeats());

            for (int bi = 0; bi < m.countBeats(); bi++) {

                TGBeat beat =
                        m.getBeat(bi);

                TGVoice voice =
                        beat.getVoice(0);

                StringBuilder notes =
                        new StringBuilder();

                for (int ni = 0;
                        ni < voice.countNotes();
                        ni++) {

                    TGNote note =
                            voice.getNote(ni);

                    if (notes.length() > 0) {
                        notes.append(", ");
                    }

                    notes.append("s")
                         .append(note.getString())
                         .append(":")
                         .append(note.getValue());
                }

                debug(
                        "    beat #"
                        + (bi + 1)
                        + " start="
                        + beat.getStart()
                        + " dur=1/"
                        + voice.getDuration().getValue()
                        + (voice.getDuration().isDotted()
                                ? "."
                                : "")
                        + " empty="
                        + voice.isEmpty()
                        + " notes=["
                        + notes
                        + "]");
            }
        }
        

        debug(
                "buildSong(): tracks="
                + song.countTracks());

        debug(
                "buildSong(): headers="
                + song.countMeasureHeaders());

        debug(
                "buildSong(): measures="
                + track.countMeasures());

        for (int i = 0;
                i < track.countMeasures();
                i++) {

            TGMeasure measure =
                    track.getMeasure(i);

            debug(
                    "measure["
                    + i
                    + "]"
                    + " start="
                    + measure.getStart()
                    + " beats="
                    + measure.countBeats()
                    + " time="
                    + measure.getHeader()
                            .getTimeSignature()
                            .getNumerator()
                    + "/"
                    + measure.getHeader()
                            .getTimeSignature()
                            .getDenominator()
                            .getValue()
                    + " tempo="
                    + measure.getHeader()
                            .getTempo()
                            .getQuarterValue());
        }

        debug(
                "buildSong(): END");

        return song;
    }

    /*
     * ----------------------------------------------------------------------
     * Track setup
     * ----------------------------------------------------------------------
     */

    private void configureStrings(
            TGTrack track,
            AsciiTabParser.TabBlock firstBlock) {

        int[] tuning =
                firstBlock.getTuning();

        track.getStrings().clear();

        for (int i = 0;
                i < tuning.length;
                i++) {

            TGString string =
                    factory.newString();

            string.setNumber(
                    i + 1);

            string.setValue(
                    tuning[i]);

            track.getStrings().add(
                    string);
        }

        debug(
                "strings configured: count="
                + track.stringCount());
    }

    private void configureChannel(
            TGSong song,
            TGTrack track) {

        TGChannel channel =
                manager.addChannel(song);

        channel.setProgram(
                (short) DEFAULT_PROGRAM);

        channel.setVolume(
                (short) DEFAULT_VOLUME);

        channel.setBalance(
                (short) DEFAULT_BALANCE);

        channel.setName(
                manager.createChannelNameFromProgram(
                        song,
                        channel));

        track.setChannelId(
                channel.getChannelId());

        debug(
                "channel configured: id="
                + channel.getChannelId());
    }

    /*
     * ----------------------------------------------------------------------
     * Measure creation
     * ----------------------------------------------------------------------
     */

    private TGMeasure getOrCreateMeasure(
            TGSong song,
            TGTrack track,
            int measureIndex) {

        while (track.countMeasures()
                <= measureIndex) {

            debug(
                    "adding measure, current count="
                    + track.countMeasures());

            manager.addNewMeasureBeforeEnd(
                    song);
        }

        return track.getMeasure(
                measureIndex);
    }

    /*
     * ----------------------------------------------------------------------
     * Header
     * ----------------------------------------------------------------------
     */

    private void configureMeasureHeader(
            TGMeasureHeader header,
            int numerator,
            int denominator,
            int tempo) {

        /*
         * Time signature.
         */
        header.getTimeSignature()
                .setNumerator(
                        numerator);

        header.getTimeSignature()
                .getDenominator()
                .setValue(
                        denominator);

        /*
         * Tempo is stored on TGMeasureHeader.
         */
        header.getTempo()
                .setQuarterValue(
                        tempo);

        debug(
                "header #"
                + header.getNumber()
                + ": "
                + numerator
                + "/"
                + denominator
                + ", tempo="
                + tempo
                + ", start="
                + header.getStart()
                + ", length="
                + header.getLength());
    }

    /*
     * ----------------------------------------------------------------------
     * QuantizedMeasure -> TGMeasure
     * ----------------------------------------------------------------------
     */
     
    private boolean hasNoteEvents(
            AsciiTabQuantizer.QuantizedMeasure measure) {

        for (AsciiTabQuantizer.QuantizedEvent event :
                measure.getEvents()) {

            if (!event.isRest()
                    && event.getNotes() != null
                    && !event.getNotes().isEmpty()) {

                return true;
            }
        }

        return false;
    }     

    private void populateMeasure(
            TGMeasure measure,
            AsciiTabQuantizer.QuantizedMeasure source) {

        /*
         * The quantizer already gives us sequential musical durations.
         *
         * We therefore no longer calculate positions from ASCII columns.
         */
        long start =
                measure.getStart();

        List<AsciiTabQuantizer.QuantizedEvent> events =
                source.getEvents();

        debug(
                "populateMeasure(): events="
                + events.size()
                + ", baseUnits="
                + source.getBaseUnits()
                + ", chosenUnits="
                + source.getChosenUnits());

        for (int eventIndex = 0;
                eventIndex < events.size();
                eventIndex++) {

            AsciiTabQuantizer.QuantizedEvent event =
                    events.get(eventIndex);

            TGDuration duration =
                    factory.newDuration();

            int durationValue =
                    normalizeDurationValue(
                            event.getDenominator());

            duration.setValue(
                    durationValue);

            duration.setDotted(
                    event.isDotted());

            /*
             * No double-dotted values are currently produced
             * by txt2gp5's duration table.
             */
            duration.setDoubleDotted(
                    false);

            TGBeat beat =
                    factory.newBeat();

            beat.setStart(
                    start);

            measure.addBeat(
                    beat);

            TGVoice voice =
                    beat.getVoice(0);

            /*
             * Copy the quantized duration into the voice.
             */
            voice.getDuration()
                    .setValue(
                            duration.getValue());

            voice.getDuration()
                    .setDotted(
                            duration.isDotted());

            voice.getDuration()
                    .setDoubleDotted(
                            duration.isDoubleDotted());

            if (event.isRest()) {

                voice.setEmpty(
                        false); // true ist falsch ;

                debug(
                        "  event #"
                        + (eventIndex + 1)
                        + " REST"
                        + " start="
                        + start
                        + " duration=1/"
                        + duration.getValue()
                        + (duration.isDotted()
                                ? "."
                                : ""));

            } else {

                voice.setEmpty(
                        false);

                addNotes(
                        voice,
                        event.getNotes());

                debug(
                        "  event #"
                        + (eventIndex + 1)
                        + " NOTES"
                        + " start="
                        + start
                        + " duration=1/"
                        + duration.getValue()
                        + (duration.isDotted()
                                ? "."
                                : "")
                        + " notes="
                        + event.getNotes());
            }

            /*
             * This is now the crucial part:
             *
             * advance by the REAL TuxGuitar duration rather than by
             * ASCII columns or arbitrary slot numbers.
             */
            start +=
                    duration.getTime();
        }

        debug(
                "populateMeasure(): beats="
                + measure.countBeats()
                + ", expectedEnd="
                + (measure.getStart()
                + measure.getHeader().getLength())
                + ", actualEnd="
                + start);
    }

    /*
     * ----------------------------------------------------------------------
     * Notes
     * ----------------------------------------------------------------------
     */

    private void addNotes(
            TGVoice voice,
            List<AsciiTabQuantizer.ParsedNote> sourceNotes) {

        for (AsciiTabQuantizer.ParsedNote sourceNote :
                sourceNotes) {

            int stringNumber =
                    sourceNote.getStringNumber();

            int fret =
                    sourceNote.getFret();

            /*
             * Same sanity limits as txt2gp5's basic behaviour.
             */
            if (stringNumber < 1
                    || stringNumber > 6) {

                debug(
                        "  ignored invalid string="
                        + stringNumber);

                continue;
            }

            if (fret < 0
                    || fret > 24) {

                debug(
                        "  ignored invalid fret="
                        + fret);

                continue;
            }

            /*
             * Avoid two simultaneous notes on the same string.
             */
            if (containsString(
                    voice,
                    stringNumber)) {

                debug(
                        "  ignored duplicate string="
                        + stringNumber
                        + ", fret="
                        + fret);

                continue;
            }

            TGNote note =
                    factory.newNote();

            note.setString(
                    stringNumber);

            note.setValue(
                    fret);

            note.setVelocity(
                    64);

            voice.addNote(
                    note);
        }
    }

    private boolean containsString(
            TGVoice voice,
            int stringNumber) {

        for (int i = 0;
                i < voice.countNotes();
                i++) {

            if (voice.getNote(i)
                    .getString()
                    == stringNumber) {

                return true;
            }
        }

        return false;
    }

    /*
     * ----------------------------------------------------------------------
     * Duration
     * ----------------------------------------------------------------------
     */

    /**
     * The quantizer should already return standard values:
     *
     * 1, 2, 4, 8, 16, 32, 64
     *
     * Keep this method defensive so malformed input cannot create an
     * unsupported TGDuration.
     */
    private int normalizeDurationValue(
            int denominator) {

        switch (denominator) {

            case 1:
                return TGDuration.WHOLE;

            case 2:
                return TGDuration.HALF;

            case 4:
                return TGDuration.QUARTER;

            case 8:
                return TGDuration.EIGHTH;

            case 16:
                return TGDuration.SIXTEENTH;

            case 32:
                return TGDuration.THIRTY_SECOND;

            case 64:
                return TGDuration.SIXTY_FOURTH;

            default:

                debug(
                        "unsupported duration denominator="
                        + denominator
                        + ", fallback to 1/16");

                return TGDuration.SIXTEENTH;
        }
    }

    /*
     * ----------------------------------------------------------------------
     * Debug quantizer result
     * ----------------------------------------------------------------------
     */

    private void debugQuantizedMeasure(
            int measureIndex,
            AsciiTabQuantizer.QuantizedMeasure measure) {

        if (!isDebugEnabled()) {
            return;
        }

        debug(
                "QUANTIZED measure #"
                + (measureIndex + 1));

        debug(
                "  columns="
                + measure.getStartColumn()
                + ".."
                + measure.getEndColumn());

        debug(
                "  onsets="
                + measure.getOnsetColumns());

        debug(
                "  markers="
                + measure.getMarkers());

        debug(
                "  spans="
                + measure.getSpans());

        debug(
                "  prefer="
                + measure.getPreferMask());

        debug(
                "  baseUnits="
                + measure.getBaseUnits());

        debug(
                "  chosenUnits="
                + measure.getChosenUnits());

        for (int i = 0;
                i < measure.getEvents().size();
                i++) {

            AsciiTabQuantizer.QuantizedEvent event =
                    measure.getEvents().get(i);

            debug(
                    "  qevent["
                    + i
                    + "] "
                    + (event.isRest()
                            ? "REST"
                            : "NOTES")
                    + " duration=1/"
                    + event.getDenominator()
                    + (event.isDotted()
                            ? "."
                            : "")
                    + " sourceUnits="
                    + event.getSourceUnits()
                    + " notes="
                    + event.getNotes());
        }
    }
}
