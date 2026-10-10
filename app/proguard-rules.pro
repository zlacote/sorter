# JNI resolves native methods by class/method name; keep them stable.
-keepclasseswithmembernames class com.athea.app.engine.PtyBridge {
    native <methods>;
}

# kotlinx.serialization — keep serializable models (Journal, Storage).
-keepattributes *Annotation*, InnerClasses, EnclosingMethod
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep @kotlinx.serialization.Serializable class **
-keepclassmembers class ** {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclasseswithmembers class ** {
    @kotlinx.serialization.Serializable <init>(...);
}
-dontwarn kotlinx.serialization.**
