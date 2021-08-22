# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Program Files (x86)\Android\android-sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# FIXME remove this entire Android 4.2 workaround from 12/3/15 timrae commit for 2.15.x+
# Samsung Android 4.2 bug workaround
# https://code.google.com/p/android/issues/detail?id=78377
-keepattributes **
-keep class !android.support.v7.view.menu.**,!android.support.design.internal.NavigationMenu,!android.support.design.internal.NavigationMenuPresenter,!android.support.design.internal.NavigationSubMenu,** {*;}
#5806 - Class: ActionBarOverflow
-keep public class android.support.v7.internal.view.menu.** { *; }
-keep public class androidx.appcompat.view.menu.** { *; }
-dontpreverify
-dontoptimize
-dontshrink
-dontwarn **
-dontnote **
