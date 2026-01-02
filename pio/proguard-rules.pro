-keep class com.omarea.common.** { *; }
-keep class com.omarea.krscript.** { *; }

-keepclassmembers class **$Companion {
    *;
}

-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes Signature

-keepclassmembers class * implements java.io.Serializable {
    *;
}
