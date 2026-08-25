package app.tuxguitar.io.ascii;

import app.tuxguitar.io.base.TGFileFormatDetector;
import app.tuxguitar.io.base.TGFileFormat;
import app.tuxguitar.io.base.TGSongReader;
import app.tuxguitar.io.plugin.TGSongReaderPlugin;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.plugin.TGPluginException;

/**
 * Plugin for ASCII tablature import functionality.
 */
public class AsciiTabSongReaderPlugin extends TGSongReaderPlugin {
    
    public static final String MODULE_ID = "tuxguitar-ascii-import";
    
    public AsciiTabSongReaderPlugin() {
        super(true); // This is a common file format
    }
    
    @Override
    public String getModuleId() {
        return MODULE_ID;
    }
    
    @Override
    protected TGSongReader createInputStream(TGContext context) throws TGPluginException {
        return new AsciiTabSongReader();
    }
    
    @Override
    protected TGFileFormatDetector createFileFormatDetector(TGContext context) throws TGPluginException {
        return new AsciiTabFileFormatDetector();
    }
}