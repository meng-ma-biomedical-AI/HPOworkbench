package org.monarch.hpoapi.cmd;

import java.util.ArrayList;
import java.util.List;

import net.sourceforge.argparse4j.inf.Namespace;


/**
 * Configuration for database-related commands in HPOAPI (adapted from Manuel Holtgrewe's code in Jannovar)
 *
 * @author <a href="mailto:manuel.holtgrewe@bihealth.de">Manuel Holtgrewe</a>
 */
public class HPODBOptions extends HPOAPIBaseOptions {

    /**
     * paths to INI files ot use for parsing
     */
    public List<String> dataSourceFiles = new ArrayList<>();

    public List<String> getDataSourceFiles() {
        return dataSourceFiles;
    }

    public void setDataSourceFiles(List<String> dataSourceFiles) {
        this.dataSourceFiles = dataSourceFiles;
    }

    @Override
    public void setFromArgs(Namespace args) throws CommandLineParsingException {
        super.setFromArgs(args);

        dataSourceFiles = args.getList("data_source_list");
    }

    @Override
    public String toString() {
        return "HPODBOptions [dataSourceFiles=" + dataSourceFiles + ", isReportProgress()=" + isReportProgress()
                + ", getHttpProxy()=" + getHttpProxy() + ", getHttpsProxy()=" + getHttpsProxy() + ", getFtpProxy()="
                + getFtpProxy() + "]";
    }

}
