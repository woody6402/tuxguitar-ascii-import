package app.tuxguitar.io.ascii;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import app.tuxguitar.io.base.TGFileFormatException;
import app.tuxguitar.io.base.TGFileFormat;
import app.tuxguitar.io.base.TGSongReader;
import app.tuxguitar.io.base.TGSongReaderHandle;
import app.tuxguitar.song.factory.TGFactory;
import app.tuxguitar.song.models.TGSong;

/**
 * Reader for ASCII tablature files (.txt, .tab, .ascii).
 */
public class AsciiTabSongReader implements TGSongReader {
    
    private static final TGFileFormat FILE_FORMAT = new TGFileFormat("ASCII Tablature", 
        "application/x-ascii-tab", new String[]{"txt", "tab", "ascii"});
    
    @Override
    public TGFileFormat getFileFormat() {
        return FILE_FORMAT;
    }
    
    @Override
    public void read(TGSongReaderHandle handle) throws TGFileFormatException {
        try (InputStream inputStream = handle.getInputStream()) {
            AsciiTabParser parser = new AsciiTabParser();
            List<AsciiTabParser.TabBlock> blocks = parser.parseTabBlocks(inputStream);
            
            if (blocks.isEmpty()) {
                throw new TGFileFormatException("No valid tab blocks found in file");
            }
            
            // Build the song model
            AsciiTabSongBuilder builder =
                    new AsciiTabSongBuilder(
                            handle.getFactory());            
            
            TGSong song =
                    builder.buildSong(
                            blocks,
                            "ASCII Tablature",
                            parser.getTimeNumerator(),
                            parser.getTimeDenominator(),
                            parser.getTempo(),
                            parser.getTitle(),
                            parser.getArtist(),
                            parser.getAlbum(),
                            parser.getAuthor(),
                            parser.getDate(),
                            parser.getCopyright(),
                            parser.getWriter(),
                            parser.getTranscriber(),
                            parser.getComments());
            
            handle.setSong(song);
        } catch (IOException e) {
            throw new TGFileFormatException("Error reading ASCII tab file", e);
        } catch (Exception e) {
            throw new TGFileFormatException("Error parsing ASCII tab file", e);
        }
    }
}
