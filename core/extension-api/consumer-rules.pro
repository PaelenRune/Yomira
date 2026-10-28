# Extension DEXes resolve these classes by exact name at class-load time;
# renaming or removing them breaks in-process linking.
-keep class eu.kanade.tachiyomi.** { *; }
