# These proguard rules are only needed when building
# for the combination of testing and release mode
# Certain androidx frameworks that are test-only have
# issues with proguard / minimization in release mode

# First build error in release mode for testing:
#
# ERROR: Missing classes detected while running R8. Please add the missing classes or apply additional keep rules that are generated in /Users/mike/work/ankidroid/Anki-Android/AnkiDroid/build/outputs/mapping/playReleaseAndroidTest/missing_rules.txt.
# ERROR: R8: Missing class com.google.protobuf.GeneratedMessageLite$MergeFromVisitor (referenced from: java.lang.Object com.google.android.apps.common.testing.accessibility.framework.uielement.proto.AndroidFrameworkProtos$LayoutParamsProto.dynamicMethod(com.google.protobuf.GeneratedMessageLite$MethodToInvoke, java.lang.Object, java.lang.Object))
# Missing class com.google.protobuf.GeneratedMessageLite$Visitor (referenced from: java.lang.Object com.google.android.apps.common.testing.accessibility.framework.proto.AccessibilityEvaluationProtos$AccessibilityEvaluation.dynamicMethod(com.google.protobuf.GeneratedMessageLite$MethodToInvoke, java.lang.Object, java.lang.Object) and 19 other contexts)
#
# We are not using automated accessibility testing, so there should be
# no impact for these classes to be missing, ignore them
-dontwarn com.google.protobuf.GeneratedMessageLite$MergeFromVisitor
-dontwarn com.google.protobuf.GeneratedMessageLite$Visitor