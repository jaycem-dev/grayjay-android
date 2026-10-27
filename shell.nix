# Development environment for building the Grayjay Android app via nix.
# Usage: nix-shell shell.nix [--run 'command']
#
# Note: intended for x86_64-linux hosts (Google ships x86_64-only Linux build
# tools). Evaluates the system nixpkgs (<nixpkgs>).
let
  pkgs = import <nixpkgs> {
    config.allowUnfree = true;            # android sdk cmdline tools
    config.android_sdk.accept_license = true;
  };

  # Android SDK: AGP 8.13 defaults to build-tools 35.0.0; compileSdk 36;
  # the included futopay build wants platform 34.
  android = pkgs.androidenv.composeAndroidPackages {
    buildToolsVersions = [ "35.0.0" "36.0.0" ];
    platformVersions = [ "34" "36" ];
    includeNDK = false;
    includeCmake = false;
    includeEmulator = false;
  };
in
pkgs.mkShell {
  ANDROID_SDK_ROOT = "${android.androidsdk}/libexec/android-sdk";
  JAVA_HOME = "${pkgs.jdk21}";
  buildInputs = with pkgs; [ jdk21 git-lfs ];

  shellHook = ''
    # AGP downloads its own aapt2 from Maven. That binary is built for a
    # generic glibc Linux and cannot run on NixOS, so use the aapt2 from the
    # Nix-provided Android SDK instead.
    export GRADLE_OPTS="-Dorg.gradle.project.android.aapt2FromMavenOverride=$ANDROID_SDK_ROOT/build-tools/35.0.0/aapt2 ''${GRADLE_OPTS:-}"
  '';
}
