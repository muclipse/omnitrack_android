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

-keepattributes Signature, *Annotation*

-keepclassmembers class * extends java.lang.Enum {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepnames class * extends kr.ac.snu.hcil.omnitrack.ui.activities.OTFragment

-dontwarn okio.**

-dontwarn org.apache.commons.math3.**
-dontwarn java.beans.**
-dontwarn org.apache.commons.beanutils.**
-dontwarn org.apache.commons.lang3.**
-dontwarn org.apache.commons.collections.**


-dontwarn io.nlopez.smartlocation.rx.**

-dontwarn sun.misc.**

-keepclassmembers class rx.internal.util.unsafe.*ArrayQueue*Field* {
   long producerIndex;
   long consumerIndex;
}

-keepclassmembers class rx.internal.util.unsafe.BaseLinkedQueueProducerNodeRef {
    rx.internal.util.atomic.LinkedQueueNode producerNode;
}

-keepclassmembers class rx.internal.util.unsafe.BaseLinkedQueueConsumerNodeRef {
    rx.internal.util.atomic.LinkedQueueNode consumerNode;
}

-dontnote rx.internal.util.PlatformDependent

# Class names are needed in reflection
-keepnames class com.amazonaws.**
# Request handlers defined in request.handlers
-keep class com.amazonaws.services.**.*Handler
# The following are referenced but aren't required to run
-dontwarn com.fasterxml.jackson.**
-dontwarn org.apache.commons.logging.**
# Android 6.0 release removes support for the Apache HTTP client
-dontwarn org.apache.http.**
# The SDK has several references of Apache HTTP client
-dontwarn com.amazonaws.http.**
-dontwarn com.amazonaws.metrics.**


# Platform calls Class.forName on types which do not exist on Android to determine platform.
-dontnote retrofit2.Platform
# Platform used when running on RoboVM on iOS. Will not be used at runtime.
-dontnote retrofit2.Platform$IOS$MainThreadExecutor
# Platform used when running on Java 8 VMs. Will not be used at runtime.
-dontwarn retrofit2.Platform$Java8

-dontwarn retrofit.**
# Retain generic type information for use by reflection by converters and adapters.
-keepattributes Signature
# Retain declared checked exceptions for use by a Proxy instance.
-keepattributes Exceptions

# Glide settings
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public enum com.bumptech.glide.load.resource.bitmap.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}

-keep public class com.google.android.gms.location.DetectedActivity
-dontwarn com.google.android.gms.location.DetectedActivity

-dontwarn com.google.errorprone.annotations.*
-dontwarn com.beloo.widget.chipslayoutmanager.Orientation

-dontwarn kr.ac.snu.hcil.omnitrack.core.visualization.models.DurationHeatMapModel

# for DexGuard only
#-keepresourcexmlelements manifest/application/meta-data@value=GlideModule
# end Glide settings