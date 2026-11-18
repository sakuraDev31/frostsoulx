// Host integration regression tests exercise the actual Android JNI bridge.
// Build: g++ -std=c++17 -I<engine include> -I<JDK include> -I<JDK include/linux>
// tools/test_stereo_surround_tuning.cpp <engine static library> -pthread -o <test>
#include "../app/src/main/cpp/frostsoulx_immersive_jni.cpp"
#include <cstring>
#include <cstdio>
#include <cstdlib>
#include <vector>

namespace {
void require(bool ok, const char* message) {
    if (!ok) { std::fprintf(stderr, "FAIL: %s\n", message); std::exit(1); }
}
struct Buffer { void* data; jlong bytes; };
std::vector<jdouble> telemetry;
void* JNICALL bufferAddress(JNIEnv*, jobject object) { return reinterpret_cast<Buffer*>(object)->data; }
jlong JNICALL bufferCapacity(JNIEnv*, jobject object) { return reinterpret_cast<Buffer*>(object)->bytes; }
jdoubleArray JNICALL newArray(JNIEnv*, jsize count) {
    telemetry.assign(static_cast<std::size_t>(count), 0.0);
    return reinterpret_cast<jdoubleArray>(&telemetry);
}
void JNICALL setArray(JNIEnv*, jdoubleArray, jsize start, jsize count, const jdouble* values) {
    std::copy(values, values + count, telemetry.begin() + start);
}
void process(JNIEnv* env, jlong address, void* data, jlong bytes, int frames, int encoding) {
    Buffer buffer{data, bytes};
    Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeProcess(
        env, nullptr, address, reinterpret_cast<jobject>(&buffer), frames, encoding);
}
}
int main() {
    JNINativeInterface_ functions{};
    functions.GetDirectBufferAddress = bufferAddress;
    functions.GetDirectBufferCapacity = bufferCapacity;
    functions.NewDoubleArray = newArray;
    functions.SetDoubleArrayRegion = setArray;
    JNIEnv_ env{&functions};
    const auto address = Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeCreate(&env, nullptr, 48000, 4);
    require(address != 0, "native prepare");
    auto& handle = *reinterpret_cast<Handle*>(address);
    for (int id = 0; id <= 12; ++id) {
        Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeSetRoomPreset(&env, nullptr, address, id);
        require(handle.roomPreset.load() == id, "all UI preset IDs survive JNI mapping");
        require(handle.engine.activeBrir().valid(), "preset has a valid BRIR");
    }
    std::vector<float> pcm(770, 0.3f);
    pcm[0] = std::numeric_limits<float>::quiet_NaN();
    const auto original = pcm;
    process(&env, address, pcm.data(), static_cast<jlong>(pcm.size() * sizeof(float)), 385, 4);
    require(std::memcmp(pcm.data(), original.data(), pcm.size() * sizeof(float)) == 0, "float OFF bypass is byte-identical including NaN");
    Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeSetRoomPreset(&env, nullptr, address, 6);
    const auto oldPosition = handle.engine.activeSpaceProfile().sourcePosition();
    Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeSetSource(&env, nullptr, address, 45, 20, 0.5f);
    const auto position = handle.engine.activeSpaceProfile().sourcePosition();
    require((position - oldPosition).length() > 0.001f, "azimuth/elevation map to physical source");
    Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeSetCarFader(&env, nullptr, address, -1);
    require((handle.engine.activeSpaceProfile().sourcePosition() - position).length() > 0.001f, "fader changes source geometry");
    Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeSetEnabled(&env, nullptr, address, JNI_TRUE);
    Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeSetSpatialBlend(&env, nullptr, address, 1);
    Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeSetBassWidth(&env, nullptr, address, 2);
    Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeSetBassGainDb(&env, nullptr, address, 12);
    require(handle.bassGainDb.load() == 6, "bass boost respects new engine range");
    process(&env, address, pcm.data(), static_cast<jlong>(pcm.size() * sizeof(float)), 385, 4);
    for (float value : pcm) require(std::isfinite(value) && std::fabs(value) <= 0.98001f, "float native output is finite and safe");
    Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeReadDiagnostics(&env, nullptr, address);
    require(telemetry.size() == 61, "legacy telemetry field layout retained");
    require(telemetry[41] == 3 && telemetry[42] == 210 && telemetry[43] == 1, "engine identity latency and readiness mapped");
    require(telemetry[56] > 0 && telemetry[57] > 0 && telemetry[60] > 0 && telemetry[60] <= 1, "matrix telemetry measured");
    require(telemetry[23] > 0, "nonfinite input counted");
    const auto before = pcm;
    process(&env, address, pcm.data(), 8, 385, 4);
    require(pcm == before, "undersized direct buffer is rejected without writes");
    Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeResetDiagnostics(&env, nullptr, address);
    require(handle.resetDiagnosticsRequested.load(), "diagnostic reset deferred to audio thread");
    process(&env, address, pcm.data(), static_cast<jlong>(pcm.size() * sizeof(float)), 385, 4);
    require(handle.diagnostics.processedFrames.load() == 385, "reset consumed at PCM boundary");
    Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeRelease(&env, nullptr, address);

    const auto pcm16Address = Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeCreate(&env, nullptr, 48000, 2);
    require(pcm16Address != 0, "PCM16 prepare");
    std::vector<std::int16_t> integers{32767, -32768, 0, -1, 1, 16384};
    const auto integerOriginal = integers;
    process(&env, pcm16Address, integers.data(), 12, 3, 2);
    require(integers == integerOriginal, "PCM16 OFF bypass is byte-identical at boundaries");
    Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeSetEnabled(&env, nullptr, pcm16Address, JNI_TRUE);
    process(&env, pcm16Address, integers.data(), 12, 3, 2);
    auto& pcm16Handle = *reinterpret_cast<Handle*>(pcm16Address);
    require(std::abs(static_cast<int>(quantizePcm16(pcm16Handle, 1))) >= 32766, "PCM16 positive saturation");
    require(quantizePcm16(pcm16Handle, -1) <= -32767, "PCM16 negative saturation");
    require(std::abs(static_cast<int>(quantizePcm16(pcm16Handle, std::numeric_limits<float>::infinity()))) <= 1, "PCM16 infinity safe conversion");
    Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeRelease(&env, nullptr, pcm16Address);
    std::puts("PASS: JNI preset mapping, geometry, bypass, conversion, telemetry and buffer validation");
}
