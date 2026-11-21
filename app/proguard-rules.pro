# kotlinx.serialization ships its own consumer rules. Keep catalog models readable in crash reports.
-keepattributes SourceFile,LineNumberTable

# WireGuard's tunnel library is called from its own native code and ships no rules of its own.
-keep class com.wireguard.** { *; }
