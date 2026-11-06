# Stack trace leggibili nei report di errore
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Le enum salvate per nome nel database (Source) devono mantenere i nomi
-keepclassmembers enum com.stafir.** { *; }

# Avvisi innocui di librerie opzionali
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.checkerframework.**
