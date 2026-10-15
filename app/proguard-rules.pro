# Disable obfuscation (we use Proguard exclusively for optimization)
-dontobfuscate

# Keep `Companion` object fields of serializable classes.
# This avoids serializer lookup through `getDeclaredClasses` as done for named companion objects.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep `serializer()` on companion objects (both default and named) of serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` of serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# @Serializable and @Polymorphic are used at runtime for polymorphic serialization.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

-keep class dev.patrickgold.florisboard.lib.fleet.IFleetHub { *; }
-keep class dev.patrickgold.florisboard.lib.fleet.IFleetHub$Stub { *; }
-keep class dev.patrickgold.florisboard.ime.ai.LlamaNative { *; }
-keep interface dev.patrickgold.florisboard.ime.ai.TokenSink { *; }
-keepclassmembers class * implements dev.patrickgold.florisboard.ime.ai.TokenSink {
    boolean onToken(java.lang.String);
}
