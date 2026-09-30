/*
 * Resonance engine plugin ABI, version 1.
 *
 * A sound engine is a shared library (.so) exporting the six C functions below.
 * The app loads it with dlopen(), so the engine can be built with any toolchain
 * as long as it exports this C interface.
 */
#ifndef AE_PLUGIN_H
#define AE_PLUGIN_H

#ifdef __cplusplus
extern "C" {
#endif

#define AE_ABI_VERSION 1

#if defined(_WIN32)
#define AE_EXPORT __declspec(dllexport)
#else
#define AE_EXPORT __attribute__((visibility("default")))
#endif

typedef struct AeEngine AeEngine;

AE_EXPORT int ae_abi_version(void);
AE_EXPORT AeEngine* ae_create(int sample_rate, int channels);
AE_EXPORT void ae_destroy(AeEngine* engine);
AE_EXPORT void ae_set_param(AeEngine* engine, const char* id, float value);
AE_EXPORT void ae_process(AeEngine* engine, float* interleaved, int frames);
AE_EXPORT void ae_reset(AeEngine* engine);

#ifdef __cplusplus
}
#endif

#endif /* AE_PLUGIN_H */
