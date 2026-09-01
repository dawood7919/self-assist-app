# Compose and AndroidX ship their own consumer rules; this file only holds
# project-specific keeps.

# Keep line numbers for readable release crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# PDFBox loads parsers and font resources reflectively, and ships optional
# hooks for classes that are not on Android at all.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn com.tom_roush.fontbox.**
-dontwarn org.bouncycastle.**
-dontwarn javax.**
-dontwarn java.awt.**
