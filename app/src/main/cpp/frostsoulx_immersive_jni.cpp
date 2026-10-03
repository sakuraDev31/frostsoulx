#include <jni.h>
#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>
#include <memory>
#include <atomic>
#include "frostsoulx/immersive_audio_engine.h"
struct Handle{frostsoulx::ImmersiveAudioEngine engine;int encoding=0,sampleRate=0,quantum=384;bool enabled=false;float trebleDb=0,outputDb=0,lowL=0,lowR=0;float sourceAzimuth=0,sourceElevation=0,sourceDistance=1,carFader=0;int roomPreset=2;std::array<float,4096> scratch{};};
static float g(float d)noexcept{return std::pow(10.0f,std::clamp(std::isfinite(d)?d:0.0f,-24.0f,12.0f)/20.0f);}
static int16_t r16(const uint8_t*p)noexcept{return(int16_t)((uint16_t)p[0]|((uint16_t)p[1]<<8));}
static void w16(uint8_t*p,float x)noexcept{int q=(int)std::lround(std::clamp(std::isfinite(x)?x:0.0f,-0.98f,0.98f)*32767.0f);uint16_t u=(uint16_t)std::clamp(q,-32768,32767);p[0]=(uint8_t)u;p[1]=(uint8_t)(u>>8);}
static void tone(Handle&h,float*p,int n)noexcept{float tg=g(h.trebleDb),og=g(h.outputDb),a=std::clamp(120.0f/std::max(8000.0f,(float)h.sampleRate),0.005f,0.03f);for(int i=0;i<n;i++){float l=p[2*i]*og,r=p[2*i+1]*og;h.lowL+=a*(l-h.lowL);h.lowR+=a*(r-h.lowR);p[2*i]=h.lowL+(l-h.lowL)*tg;p[2*i+1]=h.lowR+(r-h.lowR)*tg;}}
static frostsoulx::RoomSimulationPreset legacyRoom(int v)noexcept{switch(std::clamp(v,0,5)){case 1:return frostsoulx::RoomSimulationPreset::SmallRoom;case 2:return frostsoulx::RoomSimulationPreset::Studio;case 3:return frostsoulx::RoomSimulationPreset::ConcertHall;case 4:return frostsoulx::RoomSimulationPreset::Cathedral;case 5:return frostsoulx::RoomSimulationPreset::Subway;default:return frostsoulx::RoomSimulationPreset::Off;}}
static void applyRoomPreset(Handle&h,int v)noexcept{
h.roomPreset=std::clamp(v,0,12);
if(h.roomPreset<=5){h.engine.setRoomSimulationPreset(legacyRoom(h.roomPreset));return;}
using P=frostsoulx::spatial::SpaceProfile::Preset;
switch(h.roomPreset){
case 6:h.engine.setSpacePreset(P::ClosedCar);break;
case 7:h.engine.setSpacePreset(P::MediumHall);break;
case 8:h.engine.setSpacePreset(P::SubwayPlatform);break;
case 9:h.engine.setSpacePreset(P::LongTunnel);break;
case 10:h.engine.setSpacePreset(P::OpenRoad);break;
case 11:h.engine.setSpacePreset(P::Cave);break;
case 12:h.engine.setSpacePreset(P::Stadium);break;
default:h.engine.setSpacePreset(P::LivingRoom);break;
}}
static void applySourcePosition(Handle&h)noexcept{
const float az=h.sourceAzimuth*0.0174532925199433f;
const float el=h.sourceElevation*0.0174532925199433f;
const float ce=std::cos(el);
const float x=h.sourceDistance*std::sin(az)*ce;
const float y=h.sourceDistance*std::sin(el);
const float z=h.sourceDistance*std::cos(az)*ce;
h.engine.setSourcePosition(x,y,z);
}

extern "C" JNIEXPORT jlong JNICALL Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeCreate(JNIEnv*,jclass,jint sr,jint enc){if(sr<8000||sr>384000||(enc!=2&&enc!=4))return 0;auto h=std::make_unique<Handle>();h->sampleRate=sr;h->encoding=enc;if(!h->engine.prepare(sr,2048))return 0;h->engine.setSpatialBlend(1);h->engine.setEnabled(false);return(jlong)h.release();}
extern "C" JNIEXPORT void JNICALL Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeRelease(JNIEnv*,jclass,jlong a){delete(Handle*)a;}
extern "C" JNIEXPORT void JNICALL Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeReset(JNIEnv*,jclass,jlong a){if(auto*h=(Handle*)a)h->engine.reset();}
extern "C" JNIEXPORT void JNICALL Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeResetDiagnostics(JNIEnv*,jclass,jlong){}
extern "C" JNIEXPORT void JNICALL Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeSetEnabled(JNIEnv*,jclass,jlong a,jboolean v){if(auto*h=(Handle*)a){h->enabled=v==JNI_TRUE;h->engine.setEnabled(h->enabled);}}
extern "C" JNIEXPORT void JNICALL Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeSetLimiterEnabled(JNIEnv*,jclass,jlong,jboolean){}
extern "C" JNIEXPORT void JNICALL Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeSetBassGainDb(JNIEnv*,jclass,jlong a,jfloat v){if(auto*h=(Handle*)a)h->engine.setBassGain(std::clamp(g(v),0.0f,2.0f));}
extern "C" JNIEXPORT void JNICALL Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeSetTrebleGainDb(JNIEnv*,jclass,jlong a,jfloat v){if(auto*h=(Handle*)a)h->trebleDb=v;}
extern "C" JNIEXPORT void JNICALL Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeSetOutputGainDb(JNIEnv*,jclass,jlong a,jfloat v){if(auto*h=(Handle*)a)h->outputDb=v;}
extern "C" JNIEXPORT void JNICALL Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeSetSpatialBlend(JNIEnv*,jclass,jlong a,jfloat v){if(auto*h=(Handle*)a)h->engine.setSpatialBlend(std::clamp(std::isfinite(v)?v:0.0f,0.0f,1.0f));}
extern "C" JNIEXPORT void JNICALL Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeSetRoomPreset(JNIEnv*,jclass,jlong a,jint v){if(auto*h=(Handle*)a)applyRoomPreset(*h,v);}
extern "C" JNIEXPORT void JNICALL Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeSetRoomMix(JNIEnv*,jclass,jlong a,jfloat v){if(auto*h=(Handle*)a)h->engine.setRoomMix(std::clamp(v,0.0f,1.0f));}
extern "C" JNIEXPORT void JNICALL Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeSetReflectionAmount(JNIEnv*,jclass,jlong a,jfloat v){if(auto*h=(Handle*)a)h->engine.setReflectionAmount(std::clamp(v,0.0f,1.0f));}
extern "C" JNIEXPORT void JNICALL Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeSetReverbTimeSeconds(JNIEnv*,jclass,jlong a,jfloat v){if(auto*h=(Handle*)a)h->engine.setReverbTimeSeconds(std::clamp(v,0.2f,8.0f));}
extern "C" JNIEXPORT void JNICALL Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeSetRoomSize(JNIEnv*,jclass,jlong a,jfloat v){if(auto*h=(Handle*)a)h->engine.setRoomSize(std::clamp(v,0.0f,1.0f));}
extern "C" JNIEXPORT void JNICALL Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeSetDampening(JNIEnv*,jclass,jlong a,jfloat v){if(auto*h=(Handle*)a)h->engine.setDampening(std::clamp(v,0.0f,1.0f));}
extern "C" JNIEXPORT void JNICALL Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeSetSourcePosition(JNIEnv*,jclass,jlong a,jfloat az,jfloat el){if(auto*h=(Handle*)a){h->sourceAzimuth=std::clamp(std::isfinite(az)?az:0.0f,-180.0f,180.0f);h->sourceElevation=std::clamp(std::isfinite(el)?el:0.0f,-45.0f,90.0f);applySourcePosition(*h);}}
extern "C" JNIEXPORT void JNICALL Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeSetSourceDistance(JNIEnv*,jclass,jlong a,jfloat d){if(auto*h=(Handle*)a){h->sourceDistance=std::clamp(std::isfinite(d)?d:1.0f,1.0f,10.0f);applySourcePosition(*h);}}
extern "C" JNIEXPORT void JNICALL Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeSetStereoWidth(JNIEnv*,jclass,jlong a,jfloat v){if(auto*h=(Handle*)a)h->engine.setStereoWidth(std::clamp(v,0.0f,1.0f));}
extern "C" JNIEXPORT void JNICALL Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeSetCarFader(JNIEnv*,jclass,jlong a,jfloat v){if(auto*h=(Handle*)a){h->carFader=std::clamp(std::isfinite(v)?v:0.0f,-1.0f,1.0f);if(h->roomPreset==6){h->sourceAzimuth=h->carFader*180.0f;h->sourceElevation=-3.0f;applySourcePosition(*h);}}}
extern "C" JNIEXPORT void JNICALL Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeSetQuantumFrames(JNIEnv*,jclass,jlong a,jint v){if(auto*h=(Handle*)a)h->quantum=std::clamp(v,96,2048);}
extern "C" JNIEXPORT jdoubleArray JNICALL Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeReadDiagnostics(JNIEnv*e,jclass,jlong a){jdouble v[61]{};if(auto*h=(Handle*)a){v[36]=h->sampleRate;v[38]=h->quantum;v[39]=h->enabled?1:0;v[40]=h->encoding;v[42]=h->engine.latencySamples();}auto o=e->NewDoubleArray(61);if(o)e->SetDoubleArrayRegion(o,0,61,v);return o;}
extern "C" JNIEXPORT void JNICALL Java_dev_vxs_frostsoulx_playback_ImmersiveAudioProcessor_nativeProcess(JNIEnv*e,jclass,jlong a,jobject b,jint frames,jint enc){auto*h=(Handle*)a;if(!h||!b||frames<=0||frames>2048||enc!=h->encoding)return;auto*bytes=(uint8_t*)e->GetDirectBufferAddress(b);auto cap=e->GetDirectBufferCapacity(b);int bs=enc==4?4:2;if(!bytes||cap<(jlong)frames*2*bs||!h->enabled)return;if(enc==4){auto*p=(float*)bytes;tone(*h,p,frames);(void)h->engine.process(p,frames);}else{for(int i=0;i<frames;i++){h->scratch[2*i]=r16(bytes+4*i)/32768.0f;h->scratch[2*i+1]=r16(bytes+4*i+2)/32768.0f;}tone(*h,h->scratch.data(),frames);if(h->engine.process(h->scratch.data(),frames))for(int i=0;i<frames;i++){w16(bytes+4*i,h->scratch[2*i]);w16(bytes+4*i+2,h->scratch[2*i+1]);}}}
