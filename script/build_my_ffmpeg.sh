EXO_ROOT="/Users/geckour/develop/Git/ExoPlayer"
FFMPEG_EXT_PATH="$EXO_ROOT/extensions/ffmpeg/src/main/jni"
NDK_PATH="/Users/geckour/develop/Misc/Android/sdk/ndk/21.0.6113669"
HOST_PLATFORM="darwin-x86_64"

ENABLED_DECODERS=(vorbis opus flac alac pcm_mulaw pcm_alaw mp3 aac eac3 dca mlp truehd)

cd "${FFMPEG_EXT_PATH}" &&
  ./build_ffmpeg.sh \
    "${FFMPEG_EXT_PATH}" "${NDK_PATH}" "${HOST_PLATFORM}" "${ENABLED_DECODERS[@]}"

cd "${FFMPEG_EXT_PATH}" &&
  ${NDK_PATH}/ndk-build APP_ABI="armeabi-v7a arm64-v8a x86" -j12
