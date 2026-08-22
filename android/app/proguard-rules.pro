# Copyright (c) Meta Platforms, Inc. and affiliates.
# All rights reserved.
#
# This source code is licensed under the license found in the
# LICENSE file in the root directory of this source tree.

# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ---- DedaAI release rules -------------------------------------------------
# NOTE: this R8 config is UNVERIFIED until assembleRelease runs — builds
# happen only on the owner's word (BUILD.md).

# Meta DAT SDK: its reflection/JNI surface is undocumented — keep it whole.
-keep class com.meta.wearable.** { *; }
-dontwarn com.meta.wearable.**

# ---- JNI: R8 DELETED native methods the .so registers by name -------------
# Measured from the SHIPPED 0.1.5 build's own mapping output, 2026-08-22.
# `usage.txt` (R8's list of what it REMOVED) contains 61 native methods,
# among them:
#     com.facebook.wearable.airshield.stream.Framing.packNative(...)
#     androidx.camera.core.ImageProcessingUtil.nativeConvertAndroid420ToBitmap
# plus every `mHybridData` field of the fbjni hybrid classes.
#
# No Java code calls Framing.pack(), so the shrinker dropped it -- but
# libairshield_light_mbed_jni.so still calls RegisterNatives for it by name.
# Registration fails, fbjni raises a JniException, building that exception's
# message throws ClassNotFoundException in a loop, the JNI global reference
# table overflows at 51200 entries and ART aborts the process:
#     E Failed to register native method
#       com.facebook.wearable.airshield.stream.Framing.packNative(
#       Ljava/nio/ByteBuffer;IILjava/nio/ByteBuffer;II)I
#     F Abort message: 'JNI ERROR (app bug): global reference table overflow'
# That is the "opens once, then closes instantly on every later start" crash.
#
# The mechanism is SHRINKING, not obfuscation -- so `-keepclasseswithmember
# NAMES` is NOT enough (it only blocks renaming, it still allows removal).
# The rule below must be `-keepclasseswithmembers`, which makes every class
# holding a native method a shrinking root. It is deliberately global: the
# CameraX hit above proves this is not a Meta-SDK-only problem.
-keepclasseswithmembers,includedescriptorclasses class * {
    native <methods>;
}

# The SDK's transport layer lives under com.facebook.* (airshield, datax,
# mediastream, manifest) and drives fbjni HybridData, whose `mHybridData` and
# `mDestructor` FIELDS are read from C++ by name -- fields the rule above does
# not cover. Keep the whole surface; it is undocumented and reflection-heavy.
-keep class com.facebook.** { *; }
-dontwarn com.facebook.**

# libfb.so's JNI_OnLoad registers com/facebook/xplat/fbglog/FbGlog by name (it
# exports zero Java_* symbols), in the same registration run as
# com.facebook.jni.CpuCapabilitiesJni. R8 deleted all three FbGlog classes in
# 0.1.5 (usage.txt:43794-43796). Probably non-fatal -- glog has a "Failed to
# initialize glog" soft branch, which is why the 0.1.5 abort named
# Framing.packNative and not this -- but a FindClass miss leaves a pending
# exception, and without it the SDK's native logging goes nowhere anyway.
-keep class com.facebook.xplat.** { *; }

# protobuf-lite resolves message fields BY NAME at runtime (RawMessageInfo ->
# MessageSchema.reflectField -> getDeclaredField("mime_")). Nothing keeps the
# DAT SDK's generated messages: protobuf-javalite ships no consumer rules and
# none of the three mwdat AARs carries a proguard.txt. The 0.1.5 build proves
# it, A/B inside one mapping.txt:
#     com.meta.media.stream.proto.AudioConfig.mime_  ->  J    (renamed)
#     com.google.crypto.tink...KeyData.keyValue_     ->  keyValue_   (kept)
# tink keeps its names only because tink ships its own rule (configuration.txt
# :880); datastore likewise (:697). The plain com.google.protobuf the SDK uses
# has no such rule, and the shipped dex still holds the literal "mime_" while
# the field is now J -- a first-parse
#     RuntimeException: Field mime_ for G3.e not found. Known fields are [...]
# This never fired in production because Wearables.initialize() has never once
# survived a minified build, so the session handshake that parses these
# messages is unexercised release code. It would fire the moment the JNI fix
# above let the SDK start. 25 message classes are affected across
# com.meta.media.stream.proto, com.meta.coreux.session.proto, com.oculus.atc,
# com.oculus.snappmanager and com.meta.constellationauth.
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

# fbjni / Meta infra mark their JNI surface with these annotations.
-keep @com.facebook.jni.annotations.DoNotStrip class * { *; }
-keep @com.facebook.proguard.annotations.DoNotStrip class * { *; }
-keepclassmembers class * {
    @com.facebook.jni.annotations.DoNotStrip *;
    @com.facebook.proguard.annotations.DoNotStrip *;
}

# okhttp/okio reference optional platform integrations (Conscrypt etc.).
-dontwarn okhttp3.**
-dontwarn okio.**

# Readable crash stacks from a minified build.
-keepattributes SourceFile,LineNumberTable

# R8-generated missing-class suppressions (compile-only annotations and
# Facebook infer stubs referenced by tink / DAT SDK internals; absent at
# runtime by design). Source: app/build/outputs/mapping/release/missing_rules.txt
-dontwarn com.facebook.common.preconditions.Preconditions
-dontwarn com.facebook.common.stringformat.StringFormatUtil
-dontwarn com.facebook.infer.annotation.Nullsafe
-dontwarn com.facebook.infer.annotation.NullsafeStrict
-dontwarn com.facebook.secure.sanitizer.intf.DataSanitizer
-dontwarn com.google.errorprone.annotations.**
