FFMPEG_PATH="/Users/geckour/develop/git/ffmpeg"
FFMPEG_MODULE_PATH="/Users/geckour/develop/android/git/ExoPlayer/extensions/ffmpeg/src/main"
NDK_PATH="/Users/geckour/develop/android/sdk/ndk/21.0.6113669"
HOST_PLATFORM="darwin-x86_64"

ENABLED_DECODERS=(vorbis opus flac alac pcm_mulaw pcm_alaw mp3 amrnb amrwb aac ac3 eac3 dca mlp truehd)

cd "${FFMPEG_MODULE_PATH}/jni" && \
ln -s "$FFMPEG_PATH" ffmpeg

cd "${FFMPEG_MODULE_PATH}/jni" && \
./build_ffmpeg.sh \
  "${FFMPEG_MODULE_PATH}" "${NDK_PATH}" "${HOST_PLATFORM}" "${ENABLED_DECODERS[@]}"

cd "${FFMPEG_MODULE_PATH}/jni" &&
  ${NDK_PATH}/ndk-build APP_ABI="armeabi-v7a arm64-v8a x86" -j12