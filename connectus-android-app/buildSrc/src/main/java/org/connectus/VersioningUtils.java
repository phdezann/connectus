package org.connectus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class VersioningUtils {

    public static class GitInfo {
        String versionMajor;
        String versionMinor;
        String versionPatch;
        String versionBuild;
        String gitSha;

        public GitInfo(String versionMajor, String versionMinor, String versionPatch, String versionBuild, String gitSha) {
            this.versionMajor = versionMajor;
            this.versionMinor = versionMinor;
            this.versionPatch = versionPatch;
            this.versionBuild = versionBuild;
            this.gitSha = gitSha;
        }
    }

    private static GitInfo getInfo() throws Exception {
        String output = execute("git -C . describe --tags --long").trim();

        String[] allValues = output.split("-");
        String fullVersionTag = allValues[0];
        String versionBuild = allValues[1];
        String gitSha = allValues[2];

        String[] versionValues = fullVersionTag.split("\\.");
        String versionMajor = versionValues[0];
        String versionMinor = versionValues[1];
        String versionPatch = versionValues[2];

        return new GitInfo(versionMajor, versionMinor, versionPatch, versionBuild, gitSha);
    }

    public static String gitSha() throws Exception {
        return getInfo().gitSha;
    }

    public static String versionName() throws Exception {
        GitInfo gi = getInfo();
        return String.format("%s.%s.%s(%s)", gi.versionMajor, gi.versionMinor, gi.versionPatch, gi.versionBuild);
    }

    public static int versionCode() throws Exception {
        GitInfo gi = getInfo();
        return Integer.parseInt(gi.versionMajor) * 100000 +
                Integer.parseInt(gi.versionMinor) * 10000 +
                Integer.parseInt(gi.versionPatch) * 1000 +
                Integer.parseInt(gi.versionBuild);
    }

    public static String execute(String cmd) throws IOException {
        InputStream is = null;
        InputStreamReader isr = null;
        BufferedReader br = null;
        try {
            Process process = Runtime.getRuntime().exec(cmd);
            is = process.getInputStream();
            isr = new InputStreamReader(is);
            br = new BufferedReader(isr);
            StringBuffer sb = new StringBuffer();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } finally {
            if (br != null) {
                br.close();
            }
            if (isr != null) {
                isr.close();
            }
            if (is != null) {
                is.close();
            }
        }
    }
}
