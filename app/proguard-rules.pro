-keep class com.ningguang.skip.data.** { *; }
-keep class com.ningguang.skip.engine.Rule { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
