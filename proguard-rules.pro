# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

-dontobfuscate
-allowaccessmodification
-dontskipnonpubliclibraryclasses
-dontskipnonpubliclibraryclassmembers

-keepattributes SourceFile, LineNumberTable, Exception, *Annotation*, InnerClasses, EnclosingMethod, Signature

-keepclasseswithmembers,allowoptimization class de.binarynoise.** {
    public <methods>;
    public <fields>;
}

-keep class de.binarynoise.** {
    public static void main(***);
}


-keepclassmembers, allowoptimization class ** extends androidx.viewbinding.ViewBinding {
	public static ** inflate(android.view.LayoutInflater);
	public static ** bind(android.view.View);
}

-keep,allowoptimization class ** implements de.robv.android.xposed.IXposedHookLoadPackage
-keep,allowoptimization class ** implements de.robv.android.xposed.IXposedHookInitPackageResources
-keep,allowoptimization class ** implements de.robv.android.xposed.IXposedHookZygoteInit

#noinspection ShrinkerUnresolvedReference
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int d(...);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
    public static int println(...);
}

-dontwarn java.beans.**
-dontwarn java.lang.**
-dontwarn com.google.android.gms.**
-dontwarn org.checkerframework.checker.**
-dontwarn j$.**

# Okhttp

# JSR 305 annotations are for embedding nullability information.
-dontwarn javax.annotation.**

# A resource is loaded with a relative path so the package of this class must be preserved.
-keeppackagenames okhttp3.internal.publicsuffix.*
-adaptresourcefilenames okhttp3/internal/publicsuffix/PublicSuffixDatabase.gz

# Animal Sniffer compileOnly dependency to ensure APIs are compatible with older versions of Java.
-dontwarn org.codehaus.mojo.animal_sniffer.*

# OkHttp platform used only on JVM and when Conscrypt and other security providers are available.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.jspecify.annotations.**

# netty
-dontwarn com.aayushatharva.brotli4j.**
-dontwarn com.fasterxml.jackson.databind.**
-dontwarn com.github.luben.zstd.**
-dontwarn com.jcraft.jzlib.**
-dontwarn io.netty.internal.tcnative.**
-dontwarn io.vertx.codegen.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn com.google.auto.service.AutoService
