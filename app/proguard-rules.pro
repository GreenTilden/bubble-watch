# Wear OS Compose app - default rules are sufficient

# Gson (de)serializes these DTOs by reflection over field names; keep them
# so R8 minify does not rename fields and break JSON mapping.
-keep class com.darney.bubblewatch.data.** { *; }
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses
-keep class com.google.gson.** { *; }
-dontwarn okhttp3.**
-dontwarn retrofit2.**
