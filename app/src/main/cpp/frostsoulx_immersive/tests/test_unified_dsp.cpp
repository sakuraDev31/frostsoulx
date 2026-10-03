#include "frostsoulx/immersive_audio_engine.h"
#include "frostsoulx/dsp/stereo_frontend.h"
#include "frostsoulx/dsp/true_peak.h"
#include "frostsoulx/dsp/partitioned_convolver.h"
#include <algorithm>
#include <array>
#include <atomic>
#include <chrono>
#include <cmath>
#include <cstdlib>
#include <iostream>
#include <limits>
#include <new>
#include <thread>
#include <vector>

// The callback scope catches both C++ allocation and direct C allocation/free.
thread_local bool callback = false;
thread_local std::size_t allocations = 0, deallocations = 0;
extern "C" void* __real_malloc(std::size_t);
extern "C" void* __real_calloc(std::size_t, std::size_t);
extern "C" void* __real_realloc(void*, std::size_t);
extern "C" void __real_free(void*);
extern "C" void* __wrap_malloc(std::size_t n) { if (callback) ++allocations; return __real_malloc(n); }
extern "C" void* __wrap_calloc(std::size_t n, std::size_t s) { if (callback) ++allocations; return __real_calloc(n, s); }
extern "C" void* __wrap_realloc(void* p, std::size_t n) { if (callback) ++allocations; return __real_realloc(p, n); }
extern "C" void __wrap_free(void* p) { if (callback && p) ++deallocations; __real_free(p); }
void* operator new(std::size_t n) { if (void* p = std::malloc(n ? n : 1)) return p; throw std::bad_alloc(); }
void* operator new[](std::size_t n) { return ::operator new(n); }
void operator delete(void* p) noexcept { std::free(p); }
void operator delete[](void* p) noexcept { std::free(p); }
void operator delete(void* p, std::size_t) noexcept { std::free(p); }
void operator delete[](void* p, std::size_t) noexcept { std::free(p); }

namespace {
using frostsoulx::ImmersiveAudioEngine;
using namespace frostsoulx::dsp;
int failures = 0;
void check(bool ok, const char* text) { if (!ok) { ++failures; std::cerr << "FAIL: " << text << '\n'; } }
struct Noise { unsigned s = 1; float next() { s = 1664525u*s+1013904223u; return static_cast<float>(s >> 8) / 8388608.0f - 1.0f; } };
void dry(ImmersiveAudioEngine& e, float blend = 0) {
    e.setRoomSimulationPreset(frostsoulx::RoomSimulationPreset::Off);
    e.setRoomMix(0); e.setSpatialBlend(blend); e.setEnabled(true);
}
bool process(ImmersiveAudioEngine& e, float* data, int n) {
    callback = true;
    const bool ok = e.process(data, n);
    callback = false;
    return ok;
}

// Independent 16x, 64-tap Blackman-sinc reconstruction (not the detector kernel).
// This is a numerical true-peak reference, not a BS.1770 certification claim.
double truePeak(const std::vector<float>& x) {
    std::array<std::array<double,64>,16> coeff{};
    for (int p = 0; p < 16; ++p) {
        double sum = 0;
        for (int k = 0; k < 64; ++k) {
            const double d = k - (31.0 + p / 16.0);
            const double sinc = std::fabs(d) < 1e-12 ? 1 : std::sin(3.141592653589793*d)/(3.141592653589793*d);
            const double w = 0.42-0.5*std::cos(6.283185307179586*k/63)+0.08*std::cos(12.566370614359172*k/63);
            coeff[p][k]=sinc*w; sum+=sinc*w;
        }
        for(auto& v:coeff[p]) v/=sum;
    }
    double peak=0;
    for (std::size_t n=64;n<x.size()/2;++n) {
        for(int c=0;c<2;++c) for(int p=0;p<16;++p) {
            double value=0;
            for(std::size_t k=0;k<64;++k) value+=x[2*(n-k)+static_cast<std::size_t>(c)]*coeff[p][k];
            peak=std::max(peak,std::fabs(value));
        }
    }
    return peak;
}
void testFrontend() {
    StereoFrontend f; f.prepare(48000); Noise rng;
    double maxError=0, inputEnergy=0, outputEnergy=0;
    for(int n=0;n<100000;++n) {
        const float il=rng.next(),ir=rng.next(); float l=il,r=ir;
        f.process(l,r,1,1,1);
        maxError=std::max({maxError,static_cast<double>(std::fabs(l-il)),static_cast<double>(std::fabs(r-ir))});
        inputEnergy+=il*il+ir*ir;outputEnergy+=l*l+r*r;
    }
    check(maxError<3e-7,"orthonormal M/S unity including complementary crossover");
    check(std::fabs(outputEnergy/inputEnergy-1)<2e-7,"M/S unity preserves energy");
    f.reset(); float last=0; double jump=0, peak=0;
    for(int n=0;n<80000;++n) {
        float l=0.999f,r=-0.999f;
        const float target=(n/1000)%2?2.0f:0.0f;
        f.process(l,r,target,2-target,target);
        if(n>100)jump=std::max(jump,static_cast<double>(std::fabs(l-last)));
        peak=std::max({peak,static_cast<double>(std::fabs(l)),static_cast<double>(std::fabs(r))}); last=l;
    }
    check(peak<=1.00001,"bass/width headroom keeps near-full-scale signals bounded before limiter");
    check(jump<0.01,"bass and width transitions do not zipper");
    // Bass width zero collapses only LF; high-band side remains independent.
    auto bandSide=[](double frequency,float bw,float hw){
        StereoFrontend p;p.prepare(48000);double energy=0;
        for(int n=0;n<48000;++n){float l=.2f*static_cast<float>(std::sin(6.283185307179586*frequency*n/48000)),r=-l;p.process(l,r,1,bw,hw);if(n>24000)energy+=(l-r)*(l-r);}
        return energy;
    };
    check(bandSide(20,0,1)<bandSide(20,1,1)*.02,"independent bass width attenuates low side");
    check(bandSide(8000,0,1)>bandSide(8000,1,1)*.15,"bass width retains high-band stereo");
    check(bandSide(8000,1,0)<bandSide(8000,1,1)*.01,"high-band width independent of bass");
}
void testStreamContinuity() {
    constexpr int frames=12000;
    ImmersiveAudioEngine fixed, variable;
    dry(fixed);dry(variable);
    check(fixed.prepare(48000,384)&&variable.prepare(48000,384),"stream engines prepare");
    Noise rng;std::vector<float> input(2*frames), a,b;
    for(float& x:input)x=.3f*rng.next();a=input;b=input;
    for(int pos=0;pos<frames;){int n=std::min(384,frames-pos);check(process(fixed,a.data()+2*pos,n),"fixed blocks process");pos+=n;}
    const int sizes[]={1,7,17,64,127,129,383,31};
    for(int pos=0,block=0;pos<frames;++block){int n=std::min(sizes[block%8],frames-pos);check(process(variable,b.data()+2*pos,n),"partial blocks process");pos+=n;}
    double error=0,dryError=0;
    const int latency=fixed.latencySamples();
    for(int n=0;n<frames;++n)for(int c=0;c<2;++c){error=std::max(error,static_cast<double>(std::fabs(a[2*n+c]-b[2*n+c])));if(n>=latency)dryError=std::max(dryError,static_cast<double>(std::fabs(a[2*n+c]-input[2*(n-latency)+c])));}
    check(error<1e-7,"partial callbacks preserve every sample and convolution time");
    check(dryError<1e-6,"M/S unity + blend zero is latency-aligned transparent");
    dry(fixed,1); dry(variable,1); fixed.reset();variable.reset(); a=input;b=input;
    for(int pos=0;pos<frames;){int n=std::min(384,frames-pos);process(fixed,a.data()+2*pos,n);pos+=n;}
    for(int pos=0,block=0;pos<frames;++block){int n=std::min(sizes[block%8],frames-pos);process(variable,b.data()+2*pos,n);pos+=n;}
    error=0;for(std::size_t n=0;n<a.size();++n)error=std::max(error,static_cast<double>(std::fabs(a[n]-b[n])));
    check(error<1e-7,"full wet spatial convolution invariant to callback boundaries");
}
void testMatrixConvolution() {
    NonUniformConvolver::Config cfg;cfg.maxTaps=32768;cfg.crossfadeBlocks=8;
    MimoConvolver m; check(m.prepare(2,2,cfg),"shared-input MIMO prepare");
    std::array<std::vector<float>,4> h;
    const int edges[]={0,1,127,128,383,384,511,1919,1920,2047,8063,8064,8191,8192,16383,32767};
    for(int p=0;p<4;++p){h[p].assign(cfg.maxTaps,0);for(int edge:edges)h[p][edge]=.002f*static_cast<float>(p+1)*(edge%2?-1:1);}
    const float* matrix[]={h[0].data(),h[1].data(),h[2].data(),h[3].data()};
    check(m.loadMatrix(matrix,cfg.maxTaps),"atomic matrix load");
    constexpr int frames=49152;Noise rng;
    std::array<std::vector<float>,2> x,y;
    for(int c=0;c<2;++c){x[c].resize(frames);y[c].resize(frames);for(auto& v:x[c])v=.9f*rng.next();}
    for(int pos=0;pos<frames;pos+=128){const float* in[]={x[0].data()+pos,x[1].data()+pos};float* out[]={y[0].data()+pos,y[1].data()+pos};callback=true;m.processBlock(in,out);callback=false;}
    double error=0;
    for(int n=0;n<frames;++n)for(int ear=0;ear<2;++ear){double reference=0;for(int source=0;source<2;++source)for(int edge:edges)if(edge<=n)reference+=x[source][n-edge]*h[source*2+ear][edge];error=std::max(error,std::fabs(reference-y[ear][n]));}
    check(error<2e-5,"all four transfers match direct convolution across every tier edge");
    // Replace a whole long matrix while deeper tiers are mid-emission, and
    // compare EACH sample to a global fade of two complete direct references.
    const auto oldH = h;
    const int extra = 4096;
    for(int c=0;c<2;++c){x[c].resize(frames+extra);for(int n=frames;n<frames+extra;++n)x[c][n]=.9f*rng.next();}
    for(auto& path:h)for(float& tap:path)tap*=-.6f;
    for(int p=0;p<4;++p)matrix[p]=h[p].data();
    const int change = frames + 256;
    double fadeError=0;
    std::array<float,128> fadeL{},fadeR{};
    for(int pos=frames;pos<frames+extra;pos+=128){
        if(pos==change)check(m.loadMatrix(matrix,cfg.maxTaps),"long IR publication mid-tail emission");
        const float* ip[]={x[0].data()+pos,x[1].data()+pos};float* op[]={fadeL.data(),fadeR.data()};
        callback=true;m.processBlock(ip,op);callback=false;
        for(int k=0;k<128;++k)for(int ear=0;ear<2;++ear){
            const int n=pos+k;double refA=0,refB=0;
            for(int source=0;source<2;++source)for(int edge:edges){refA+=x[source][n-edge]*oldH[source*2+ear][edge];refB+=x[source][n-edge]*h[source*2+ear][edge];}
            const double t=std::clamp(static_cast<double>(n-change)/1024,0.0,1.0);
            const double actual=ear==0?fadeL[k]:fadeR[k];
            fadeError=std::max(fadeError,std::fabs(actual-(refA+t*(refB-refA))));
        }
    }
    check(fadeError<3e-5,"all convolution tiers share one coherent whole-matrix sample fade");

    std::array<float,128> inL{},inR{},outL{},outR{};inL.fill(1);inR.fill(1);
    const float* in[]={inL.data(),inR.data()};float* out[]={outL.data(),outR.data()};
    // Correlated identical-IR swaps MUST NOT exhibit equal-power +3 dB bump.
    NonUniformConvolver::Config shortCfg;shortCfg.maxTaps=128;shortCfg.maxTiers=1;shortCfg.crossfadeBlocks=8;
    MimoConvolver unity;unity.prepare(2,2,shortCfg);
    std::array<float,128> id{},cross{};id[0]=.8f;cross[0]=.1f;
    const float* u[]={id.data(),cross.data(),cross.data(),id.data()};unity.loadMatrix(u,128);
    for(int k=0;k<10;++k)unity.processBlock(in,out);
    double deviation=0;
    for(int k=0;k<100;++k){unity.loadMatrix(u,128);callback=true;unity.processBlock(in,out);callback=false;for(float v:outL)deviation=std::max(deviation,std::fabs(v-.9));}
    check(deviation<1e-5,"rapid identical IR transitions have no gain bump/click");
    for(int k=0;k<20;++k)unity.processBlock(in,out); // finish coalesced swaps
    const float* empty[]={nullptr,nullptr,nullptr,nullptr};unity.loadMatrix(empty,0);
    float previous=.9f;bool monotonic=true;
    for(int k=0;k<12;++k){unity.processBlock(in,out);for(float v:outL){monotonic &= v<=previous+1e-5f&&v>=-1e-5f;previous=v;}}
    check(monotonic&&std::fabs(previous)<1e-6,"IR removal fades to silence without stale tail");
    unity.loadMatrix(u,128);for(int k=0;k<12;++k)unity.processBlock(in,out);
    check(std::fabs(outL.back()-.9f)<1e-5,"empty IR retains input history for seamless revival");

    // Dense randomized 2x2 IR: not just isolated impulse taps.
    shortCfg.maxTaps=2048;shortCfg.maxTiers=3;shortCfg.crossfadeBlocks=0;
    MimoConvolver dense;dense.prepare(2,2,shortCfg);
    for(auto& path:h){path.assign(2048,0);for(auto& tap:path)tap=.002f*rng.next();}
    for(int p=0;p<4;++p)matrix[p]=h[p].data();dense.loadMatrix(matrix,2048);
    for(int pos=0;pos<4096;pos+=128){const float* ip[]={x[0].data()+pos,x[1].data()+pos};float* op[]={y[0].data()+pos,y[1].data()+pos};dense.processBlock(ip,op);}
    error=0;for(int n=0;n<4096;++n)for(int ear=0;ear<2;++ear){double ref=0;for(int source=0;source<2;++source)for(int k=0;k<2048&&k<=n;++k)ref+=x[source][n-k]*h[source*2+ear][k];error=std::max(error,std::fabs(ref-y[ear][n]));}
    check(error<3e-5,"dense full partitioned MIMO matches independent direct reference");
}
void testSafety() {
    double worst=0;
    for(int signal=0;signal<6;++signal){
        TruePeakSafety safety;safety.prepare(48000);Noise rng;
        std::vector<float> out(2*20000,0);
        for(int n=0;n<20000;++n){float x=0;
            if(n>1024&&n<19000){
                if(signal==0)x=.999f*static_cast<float>(std::sin(6.283185307179586*.25*n+.78539816339));
                if(signal==1)x=.999f*rng.next();
                if(signal==2)x=1.999f*static_cast<float>(std::sin(.17*n));
                if(signal==3)x=((n/73)%2?-.999f:.999f);
                if(signal==4)x=(n%997==0?1.999f:0.0f);
                if(signal==5)x=.999f*static_cast<float>(std::sin(6.283185307179586*.43*n+.4));
            }
            float l=x,r=-x;safety.process(l,r);out[2*n]=l;out[2*n+1]=r;
            check(std::isfinite(l)&&std::fabs(l)<=.980001,"safety sample peak bound");
        }
        const double tp=truePeak(out);worst=std::max(worst,tp);
        if(tp>.981)std::cerr<<"True peak case "<<signal<<": "<<tp<<'\n';
        check(tp<=.981,"independent 16x reconstructed true peak <= 0.98 ceiling");
    }
    std::cout<<"Independent 16x safety worst true peak: "<<worst<<'\n';
    TruePeakSafety safety;safety.prepare(48000);Noise rng;std::array<float,2000> original{},output{};
    for(int n=0;n<2000;++n){float l=.2f*rng.next(),r=l;original[n]=l;safety.process(l,r);output[n]=l;}
    bool transparent=true;for(int n=64;n<2000;++n)transparent &= output[n]==original[n-64];
    check(transparent&&safety.gain()==1,"safety is exactly a delay below threshold, not a tone shaper");
}
void testSpatialTransitionsAndBounds() {
    ImmersiveAudioEngine e;dry(e,1);e.prepare(48000,384);
    double maxDelta=0;float previous=0;std::array<float,768> block{};
    for(int iteration=0;iteration<80;++iteration){
        e.setSourcePosition(static_cast<float>((iteration%7)-3)*.3f,1.2f,1.5f);
        e.setListenerPosition(static_cast<float>((iteration%3)-1)*.1f,1.2f,0);
        e.setHeadOrientation(static_cast<float>((iteration%9)-4)*10,5,-2);
        e.setBassGain(iteration%2?1.8f:.8f);e.setBassWidth(iteration%2?.2f:1.8f);
        e.setHighBandWidth(iteration%2?1.7f:.7f);
        for(int ear=0;ear<2;++ear){double power=0,peak=0;for(const auto& pair:e.activeTransferMatrix())for(float v:ear==0?pair.left:pair.right){power+=static_cast<double>(v)*v;peak=std::max(peak,std::fabs(static_cast<double>(v)));}check(std::sqrt(power)<=.980001,"moving source/listener matrix power bound");check(peak<=.980001,"moving source/listener matrix peak bound");}
        block.fill(.9f);check(process(e,block.data(),384),"transition callback");
        for(int n=0;n<384;++n){float value=block[2*n];if(iteration>5)maxDelta=std::max(maxDelta,static_cast<double>(std::fabs(value-previous)));previous=value;check(std::isfinite(value)&&std::fabs(value)<.981,"all parameter transitions finite and peak-safe");}
        check(e.safetyGain()==1,"spatial gain fix keeps safety inactive on correlated loud DC");
    }
    check(maxDelta<.02,"movement and band parameter changes have bounded sample steps");
    std::cout<<"Movement/parameter maximum sample step: "<<maxDelta<<'\n';
    e.reset();block.fill(0);process(e,block.data(),384);check(std::all_of(block.begin(),block.end(),[](float x){return x==0;}),"reset clears all convolution/safety state");
    e.setSourcePosition(std::numeric_limits<float>::quiet_NaN(),std::numeric_limits<float>::infinity(),0);
    e.setHeadOrientation(std::numeric_limits<float>::infinity(),0,0);
    e.setBassGain(std::numeric_limits<float>::quiet_NaN());
    block.fill(.999f);block[0]=std::numeric_limits<float>::quiet_NaN();block[1]=std::numeric_limits<float>::infinity();
    for(int k=0;k<8;++k){process(e,block.data(),384);check(std::all_of(block.begin(),block.end(),[](float x){return std::isfinite(x)&&std::fabs(x)<=.98f;}),"NaN/Inf do not poison DSP history");}
}
void testRateAndShortIr() {
    for(int rate:{8000,44100,48000,96000,192000,384000}) {
        ImmersiveAudioEngine e;dry(e,1);e.setIrLength(512);
        check(e.prepare(rate,384),"short IR prepares at supported sample rate");
        std::array<float,768> block{};block[0]=.5f;
        double energy=0;
        for(int n=0;n<8;++n){process(e,block.data(),384);for(float x:block){energy+=x*x;check(std::isfinite(x)&&std::fabs(x)<=.98f,"short-IR output finite and bounded at high sample rates");}block.fill(0);}
        check(energy>1e-8,"short IR still contains direct HRTF even when HRIR is longer than room IR");
    }
}
void testConcurrentPublication() {
    ImmersiveAudioEngine e;dry(e,1);e.prepare(48000,384);
    std::atomic<bool> start{false};std::atomic<int> bad{0};
    std::thread producer([&]{while(!start.load())std::this_thread::yield();for(int k=0;k<100;++k){e.setHeadOrientation(static_cast<float>(k%90)-45,0,0);e.setBassWidth(static_cast<float>(k%20)/10);e.setSpatialBlend(static_cast<float>(k%10)/10);}});
    std::thread audio([&]{std::array<float,768> data{};start.store(true);for(int k=0;k<3000;++k){data.fill(.8f);callback=true;bool ok=e.process(data.data(),384);callback=false;if(!ok||!std::all_of(data.begin(),data.end(),[](float x){return std::isfinite(x)&&std::fabs(x)<=.980001;}))++bad;}if(allocations||deallocations)++bad;});
    producer.join();audio.join();
    check(bad.load()==0,"concurrent IR/control publication is finite, bounded and allocation-free");
}
void benchmark() {
    ImmersiveAudioEngine e;e.prepare(48000,384);e.setSpacePreset(frostsoulx::spatial::SpaceProfile::Preset::ConcertHall);e.setRoomMix(.35f);e.setEnabled(true);
    std::array<float,768> data{};Noise rng;
    for(int k=0;k<64;++k){for(float& x:data)x=.8f*rng.next();process(e,data.data(),384);}
    std::array<double,1000> times{};
    const auto allStart=std::chrono::steady_clock::now();
    for(auto& dt:times){for(float& x:data)x=.8f*rng.next();const auto start=std::chrono::steady_clock::now();process(e,data.data(),384);dt=std::chrono::duration<double,std::micro>(std::chrono::steady_clock::now()-start).count();}
    const double total=std::chrono::duration<double>(std::chrono::steady_clock::now()-allStart).count();
    std::sort(times.begin(),times.end());
    std::cout<<"Unified 32k-tap stereo/48k/384 benchmark: "<<total<<" s for 8 s audio; "<<total/8*100<<"% realtime; p50 "<<times[500]<<" us, p99 "<<times[990]<<" us, max "<<times.back()<<" us\n";
#if !defined(__SANITIZE_ADDRESS__) && !defined(__SANITIZE_THREAD__)
    check(total<4,"complete unified engine within 50% host realtime budget");
    check(times[990]<8000,"p99 callback time fits the preserved 384-frame/8ms quantum");
#endif
}
}
int main(){
    static_assert(std::atomic<int>::is_always_lock_free&&std::atomic<float>::is_always_lock_free,"ARM64 RT requires lock-free scalar publication");
    testFrontend();testStreamContinuity();testMatrixConvolution();testSafety();
    testSpatialTransitionsAndBounds();testRateAndShortIr();testConcurrentPublication();benchmark();
    check(allocations==0&&deallocations==0,"zero malloc/calloc/realloc/new/free/delete in callback including IR fades");
    std::cout<<"Realtime heap operations: "<<allocations<<" allocations, "<<deallocations<<" deallocations\n";
    std::cout<<"Unified DSP focused tests: "<<failures<<" failures\n";
    return failures?1:0;
}
