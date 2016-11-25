# Add attribute specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/younghokim/Library/Android/sdk/sources/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any attribute specific keep options here:

# If your attribute uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

-keepattributes Signature

-dontwarn okio.**

-dontwarn org.apache.commons.math3.**

-dontwarn io.nlopez.smartlocation.rx.**