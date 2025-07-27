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

-keep,allowoptimization class de.binarynoise.** {
    public static void main(***);
}

-keepclassmembers,allowoptimization class ** extends androidx.viewbinding.ViewBinding {
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
