package org.monarchinitiative.hpoworkbench.io;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.monarchinitiative.hpoworkbench.exception.HPOWorkbenchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashSet;
import java.util.Set;


/**
 * Helper class for downloading files over HTTP and FTP.
 *
 * The implementation of FTP downloads is more complex since we need passive FTP transfer through firewalls. This is not
 * possible when just opening a stream through an {@link URL} object with Java's builtin features.
 *
 * @author <a href="mailto:manuel.holtgrewe@charite.de">Manuel Holtgrewe</a>
 */
public class FileDownloader {
    private static final Logger logger = LoggerFactory.getLogger(FileDownloader.class);
    static class ProxyOptions {
        String host = null;
        int port = -1;
        String user = null;
        String password = null;
    }

    /**
     * Configuration for the {@link FileDownloader}.
     */
    static class Options {
        boolean printProgressBar = false;
        ProxyOptions http = new ProxyOptions();
        ProxyOptions https = new ProxyOptions();
        ProxyOptions ftp = new ProxyOptions();
    }

    /** configuration for the downloader */
    private final Options options;

    /** Initializer FileDownloader with the given options string */
    public FileDownloader(Options options) {
        this.options = options;
    }

    /** Downloader with default options */
    FileDownloader() {
        options=new Options();
    }

    /**
     * This method downloads a file to the specified local file path. If the file already exists, it will
     * overwrite it and emit a warning.
     *
     * @param src
     *            {@link URL} with file to download
     * @param dest
     *            {@link File} with destination path
     * @return <code>true</code> if the file was downloaded and <code>false</code> if not.
     * @throws HPOWorkbenchException
     *             on problems with downloading
     */
    boolean copyURLToFile(URL src, File dest) throws HPOWorkbenchException {
        if (dest.exists()) {
            logger.warn("Overwriting file at "+dest);
        }
        logger.trace("copyURLToFile dest="+dest);
        logger.trace("dest.getParentFile()="+dest.getParentFile());
        if (!dest.getParentFile().exists()) {
            logger.info("Creating directory {}"+ dest.getParentFile().getAbsolutePath());
            dest.getParentFile().mkdirs();
        }

        if (src.getProtocol().equals("ftp") && options.ftp.host != null)
            return copyURLToFileWithFTP(src, dest);
        else
            return copyURLToFileThroughURL(src, dest);
    }

    private boolean copyURLToFileWithFTP(URL src, File dest) throws HPOWorkbenchException {
        final FTPClient ftp = new FTPClient();
        ftp.enterLocalPassiveMode(); // passive mode for firewalls

        try {
            if (src.getPort() != -1)
                ftp.connect(src.getHost(), src.getPort());
            else
                ftp.connect(src.getHost());
            if (!ftp.login("anonymous", "anonymous@example.com"))
                throw new IOException("Could not login with anonymous:anonymous@example.com");
            if (!ftp.isConnected())
                logger.error("Weird, not connected!");
        } catch (IOException e) {
            throw new HPOWorkbenchException("ERROR: problem connecting when downloading file.", e);
        }
        try {
            ftp.setFileType(FTP.BINARY_FILE_TYPE); // binary file transfer
        } catch (IOException e) {
            try {
                ftp.logout();
                ftp.disconnect();
            } catch (IOException e1) {
                // swallow, nothing we can do about it
            }
            throw new HPOWorkbenchException("ERROR: could not use binary transfer.", e);
        }
        InputStream in = null;
        OutputStream out = null;
        try {
            final String parentDir = new File(src.getPath()).getParent().substring(1);
            final String fileName = new File(src.getPath()).getName();
            if (!ftp.changeWorkingDirectory(parentDir))
                throw new FileNotFoundException("Could not change directory to " + parentDir);
            // Try to get file size.
            FTPFile[] files = ftp.listFiles(fileName);
            long fileSize = -1;
            for (FTPFile ftpfile : files) {
                if (ftpfile.getName().equals(fileName)) {
                    fileSize=ftpfile.getSize();
                    break;
                }
            }
            ftp.pwd();

            // Download file.
            in = ftp.retrieveFileStream(fileName);
            if (in == null)
                throw new FileNotFoundException("Could not open connection for file " + fileName);
            out = new FileOutputStream(dest);
            BufferedInputStream inBf = new BufferedInputStream(in);
            byte [] buffer = new byte[128 * 1024];
            int readCount;
            long pos = 0;


            while ((readCount = inBf.read(buffer)) > 0) {
                out.write(buffer, 0, readCount);
                pos += readCount;
            }
            in.close();
            out.close();

        } catch (FileNotFoundException e) {
            dest.delete();
            try {
                ftp.logout();
            } catch (IOException e1) {
                // swallow, nothing we can do about it
            }
            try {
                ftp.disconnect();
            } catch (IOException e1) {
                // swallow, nothing we can do about it
            }
            throw new HPOWorkbenchException("ERROR: problem downloading file.", e);
        } catch (IOException e) {
            dest.delete();
            try {
                ftp.logout();
                ftp.disconnect();
            } catch (IOException e1) {
                // swallow, nothing we can do about it
            }
            throw new HPOWorkbenchException("ERROR: problem downloading file.", e);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    // swallow, nothing we can do
                }
            }
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    // swallow, nothing we can do
                }
            }
        }
        return false;
    }

    /**
     * Copy contents of a URL to a file using the {@link URL} class.
     *
     * This works for the HTTP and the HTTPS protocol and for FTP through a proxy. For plain FTP, we need to use the
     * passive mode.
     */
    private boolean copyURLToFileThroughURL(URL src, File dest) throws HPOWorkbenchException {
        setProxyProperties();

        // actually copy the file
        BufferedInputStream in;
        FileOutputStream out;
        try {
            int connectionTimeout = 5000; // 5 seconds should be more than enough to connect to a server
            final String TEXTPLAIN_REQUEST_TYPE = ", text/plain; q=0.1";
            String actualAcceptHeaders = TEXTPLAIN_REQUEST_TYPE;
            URLConnection connection =  connect(src.openConnection(),connectionTimeout,actualAcceptHeaders,new HashSet<>());
            final int fileSize = connection.getContentLength();
            in = new BufferedInputStream(connection.getInputStream());
            out = new FileOutputStream(dest);

            // Download file.
            byte [] buffer = new byte[128 * 1024];
            int readCount;
            long pos = 0;

            while ((readCount = in.read(buffer)) > 0) {
                out.write(buffer, 0, readCount);
                pos += readCount;
            }
            in.close();
            out.close();
        } catch (IOException | IllegalStateException e) {
            logger.error(String.format("Failed to downloaded file from %s",src.getHost()),e);
            throw new HPOWorkbenchException("ERROR: Problem downloading file: " + e.getMessage());
        }
        return true;
    }


    protected static URLConnection connect(URLConnection conn, int connectionTimeout, String acceptHeaders, Set<String> visited)
            throws IOException {
        if (conn instanceof HttpURLConnection con) {
            // follow redirects to HTTPS
            con.connect();
            int responseCode = con.getResponseCode();
            // redirect
            if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                    || responseCode == HttpURLConnection.HTTP_MOVED_PERM
                    || responseCode == HttpURLConnection.HTTP_SEE_OTHER
                    // no constants for temporary and permanent redirect in HttpURLConnection
                    || responseCode == 307 || responseCode == 308) {
                String location = con.getHeaderField("Location");
                if (visited.add(location)) {
                    URL newURL = new URL(location);
                    return connect(rebuildConnection(connectionTimeout, newURL, acceptHeaders),
                            connectionTimeout, acceptHeaders, visited);
                } else {
                    throw new IllegalStateException(
                            "Infinite loop: redirect cycle detected. " + visited);
                }
            }
        }
        return conn;
    }

    protected static URLConnection rebuildConnection(int connectionTimeout, URL newURL, String acceptHeaders) throws IOException {
        URLConnection conn;
        conn = newURL.openConnection();
        final String ACCEPTABLE_CONTENT_ENCODING = "xz,gzip,deflate";
        conn.addRequestProperty("Accept", acceptHeaders);
        conn.setRequestProperty("Accept-Encoding", ACCEPTABLE_CONTENT_ENCODING);
        conn.setConnectTimeout(connectionTimeout);
        return conn;
    }




    /**
     * Set system properties from {@link #options}.
     */
    private void setProxyProperties() {
        if (options.ftp.host != null)
            System.setProperty("ftp.proxyHost", options.ftp.host);
        if (options.ftp.port != -1)
            System.setProperty("ftp.proxyPort", Integer.toString(options.ftp.port));
        if (options.ftp.user != null)
            System.setProperty("ftp.proxyUser", options.ftp.user);
        if (options.ftp.password != null)
            System.setProperty("ftp.proxyPassword", options.ftp.password);

        if (options.http.host != null)
            System.setProperty("http.proxyHost", options.http.host);
        if (options.http.port != -1)
            System.setProperty("http.proxyPort", Integer.toString(options.http.port));
        if (options.http.user != null)
            System.setProperty("http.proxyUser", options.http.user);
        if (options.http.password != null)
            System.setProperty("http.proxyPassword", options.http.password);

        if (options.https.host != null)
            System.setProperty("https.proxyHost", options.https.host);
        if (options.https.port != -1)
            System.setProperty("https.proxyPort", Integer.toString(options.https.port));
        if (options.https.user != null)
            System.setProperty("https.proxyUser", options.https.user);
        if (options.https.password != null)
            System.setProperty("https.proxyPassword", options.https.password);
    }

}

