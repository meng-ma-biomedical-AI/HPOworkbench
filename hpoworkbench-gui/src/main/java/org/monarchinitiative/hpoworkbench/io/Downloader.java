package org.monarchinitiative.hpoworkbench.io;

import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * This class is used to download files to the local file system of the user (chromFa.tar.gz and refGene.txt.gz).
 * @author Peter Robinson
 * @version 0.2.0 (2017-10-20)
 */
public class Downloader extends Task<Void> {
    private static final Logger logger = LoggerFactory.getLogger(Downloader.class);

    /**
     * The absolute path to the place (directory) where the downloaded file will be
     * saved in the local filesystem.*/
    private final File localDir;

    /**
     * The full local path of the file we will download. It should be set to be identical
     * to {@link #localDir} except for the final file base name.
     */
    private final File localFilePath;


    /** This is the URL of the file we want to download */
    private final String urlstring;

    public Downloader(File directoryPath, String url, String basename) {
        this.localDir = directoryPath;
        this.urlstring=url;
        this.localFilePath = new File(this.localDir + File.separator + basename);
    }

    public Downloader(String path, String url, String basename) {
        this(new File(path),url,basename);
    }

    private File getLocalFilePath() { return  this.localFilePath; }

    /**
     * This method downloads a file to the specified local file path. If the file already exists, it emits a warning
     * message and does nothing.
     */
    @Override
    protected Void call() throws Exception {
        logger.debug("[INFO] Downloading: \"" + urlstring + "\"");
        try {
            URL url = new URL(urlstring);
            FileDownloader fileDownloader = new FileDownloader();
            fileDownloader.copyURLToFile(url,localFilePath);

        } catch (MalformedURLException e) {
            updateProgress(0.00, 1);
            throw new Exception(String.format("Malformed url: \"%s\"\n%s", urlstring, e));
        } catch (IOException e) {
            updateProgress(0.00, 1);
            throw new Exception(String.format("IO Exception reading from URL: \"%s\" to local file \"%s\"\n%s", urlstring,localFilePath, e.toString()));
        } catch (Exception e){
            updateProgress(0.00, 1);
            throw new Exception(e.getMessage());
        }
        updateProgress(1.000, 1.000); /* show 100% completion */
        return null;
    }


}
