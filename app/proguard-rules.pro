# Tesseract4Android: JNI обращается к полям/методам по именам (нет consumer-правил в AAR)
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }
-keepclasseswithmembernames class * { native <methods>; }
# Компоненты из манифеста AAPT сохраняет сам; оставим имена наших классов для читаемых стектрейсов
-keepattributes SourceFile,LineNumberTable
-dontobfuscate
