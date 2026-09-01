# Release builds are not minified (isMinifyEnabled = false), so these rules are a safety net
# rather than load-bearing. Kept so turning minification on later does not silently break the
# exported provider or the boot receiver, which are entered by the framework by name.
-keep class com.gios.brightsteps.provider.StepsProvider { *; }
-keep class com.gios.brightsteps.alarm.SampleReceiver { *; }
