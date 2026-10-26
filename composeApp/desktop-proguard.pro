# Gson persists model field names; JNA resolves native structures and methods by reflection.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
-keep class com.dccleaner.app.** { *; }
-keep class com.sun.jna.** { *; }

# Optional TLS providers, GraalVM native-image hooks, and jsoup regex backend.
-dontwarn org.graalvm.**
-dontwarn com.oracle.svm.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn com.google.re2j.**
