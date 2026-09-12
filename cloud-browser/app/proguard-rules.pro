# JSch (SSH) uses reflection for some socket/cipher internals.
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**
-dontwarn org.slf4j.**
