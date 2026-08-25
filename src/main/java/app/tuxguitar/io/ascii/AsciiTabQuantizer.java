package app.tuxguitar.io.ascii;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Quantization logic ported from tab2gp5/txt2gp5.py.
 *
 * Responsibilities:
 *
 * ASCII measure segment
 *      -> onset columns
 *      -> markers
 *      -> spans / events / prefer mask
 *      -> proportional quantization
 *      -> musical durations
 *
 * This class deliberately does NOT know anything about TuxGuitar's TGSong.
 * It produces a neutral musical representation for AsciiTabSongBuilder.
 */
public class AsciiTabQuantizer {

    public static final int DEFAULT_QUARTER_UNITS = 4;
    public static final int DEFAULT_LEADING_SILENCE_TOL = 2;

    private static final int[] DEFAULT_BASES =
            new int[]{16, 32, 64};

    private final int leadingSilenceTolerance;
    private final int quarterUnits;
    private final int[] bases;

    public AsciiTabQuantizer() {
        this(
                DEFAULT_LEADING_SILENCE_TOL,
                DEFAULT_QUARTER_UNITS,
                DEFAULT_BASES);
    }

    public AsciiTabQuantizer(
            int leadingSilenceTolerance,
            int quarterUnits,
            int[] bases) {

        this.leadingSilenceTolerance =
                Math.max(0, leadingSilenceTolerance);

        this.quarterUnits =
                Math.max(1, quarterUnits);

        if (bases == null || bases.length == 0) {
            this.bases = DEFAULT_BASES.clone();
        } else {
            this.bases = bases.clone();
        }
    }

    /**
     * Fixed-meter version corresponding to:
     *
     * build_song(... fixed_meter=(numerator, denominator),
     *            quarter_units=4)
     *
     * in txt2gp5.py.
     */
    public QuantizedMeasure quantizeMeasure(
            AsciiTabParser.TabBlock block,
            AsciiTabParser.MeasureSegment segment,
            int numerator,
            int denominator) {

        if (block == null) {
            throw new IllegalArgumentException("block is null");
        }

        if (segment == null) {
            throw new IllegalArgumentException("segment is null");
        }

        if (numerator <= 0 || denominator <= 0) {
            throw new IllegalArgumentException(
                    "Invalid time signature: "
                    + numerator
                    + "/"
                    + denominator);
        }

        return quantizeInternal(
                block,
                segment,
                Integer.valueOf(numerator),
                Integer.valueOf(denominator));
    }

    /**
     * Adaptive 16 -> 32 -> 64 version corresponding to the old
     * txt2gp5 mode without fixed_meter.
     */
    public QuantizedMeasure quantizeMeasureAdaptive(
            AsciiTabParser.TabBlock block,
            AsciiTabParser.MeasureSegment segment) {

        return quantizeInternal(
                block,
                segment,
                null,
                null);
    }

    private QuantizedMeasure quantizeInternal(
            AsciiTabParser.TabBlock block,
            AsciiTabParser.MeasureSegment segment,
            Integer numerator,
            Integer denominator) {

        int measureStart =
                segment.getStartColumn();

        int measureEnd =
                segment.getEndColumn();

        QuantizedMeasure result =
                new QuantizedMeasure(
                        measureStart,
                        measureEnd);

        /*
         * Python:
         *
         * onset_cols = sorted(
         *     c for c in chord_set
         *     if m_start <= c < m_end
         * )
         */
        List<Integer> onsetColumns =
                new ArrayList<Integer>();

        for (Integer value :
                block.getChordColumns()) {

            int column =
                    value.intValue();

            if (column >= measureStart
                    && column < measureEnd) {

                onsetColumns.add(
                        Integer.valueOf(column));
            }
        }

        Collections.sort(onsetColumns);

        result.setOnsetColumns(
                onsetColumns);

        /*
         * Empty / invalid measure.
         */
        if (measureEnd <= measureStart) {

            result.getEvents().add(
                    QuantizedEvent.rest(
                            1,
                            false));

            return result;
        }

        /*
         * Python:
         *
         * markers = [m_start] + onset_cols + [m_end]
         */
        List<Integer> markers =
                new ArrayList<Integer>();

        markers.add(
                Integer.valueOf(measureStart));

        markers.addAll(
                onsetColumns);

        markers.add(
                Integer.valueOf(measureEnd));

        /*
         * Python:
         *
         * c0 = m_start
         * skipped = 0
         *
         * while c0 < m_end and skipped < LEADING_SILENCE_TOL:
         *     if is_bar_column(block, c0):
         *         c0 += 1
         *         continue
         *
         *     if extract_chord_at(..., c0):
         *         break
         *
         *     c0 += 1
         *     skipped += 1
         */
        int c0 =
                measureStart;

        int skipped =
                0;

        while (c0 < measureEnd
                && skipped < this.leadingSilenceTolerance) {

            if (isBarColumn(
                    block,
                    c0)) {

                c0++;
                continue;
            }

            if (!extractChordAt(
                    block,
                    c0).isEmpty()) {

                break;
            }

            c0++;
            skipped++;
        }

        /*
         * Python:
         *
         * if skipped:
         *     markers[0] = c0
         *
         *     if len(markers) >= 2
         *          and markers[0] >= markers[1]:
         *         markers.pop(0)
         *
         *     if len(markers) == 1
         *          or markers[0] >= markers[-1]:
         *         continue
         */
        if (skipped > 0) {

            markers.set(
                    0,
                    Integer.valueOf(c0));

            if (markers.size() >= 2
                    && markers.get(0).intValue()
                    >= markers.get(1).intValue()) {

                markers.remove(0);
            }

            if (markers.size() == 1
                    || markers.get(0).intValue()
                    >= markers.get(
                            markers.size() - 1)
                            .intValue()) {

                return result;
            }
        }

        result.setMarkers(
                markers);

        /*
         * Python:
         *
         * spans = []
         * events = []
         * prefer = []
         */
        List<Integer> spans =
                new ArrayList<Integer>();

        List<List<ParsedNote>> events =
                new ArrayList<List<ParsedNote>>();

        List<Boolean> prefer =
                new ArrayList<Boolean>();

        for (int i = 0;
                i < markers.size() - 1;
                i++) {

            int a =
                    markers.get(i)
                            .intValue();

            int b =
                    markers.get(i + 1)
                            .intValue();

            spans.add(
                    Integer.valueOf(
                            Math.max(
                                    0,
                                    b - a)));

            /*
             * Python:
             *
             * if a in chord_set:
             */
            if (onsetColumns.contains(
                    Integer.valueOf(a))) {

                List<ParsedNote> chord =
                        extractChordAt(
                                block,
                                a);

                events.add(
                        chord.isEmpty()
                                ? null
                                : chord);

                prefer.add(
                        Boolean.TRUE);

            } else {

                events.add(null);

                prefer.add(
                        Boolean.FALSE);
            }
        }

        result.setSpans(spans);
        result.setPreferMask(prefer);

        List<Integer> chosenUnits;

        /*
         * Fixed time signature:
         *
         * measure_units =
         *     round(
         *       quarter_units *
         *       (numer * 4.0 / denom)
         *     )
         */
        if (numerator != null
                && denominator != null) {

            int measureUnits =
                    (int) Math.round(
                            this.quarterUnits
                            * (numerator.intValue()
                            * 4.0
                            / denominator.intValue()));

            measureUnits =
                    Math.max(
                            1,
                            measureUnits);

            chosenUnits =
                    quantizeProportional(
                            spans,
                            measureUnits,
                            prefer);

            /*
             * Python:
             *
             * if is_onset and sp > 0 and u <= 0:
             *     chosen_units[i] = 1
             */
            for (int i = 0;
                    i < chosenUnits.size();
                    i++) {

                if (prefer.get(i)
                        .booleanValue()
                        && spans.get(i)
                        .intValue() > 0
                        && chosenUnits.get(i)
                        .intValue() <= 0) {

                    chosenUnits.set(
                            i,
                            Integer.valueOf(1));
                }
            }

            result.setBaseUnits(
                    measureUnits);

            result.setChosenUnits(
                    chosenUnits);

            /*
             * Python:
             *
             * split_into_durations_units_q(
             *       n_units,
             *       quarter_units)
             */
            createFixedMeterEvents(
                    result,
                    chosenUnits,
                    events);

        } else {

            /*
             * Adaptive basis:
             *
             * for base in DEFAULT_BASES:
             *     units = quantize_proportional(...)
             *
             *     every onset must receive > 0
             */
            int chosenBase =
                    -1;

            chosenUnits =
                    null;

            for (int base :
                    this.bases) {

                List<Integer> units =
                        quantizeProportional(
                                spans,
                                base,
                                prefer);

                boolean valid =
                        true;

                for (int i = 0;
                        i < units.size();
                        i++) {

                    if (prefer.get(i)
                            .booleanValue()
                            && spans.get(i)
                            .intValue() > 0
                            && units.get(i)
                            .intValue() <= 0) {

                        valid = false;
                        break;
                    }
                }

                if (valid) {

                    chosenBase =
                            base;

                    chosenUnits =
                            units;

                    break;
                }
            }

            if (chosenUnits == null) {

                chosenBase =
                        this.bases[
                                this.bases.length - 1];

                chosenUnits =
                        quantizeProportional(
                                spans,
                                chosenBase,
                                prefer);
            }

            result.setBaseUnits(
                    chosenBase);

            result.setChosenUnits(
                    chosenUnits);

            createAdaptiveEvents(
                    result,
                    chosenUnits,
                    events,
                    chosenBase);
        }

        return result;
    }

    /*
     * ----------------------------------------------------------------------
     * Fixed-meter event creation
     * ----------------------------------------------------------------------
     */

    private void createFixedMeterEvents(
            QuantizedMeasure measure,
            List<Integer> chosenUnits,
            List<List<ParsedNote>> sourceEvents) {

        for (int i = 0;
                i < chosenUnits.size();
                i++) {

            int units =
                    chosenUnits.get(i)
                            .intValue();

            if (units <= 0) {
                continue;
            }

            List<DurationSpec> durations =
                    splitIntoDurationsUnitsQ(
                            units,
                            this.quarterUnits);

            List<ParsedNote> chord =
                    sourceEvents.get(i);

            for (DurationSpec duration :
                    durations) {

                if (chord != null
                        && !chord.isEmpty()) {

                    measure.getEvents().add(
                            QuantizedEvent.notes(
                                    duration.getDenominator(),
                                    duration.isDotted(),
                                    units,
                                    chord));

                } else {

                    measure.getEvents().add(
                            QuantizedEvent.rest(
                                    duration.getDenominator(),
                                    duration.isDotted()));
                }
            }
        }
    }

    /*
     * ----------------------------------------------------------------------
     * Adaptive event creation
     * ----------------------------------------------------------------------
     */

    private void createAdaptiveEvents(
            QuantizedMeasure measure,
            List<Integer> chosenUnits,
            List<List<ParsedNote>> sourceEvents,
            int chosenBase) {

        for (int i = 0;
                i < chosenUnits.size();
                i++) {

            int units =
                    chosenUnits.get(i)
                            .intValue();

            if (units <= 0) {
                continue;
            }

            List<DurationSpec> durations =
                    splitIntoDurationsUnits(
                            units,
                            chosenBase);

            List<ParsedNote> chord =
                    sourceEvents.get(i);

            for (DurationSpec duration :
                    durations) {

                if (chord != null
                        && !chord.isEmpty()) {

                    measure.getEvents().add(
                            QuantizedEvent.notes(
                                    duration.getDenominator(),
                                    duration.isDotted(),
                                    units,
                                    chord));

                } else {

                    measure.getEvents().add(
                            QuantizedEvent.rest(
                                    duration.getDenominator(),
                                    duration.isDotted()));
                }
            }
        }
    }

    /*
     * ----------------------------------------------------------------------
     * quantize_proportional()
     * ----------------------------------------------------------------------
     */

    public List<Integer> quantizeProportional(
            List<Integer> spans,
            int baseUnits,
            List<Boolean> preferMask) {

        int count =
                spans.size();

        List<Integer> units =
                new ArrayList<Integer>();

        if (count == 0
                || baseUnits <= 0) {

            return units;
        }

        int total =
                0;

        for (Integer span :
                spans) {

            total +=
                    span.intValue();
        }

        /*
         * Python total <= 0 fallback.
         */
        if (total <= 0) {

            for (int i = 0;
                    i < count;
                    i++) {

                units.add(
                        Integer.valueOf(0));
            }

            int index =
                    0;

            for (int i = 0;
                    i < preferMask.size();
                    i++) {

                if (preferMask.get(i)
                        .booleanValue()) {

                    index = i;
                    break;
                }
            }

            units.set(
                    index,
                    Integer.valueOf(
                            baseUnits));

            return units;
        }

        final double[] proportions =
                new double[count];

        int assigned =
                0;

        for (int i = 0;
                i < count;
                i++) {

            int span =
                    spans.get(i)
                            .intValue();

            proportions[i] =
                    span
                    * (double) baseUnits
                    / (double) total;

            int value =
                    (span == 0)
                            ? 0
                            : (int) proportions[i];

            units.add(
                    Integer.valueOf(value));

            assigned +=
                    value;
        }

        int remainder =
                baseUnits
                - assigned;

        /*
         * Same residual concept as Python.
         */
        List<Residual> residuals =
                new ArrayList<Residual>();

        for (int i = 0;
                i < count;
                i++) {

            residuals.add(
                    new Residual(
                            i,
                            proportions[i]
                            - (int) proportions[i],
                            preferMask.get(i)
                                    .booleanValue()));
        }

        if (remainder > 0) {

            Collections.sort(
                    residuals,
                    new Comparator<Residual>() {

                        @Override
                        public int compare(
                                Residual left,
                                Residual right) {

                            /*
                             * Python:
                             *
                             * ((0 if prefer else 1), -residual)
                             */
                            if (left.isPreferred()
                                    != right.isPreferred()) {

                                return left.isPreferred()
                                        ? -1
                                        : 1;
                            }

                            return Double.compare(
                                    right.getResidual(),
                                    left.getResidual());
                        }
                    });

            int k =
                    0;

            while (remainder > 0
                    && count > 0) {

                int index =
                        residuals.get(
                                k % count)
                                .getIndex();

                units.set(
                        index,
                        Integer.valueOf(
                                units.get(index)
                                .intValue()
                                + 1));

                remainder--;
                k++;
            }

        } else if (remainder < 0) {

            Collections.sort(
                    residuals,
                    new Comparator<Residual>() {

                        @Override
                        public int compare(
                                Residual left,
                                Residual right) {

                            /*
                             * Python:
                             *
                             * ((1 if prefer else 0), residual)
                             */
                            if (left.isPreferred()
                                    != right.isPreferred()) {

                                return left.isPreferred()
                                        ? 1
                                        : -1;
                            }

                            return Double.compare(
                                    left.getResidual(),
                                    right.getResidual());
                        }
                    });

            int k =
                    0;

            while (remainder < 0
                    && count > 0) {

                int index =
                        residuals.get(
                                k % count)
                                .getIndex();

                if (units.get(index)
                        .intValue() > 0) {

                    units.set(
                            index,
                            Integer.valueOf(
                                    units.get(index)
                                    .intValue()
                                    - 1));

                    remainder++;
                }

                k++;
            }
        }

        return units;
    }

    /*
     * ----------------------------------------------------------------------
     * duration_table_for_base()
     * ----------------------------------------------------------------------
     */

    public List<DurationChunk> durationTableForBase(
            int baseUnits) {

        List<DurationChunk> table =
                new ArrayList<DurationChunk>();

        if (baseUnits == 16) {

            table.add(new DurationChunk(16, 1, false));
            table.add(new DurationChunk(12, 2, true));
            table.add(new DurationChunk(8, 2, false));
            table.add(new DurationChunk(6, 4, true));
            table.add(new DurationChunk(4, 4, false));
            table.add(new DurationChunk(3, 8, true));
            table.add(new DurationChunk(2, 8, false));
            table.add(new DurationChunk(1, 16, false));

            return table;
        }

        if (baseUnits == 32) {

            table.add(new DurationChunk(32, 1, false));
            table.add(new DurationChunk(24, 2, true));
            table.add(new DurationChunk(16, 2, false));
            table.add(new DurationChunk(12, 4, true));
            table.add(new DurationChunk(8, 4, false));
            table.add(new DurationChunk(6, 8, true));
            table.add(new DurationChunk(4, 8, false));
            table.add(new DurationChunk(3, 16, true));
            table.add(new DurationChunk(2, 16, false));
            table.add(new DurationChunk(1, 32, false));

            return table;
        }

        if (baseUnits == 64) {

            table.add(new DurationChunk(64, 1, false));
            table.add(new DurationChunk(48, 2, true));
            table.add(new DurationChunk(32, 2, false));
            table.add(new DurationChunk(24, 4, true));
            table.add(new DurationChunk(16, 4, false));
            table.add(new DurationChunk(12, 8, true));
            table.add(new DurationChunk(8, 8, false));
            table.add(new DurationChunk(6, 16, true));
            table.add(new DurationChunk(4, 16, false));
            table.add(new DurationChunk(3, 32, true));
            table.add(new DurationChunk(2, 32, false));
            table.add(new DurationChunk(1, 64, false));

            return table;
        }

        throw new IllegalArgumentException(
                "Unsupported base: "
                + baseUnits);
    }

    /*
     * ----------------------------------------------------------------------
     * split_into_durations_units()
     * ----------------------------------------------------------------------
     */

    public List<DurationSpec> splitIntoDurationsUnits(
            int numberOfUnits,
            int baseUnits) {

        List<DurationSpec> result =
                new ArrayList<DurationSpec>();

        int remaining =
                numberOfUnits;

        List<DurationChunk> table =
                durationTableForBase(
                        baseUnits);

        while (remaining > 0) {

            boolean matched =
                    false;

            for (DurationChunk chunk :
                    table) {

                if (chunk.getUnits()
                        <= remaining) {

                    result.add(
                            new DurationSpec(
                                    chunk.getDenominator(),
                                    chunk.isDotted()));

                    remaining -=
                            chunk.getUnits();

                    matched = true;
                    break;
                }
            }

            if (!matched) {
                break;
            }
        }

        return result;
    }

    /*
     * ----------------------------------------------------------------------
     * _make_duration_table_q()
     * ----------------------------------------------------------------------
     */

    public List<DurationChunk> makeDurationTableQ(
            int quarterUnits) {

        final int q =
                Math.max(
                        1,
                        quarterUnits);

        List<DurationChunk> table =
                new ArrayList<DurationChunk>();

        addDurationQ(table, q, 1, false);
        addDurationQ(table, q, 2, true);
        addDurationQ(table, q, 2, false);
        addDurationQ(table, q, 4, true);
        addDurationQ(table, q, 4, false);
        addDurationQ(table, q, 8, true);
        addDurationQ(table, q, 8, false);
        addDurationQ(table, q, 16, true);
        addDurationQ(table, q, 16, false);
        addDurationQ(table, q, 32, false);

        /*
         * Python removes duplicates and sorts descending by chunk.
         */
        List<DurationChunk> unique =
                new ArrayList<DurationChunk>();

        for (DurationChunk candidate :
                table) {

            boolean exists =
                    false;

            for (DurationChunk current :
                    unique) {

                if (candidate.getUnits()
                        == current.getUnits()
                        && candidate.getDenominator()
                        == current.getDenominator()
                        && candidate.isDotted()
                        == current.isDotted()) {

                    exists = true;
                    break;
                }
            }

            if (!exists
                    && candidate.getUnits() > 0) {

                unique.add(candidate);
            }
        }

        Collections.sort(
                unique,
                new Comparator<DurationChunk>() {

                    @Override
                    public int compare(
                            DurationChunk left,
                            DurationChunk right) {

                        return Integer.compare(
                                right.getUnits(),
                                left.getUnits());
                    }
                });

        return unique;
    }

    private void addDurationQ(
            List<DurationChunk> table,
            int quarterUnits,
            int denominator,
            boolean dotted) {

        double units =
                quarterUnits
                * (4.0 / denominator);

        if (dotted) {
            units *= 1.5;
        }

        int rounded =
                Math.max(
                        1,
                        (int) Math.round(units));

        table.add(
                new DurationChunk(
                        rounded,
                        denominator,
                        dotted));
    }

    /*
     * ----------------------------------------------------------------------
     * split_into_durations_units_q()
     * ----------------------------------------------------------------------
     */

    public List<DurationSpec> splitIntoDurationsUnitsQ(
            int numberOfUnits,
            int quarterUnits) {

        List<DurationSpec> result =
                new ArrayList<DurationSpec>();

        List<DurationChunk> table =
                makeDurationTableQ(
                        quarterUnits);

        int remaining =
                numberOfUnits;

        while (remaining > 0) {

            boolean found =
                    false;

            for (DurationChunk chunk :
                    table) {

                if (chunk.getUnits()
                        <= remaining) {

                    result.add(
                            new DurationSpec(
                                    chunk.getDenominator(),
                                    chunk.isDotted()));

                    remaining -=
                            chunk.getUnits();

                    found = true;
                    break;
                }
            }

            /*
             * Same Python fallback:
             * use smallest duration.
             */
            if (!found
                    && !table.isEmpty()) {

                DurationChunk chunk =
                        table.get(
                                table.size() - 1);

                result.add(
                        new DurationSpec(
                                chunk.getDenominator(),
                                chunk.isDotted()));

                remaining -=
                        chunk.getUnits();
            }
        }

        return result;
    }

    /*
     * ----------------------------------------------------------------------
     * TAB helpers
     * ----------------------------------------------------------------------
     */

    private boolean isBarColumn(
            AsciiTabParser.TabBlock block,
            int column) {

        int count =
                0;

        for (String line :
                block.getLines()) {

            if (column < line.length()
                    && line.charAt(column)
                    == '|') {

                count++;
            }
        }

        return count >= 4;
    }

    /**
     * Direct Java equivalent of extract_chord_at().
     */
    private List<ParsedNote> extractChordAt(
            AsciiTabParser.TabBlock block,
            int column) {

        List<ParsedNote> notes =
                new ArrayList<ParsedNote>();

        List<String> lines =
                block.getLines();

        int[] rowMap =
                block.getStringMapping();

        for (int row = 0;
                row < 6
                && row < lines.size();
                row++) {

            String line =
                    lines.get(row);

            if (column >= line.length()) {
                continue;
            }

            char character =
                    line.charAt(column);

            if (Character.isDigit(character)
                    && (column == 0
                    || !Character.isDigit(
                            line.charAt(
                                    column - 1)))) {

                int end =
                        column;

                StringBuilder number =
                        new StringBuilder();

                while (end < line.length()
                        && Character.isDigit(
                                line.charAt(end))) {

                    number.append(
                            line.charAt(end));

                    end++;
                }

                int fret =
                        Integer.parseInt(
                                number.toString());

                int stringNumber =
                        rowMap[row];

                notes.add(
                        new ParsedNote(
                                stringNumber,
                                fret));

            } else if (character == 'x'
                    || character == 'X') {

                int stringNumber =
                        rowMap[row];

                notes.add(
                        new ParsedNote(
                                stringNumber,
                                0));
            }
        }

        return notes;
    }

    /*
     * ----------------------------------------------------------------------
     * Neutral result model
     * ----------------------------------------------------------------------
     */

    public static class QuantizedMeasure {

        private final int startColumn;
        private final int endColumn;

        private List<Integer> onsetColumns;
        private List<Integer> markers;
        private List<Integer> spans;
        private List<Boolean> preferMask;
        private List<Integer> chosenUnits;

        private int baseUnits;

        private final List<QuantizedEvent> events;

        public QuantizedMeasure(
                int startColumn,
                int endColumn) {

            this.startColumn =
                    startColumn;

            this.endColumn =
                    endColumn;

            this.events =
                    new ArrayList<QuantizedEvent>();
        }

        public int getStartColumn() {
            return this.startColumn;
        }

        public int getEndColumn() {
            return this.endColumn;
        }

        public List<Integer> getOnsetColumns() {
            return this.onsetColumns;
        }

        public void setOnsetColumns(
                List<Integer> onsetColumns) {

            this.onsetColumns =
                    onsetColumns;
        }

        public List<Integer> getMarkers() {
            return this.markers;
        }

        public void setMarkers(
                List<Integer> markers) {

            this.markers =
                    markers;
        }

        public List<Integer> getSpans() {
            return this.spans;
        }

        public void setSpans(
                List<Integer> spans) {

            this.spans =
                    spans;
        }

        public List<Boolean> getPreferMask() {
            return this.preferMask;
        }

        public void setPreferMask(
                List<Boolean> preferMask) {

            this.preferMask =
                    preferMask;
        }

        public List<Integer> getChosenUnits() {
            return this.chosenUnits;
        }

        public void setChosenUnits(
                List<Integer> chosenUnits) {

            this.chosenUnits =
                    chosenUnits;
        }

        public int getBaseUnits() {
            return this.baseUnits;
        }

        public void setBaseUnits(
                int baseUnits) {

            this.baseUnits =
                    baseUnits;
        }

        public List<QuantizedEvent> getEvents() {
            return this.events;
        }
    }

    public static class QuantizedEvent {

        private final int denominator;
        private final boolean dotted;
        private final boolean rest;
        private final int sourceUnits;

        private final List<ParsedNote> notes;

        private QuantizedEvent(
                int denominator,
                boolean dotted,
                boolean rest,
                int sourceUnits,
                List<ParsedNote> notes) {

            this.denominator =
                    denominator;

            this.dotted =
                    dotted;

            this.rest =
                    rest;

            this.sourceUnits =
                    sourceUnits;

            this.notes =
                    notes;
        }

        public static QuantizedEvent rest(
                int denominator,
                boolean dotted) {

            return new QuantizedEvent(
                    denominator,
                    dotted,
                    true,
                    0,
                    new ArrayList<ParsedNote>());
        }

        public static QuantizedEvent notes(
                int denominator,
                boolean dotted,
                int sourceUnits,
                List<ParsedNote> notes) {

            return new QuantizedEvent(
                    denominator,
                    dotted,
                    false,
                    sourceUnits,
                    new ArrayList<ParsedNote>(
                            notes));
        }

        public int getDenominator() {
            return this.denominator;
        }

        public boolean isDotted() {
            return this.dotted;
        }

        public boolean isRest() {
            return this.rest;
        }

        public int getSourceUnits() {
            return this.sourceUnits;
        }

        public List<ParsedNote> getNotes() {
            return this.notes;
        }
    }

    public static class ParsedNote {

        private final int stringNumber;
        private final int fret;

        public ParsedNote(
                int stringNumber,
                int fret) {

            this.stringNumber =
                    stringNumber;

            this.fret =
                    fret;
        }

        public int getStringNumber() {
            return this.stringNumber;
        }

        public int getFret() {
            return this.fret;
        }

        @Override
        public String toString() {

            return "s"
                    + this.stringNumber
                    + ":"
                    + this.fret;
        }
    }

    public static class DurationSpec {

        private final int denominator;
        private final boolean dotted;

        public DurationSpec(
                int denominator,
                boolean dotted) {

            this.denominator =
                    denominator;

            this.dotted =
                    dotted;
        }

        public int getDenominator() {
            return this.denominator;
        }

        public boolean isDotted() {
            return this.dotted;
        }
    }

    private static class DurationChunk {

        private final int units;
        private final int denominator;
        private final boolean dotted;

        public DurationChunk(
                int units,
                int denominator,
                boolean dotted) {

            this.units =
                    units;

            this.denominator =
                    denominator;

            this.dotted =
                    dotted;
        }

        public int getUnits() {
            return this.units;
        }

        public int getDenominator() {
            return this.denominator;
        }

        public boolean isDotted() {
            return this.dotted;
        }
    }

    private static class Residual {

        private final int index;
        private final double residual;
        private final boolean preferred;

        public Residual(
                int index,
                double residual,
                boolean preferred) {

            this.index =
                    index;

            this.residual =
                    residual;

            this.preferred =
                    preferred;
        }

        public int getIndex() {
            return this.index;
        }

        public double getResidual() {
            return this.residual;
        }

        public boolean isPreferred() {
            return this.preferred;
        }
    }
}
