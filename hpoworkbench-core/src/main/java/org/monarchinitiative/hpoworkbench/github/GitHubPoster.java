package org.monarchinitiative.hpoworkbench.github;

import org.apache.commons.codec.binary.Base64;
import org.json.simple.JSONValue;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;


/**
 * The purpose of this class is to post an issue to the HPO GitHub issue tracker.
 * The user of the software must have a valid GitHub user name and password.
 * TODO we only use the JSON library to format the string. Write our own function to reduce
 * dependency on the external library and make the app smaller.
 * @author <a href="mailto:peter.robinson@jax.org">Peter Robinson</a>
 * @version 0.2.13
 */
public class GitHubPoster {
    /** GitHub username. */
    private final String username;
    /** GitHub password. */
    private String password;
    /** The contents of the GitHub issue we want to create.. */
    private String payload;
    /** The HTML response code of the GitHub server. */
    private int responsecode;
    /** THe response message of the GitHub server. */
    private String response=null;

    private String githubLabel=null;

    private List<String> githubLabels = null;

    private final String githubTitle;

    private final String githubBody;
    /** if this is set to true, then we output the messages to shell but do not execute. */
    private boolean dryrun=false;



    /**  @return the response of the GitHub server following our attempt to create a new issue*/
    public String getHttpResponse() { return String.format("%s [code: %d]",response,responsecode);}


    public GitHubPoster(String uname, String passw, String title, String messagebody) {
        this.password = passw;
        this.username = uname;
        githubTitle=title;
        githubBody=messagebody;
        this.payload=formatPayload(title,messagebody);
    }

    @Deprecated
    public void setLabel(String l) {
        this.githubLabel=l;
        reformatPayloadWithLabel(githubLabel);
    }

    public void setLabel(List<String> labels) {
        this.githubLabels = labels.stream().map(JSONValue::escape).collect(Collectors.toList());
        reformatPayloadWithLabel(this.githubLabels);
    }

    public void setDryRun() {
        this.dryrun = true;
    }



    /** This escapes new lines, quotation mark etc. in order to have correct JSON */
    private String jsonFormat(String s) {
        return JSONValue.escape(s);
    }


    /**
     * Change a list of string into a Json array
     * @param labels a list of GitHub isseu tracker labels
     * @return A JSONified String with a list of GitHub labels
     */
    private String labelsArray4Json(List<String> labels) {
        if (labels.size() == 1) {
            return "\"" + labels.get(0) + "\"";
        }
        if (labels.size() > 1) {
            StringBuilder sb = new StringBuilder();
            sb.append("\"").append(labels.get(0)).append("\"");
            for (int i = 1; i < labels.size(); i++) {
                sb.append(", \"").append(labels.get(i)).append("\"");
            }
            return sb.toString();
        }
        return null;
    }


    private void reformatPayloadWithLabel(String label) {
        this.payload = String.format("""
                        {
                        "title": "%s",
                        "body": "%s",
                        "labels": [ "%s"] }""",
                JSONValue.escape(this.githubTitle),
                JSONValue.escape(this.githubBody),
                JSONValue.escape(this.githubLabel));
    }

    private void reformatPayloadWithLabel(List<String> labels) {
        this.payload = String.format("""
                        {
                        "title": "%s",
                        "body": "%s",
                        "labels": [ %s] }""",
                JSONValue.escape(this.githubTitle),
                JSONValue.escape(this.githubBody),
                labelsArray4Json(this.githubLabels));
    }



    private String formatPayload(String title, String messagebody) {
        return String.format("""
                        {
                        "title": "%s",
                        "body": "%s"}""",
                JSONValue.escape(title),JSONValue.escape(messagebody));
    }



    public void postHpoIssue() throws Exception {
        URL url = new URL("https://api.github.com/repos/obophenotype/human-phenotype-ontology/issues");
        URLConnection con = url.openConnection();
        String userpass = String.format("%s:%s",username,password);
        //String token = replace with GitHub AUTH token;
        String token = "???-REPLACEME-???";
        token = token + ":x-oauth-basic";
        String authString = "Basic " + Base64.encodeBase64String(token.getBytes());

        HttpURLConnection http = (HttpURLConnection)con;
        http.setRequestMethod("POST"); // PUT is another valid option
        http.setDoOutput(true);
        byte[] out = payload.getBytes(StandardCharsets.UTF_8);
        if (dryrun) {
            System.err.println(payload);
            return;
        }
        int length = out.length;
        http.setFixedLengthStreamingMode(length);
        http.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        http.setRequestProperty("Authorization",authString);
        http.connect();
        try(OutputStream os = http.getOutputStream()) {
            os.write(out);
        }
        if (http.getResponseCode()==400) {
            String erro=String.format("URL:%s\nPayload=%s\nServer response: %s [%d]",
                    http,
                    payload,
                    http.getResponseMessage(),
                    http.getResponseCode());
            throw new Exception(erro);
        } else {
            this.response=http.getResponseMessage();
            this.responsecode=http.getResponseCode();
        }
    }


    public void postMondoIssue() throws Exception {
        URL url = new URL("https://api.github.com/repos/monarch-initiative/mondo-build/issues");
        URLConnection con = url.openConnection();
        String userpass = String.format("%s:%s",username,password);
        String basicAuth = "Basic " + javax.xml.bind.DatatypeConverter.printBase64Binary(userpass.getBytes());
        HttpURLConnection http = (HttpURLConnection)con;
        http.setRequestMethod("POST"); // PUT is another valid option
        http.setDoOutput(true);
        byte[] out = payload.getBytes(StandardCharsets.UTF_8);
        int length = out.length;
        http.setFixedLengthStreamingMode(length);
        http.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        http.setRequestProperty("Authorization",basicAuth);
        http.connect();
        try(OutputStream os = http.getOutputStream()) {
            os.write(out);
        }
        if (http.getResponseCode()==400) {
            String erro=String.format("URL:%s\nPayload=%s\nServer response: %s [%d]",
                    http,
                    payload,
                    http.getResponseMessage(),
                    http.getResponseCode());
            throw new Exception(erro);
        } else {
            this.response=http.getResponseMessage();
            this.responsecode=http.getResponseCode();
        }
    }


    @Override
    public String toString() {
        return this.githubTitle + " " + this.githubBody;
    }

}
