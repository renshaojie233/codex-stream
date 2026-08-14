# Codex Stream

Codex Stream is the low-latency hardware streaming companion for
[Codex Pocket](https://github.com/renshaojie233/codex-pocket). It keeps the
Moonlight Android streaming core and adds authenticated one-tap host setup,
automatic Sunshine pairing through each target's Tailscale-only gateway, and
1080p/lowest-latency defaults. The Android package id is
`com.codexpocket.stream`.

The dedicated launcher shows only Workstation, Agilex, and RSJ PC and each
host exposes a single Desktop entry. Touch input follows a Mac-style trackpad
layout: one finger moves the pointer, two fingers scroll, and a two-finger tap
is right click. For the most compatible drag gesture, tap once, touch down a
second time, then move without lifting; long-press-and-move also drags. Optional
three-finger dragging remains available but is not required. Streaming quality,
frame rate, and bitrate remain adjustable in Settings.

An unobtrusive floating keyboard button opens the Android IME and sends
committed text (including composed Unicode text), Backspace, and Enter directly
to Sunshine. Three-finger dragging owns the complete press/move/release
sequence, while a stationary three-finger tap remains a keyboard shortcut.
Two-finger taps are recognized independently from pointer ordering, which keeps
secondary click reliable on Xiaomi/HyperOS while preserving two-finger scroll.

On Xiaomi/Redmi tablets, the optional “Prioritize three-finger dragging” switch
can temporarily disable HyperOS three-finger screenshot and split-screen
gestures only while a stream is active. The original system gesture values are
restored when the stream closes. Android asks for “Modify system settings” when
the switch is enabled because these gestures are owned by HyperOS rather than
the foreground app.

This project is a GPL-3.0 fork of Moonlight Android. Upstream authors and the
original project information are preserved below.

## Upstream: Moonlight Android

[![AppVeyor Build Status](https://ci.appveyor.com/api/projects/status/232a8tadrrn8jv0k/branch/master?svg=true)](https://ci.appveyor.com/project/cgutman/moonlight-android/branch/master)
[![Translation Status](https://hosted.weblate.org/widgets/moonlight/-/moonlight-android/svg-badge.svg)](https://hosted.weblate.org/projects/moonlight/moonlight-android/)

[Moonlight for Android](https://moonlight-stream.org) is an open source client for NVIDIA GameStream and [Sunshine](https://github.com/LizardByte/Sunshine).

Moonlight for Android will allow you to stream your full collection of games from your Windows PC to your Android device,
whether in your own home or over the internet.

Moonlight also has a [PC client](https://github.com/moonlight-stream/moonlight-qt) and [iOS/tvOS client](https://github.com/moonlight-stream/moonlight-ios).

You can follow development on our [Discord server](https://moonlight-stream.org/discord) and help translate Moonlight into your language on [Weblate](https://hosted.weblate.org/projects/moonlight/moonlight-android/).

## Downloads
* [Google Play Store](https://play.google.com/store/apps/details?id=com.limelight)
* [Amazon App Store](https://www.amazon.com/gp/product/B00JK4MFN2)
* [F-Droid](https://f-droid.org/packages/com.limelight)
* [APK](https://github.com/moonlight-stream/moonlight-android/releases)

## Building
* Install Android Studio and the Android NDK
* Run ‘git submodule update --init --recursive’ from within moonlight-android/
* In moonlight-android/, create a file called ‘local.properties’. Add an ‘ndk.dir=’ property to the local.properties file and set it equal to your NDK directory.
* Build the APK using Android Studio or gradle

## Authors

* [Cameron Gutman](https://github.com/cgutman)  
* [Diego Waxemberg](https://github.com/dwaxemberg)  
* [Aaron Neyer](https://github.com/Aaronneyer)  
* [Andrew Hennessy](https://github.com/yetanothername)

Moonlight is the work of students at [Case Western](http://case.edu) and was
started as a project at [MHacks](http://mhacks.org).
