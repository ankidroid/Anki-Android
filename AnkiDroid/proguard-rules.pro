# Add project specific ProGuard rules here.
# By default
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html
#
# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Samsung Android 4.2 bug workaround
# https://code.google.com/p/android/issues/detail?id=78377
-keepattributes **
-keep class !android.support.v7.view.menu.**,!android.support.design.internal.NavigationMenu,!android.support.design.internal.NavigationMenuPresenter,!android.support.design.internal.NavigationSubMenu,** {*;}

# AnkiDroid specific settings
-dontpreverify
-dontoptimize
-dontshrink
-dontwarn **
-dontnote **

# JUnit problems with new Gradle? https://github.com/googlesamples/android-testing/issues/179
-keep @org.junit.runner.RunWith public class *

# Other testing related settings
-keep class android.support.test.**
-keep class android.support.multidex.**
-keep class org.junit.**
-ignorewarnings