# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /opt/local/phdezann/android-sdk-linux/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

-keepattributes Signature
-keepattributes *Annotation*

-keep class org.connectus.** { *; }
-keep class rx.internal.util.unsafe.** { *; }
-keep class retrofit.http.** { *; }

-dontwarn java.**
-dontwarn javax.**
-dontwarn sun.misc.**
-dontwarn android.net.http.**
-dontwarn org.joda.convert.**
-dontwarn com.google.appengine.api.urlfetch.**
-dontwarn com.google.android.gms.**
-dontwarn org.apache.http.**
-dontwarn org.w3c.dom.bootstrap.**
-dontwarn dagger.internal.**
-dontwarn retrofit.client.**
-dontwarn com.jcraft.jzlib.**
-dontwarn org.ietf.**
-dontwarn com.squareup.**

-dontwarn org.junit.**
-dontwarn org.codehaus.mojo.**
-dontwarn org.assertj.**
