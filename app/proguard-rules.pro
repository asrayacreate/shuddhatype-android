# The engine is pure data + logic with no reflection; default shrinking is safe.
# Assets are read by name from AssetManager, so they are never touched by R8.
-keep class com.shuddhatype.ime.ShuddhaTypeService { *; }
