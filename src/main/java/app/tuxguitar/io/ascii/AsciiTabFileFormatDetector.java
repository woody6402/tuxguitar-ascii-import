package app.tuxguitar.io.ascii;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import app.tuxguitar.io.base.TGFileFormat;
import app.tuxguitar.io.base.TGFileFormatDetector;

/**
 * Detector for ASCII tablature files.
 */
public class AsciiTabFileFormatDetector implements TGFileFormatDetector {

    private static final TGFileFormat FILE_FORMAT =
            new TGFileFormat(
                    "ASCII Tablature",
                    "application/x-ascii-tab",
                    new String[]{"txt", "tab", "ascii"});

    @Override
    public TGFileFormat getFileFormat(InputStream inputStream) {

        try {
            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    inputStream,
                                    "UTF-8"));

            int tabLines = 0;
            int linesRead = 0;

            String line;

            while ((line = reader.readLine()) != null
                    && linesRead < 100) {

                linesRead++;

                if (looksLikeTabLine(line)) {
                    tabLines++;

                    /*
                     * A real guitar TAB should quickly contain
                     * several string lines.
                     */
                    if (tabLines >= 4) {
                        return FILE_FORMAT;
                    }
                }
            }

        } catch (Throwable throwable) {
            // Not ASCII TAB.
        }

        return null;
    }

    private boolean looksLikeTabLine(String line) {

        if (line == null) {
            return false;
        }

        String text = line.trim();

        if (text.length() < 6) {
            return false;
        }

        int firstPipe = text.indexOf('|');

        if (firstPipe < 0) {
            return false;
        }

        /*
         * Typical labelled string:
         *
         * E|--------
         * B|---10---
         * D|--------
         */
        String prefix =
                text.substring(
                        0,
                        firstPipe)
                        .trim();

        if (prefix.length() < 1
                || prefix.length() > 2) {

            return false;
        }

        char note =
                Character.toUpperCase(
                        prefix.charAt(0));

        if ("ABCDEFG".indexOf(note) < 0) {
            return false;
        }

        String body =
                text.substring(
                        firstPipe + 1);

        int tabChars = 0;

        for (int i = 0; i < body.length(); i++) {

            char ch = body.charAt(i);

            if (ch == '-'
                    || ch == '|'
                    || Character.isDigit(ch)
                    || ch == 'x'
                    || ch == 'X'
                    || ch == 'h'
                    || ch == 'p'
                    || ch == '/'
                    || ch == '\\'
                    || ch == '~') {

                tabChars++;
            }
        }

        return body.length() >= 5
                && tabChars >= body.length() / 2;
    }
}
