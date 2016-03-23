import groovy.transform.TupleConstructor

public class VersioningUtils {

    @TupleConstructor()
    private static class GitInfo {
        String versionMajor
        String versionMinor
        String versionPatch
        String versionBuild
        String gitSha
    }

    private static def getInfo() {
        def longVersionName = "git -C . describe --tags --long".execute().text.trim()
        def (fullVersionTag, versionBuild, gitSha) = longVersionName.tokenize('-')
        def (versionMajor, versionMinor, versionPatch) = fullVersionTag.tokenize('.')
        new GitInfo(versionMajor, versionMinor, versionPatch, versionBuild, gitSha)
    }

    public static def gitSha() {
        def gi = getInfo()
        gi.gitSha
    }

    public static def versionName() {
        def gi = getInfo()
        "${gi.versionMajor}.${gi.versionMinor}.${gi.versionPatch}($gi.versionBuild)"
    }

    public static def versionCode() {
        def gi = getInfo()
        gi.versionMajor.toInteger() * 100000 +
                gi.versionMinor.toInteger() * 10000 +
                gi.versionPatch.toInteger() * 1000 +
                gi.versionBuild.toInteger()
    }
}
