package dev.vxs.frostsoulx.playback

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

data class WavImpulseResponse(val sampleRate: Int, val left: FloatArray, val right: FloatArray) {
    fun resampled(targetRate: Int, maxTaps: Int): Pair<FloatArray, FloatArray> {
        require(sampleRate in 8000..384000 && targetRate in 8000..384000 && left.isNotEmpty() && left.size == right.size && maxTaps > 0)
        val count=min(maxTaps,max(1,ceil(left.size.toDouble()*targetRate/sampleRate).toInt()))
        if(count==left.size && targetRate==sampleRate) return left to right
        fun convert(input:FloatArray):FloatArray {
            val out=FloatArray(count); val ratio=sampleRate.toDouble()/targetRate
            for(i in out.indices){ val pos=i*ratio; val a=min(input.lastIndex,pos.toInt()); val b=min(input.lastIndex,a+1); out[i]=input[a]+(input[b]-input[a])*(pos-a).toFloat() }
            return out
        }
        return convert(left) to convert(right)
    }
    companion object {
        fun decode(bytes:ByteArray):WavImpulseResponse {
            require(bytes.size in 44..(32*1024*1024)) { "WAV must be between 44 bytes and 32 MB" }
            fun ascii(o:Int,s:String)=o>=0&&o+s.length<=bytes.size&&s.indices.all{bytes[o+it].toInt().toChar()==s[it]}
            fun u16(o:Int)=(bytes[o].toInt() and 255) or ((bytes[o+1].toInt() and 255) shl 8)
            fun u32(o:Int)=(bytes[o].toLong() and 255L) or ((bytes[o+1].toLong() and 255L) shl 8) or ((bytes[o+2].toLong() and 255L) shl 16) or ((bytes[o+3].toLong() and 255L) shl 24)
            require(ascii(0,"RIFF")&&ascii(8,"WAVE")) { "Choose a standard RIFF/WAVE file" }
            var format=0;var channels=0;var rate=0;var bits=0;var dataAt=-1;var dataSize=0;var off=12
            while(off+8<=bytes.size){val n=u32(off+4);require(n<=Int.MAX_VALUE){"Invalid WAV chunk size"};val size=n.toInt();val start=off+8;require(start<=bytes.size&&size<=bytes.size-start){"Truncated WAV chunk"}
                when { ascii(off,"fmt ")->{require(size>=16);format=u16(start);channels=u16(start+2);rate=u32(start+4).toInt();bits=u16(start+14);if(format==0xfffe&&size>=40)format=u16(start+24)}
                    ascii(off,"data")->{dataAt=start;dataSize=size} }
                off=start+size+(size and 1)
            }
            require(format==1||format==3){"Only PCM or IEEE-float WAV is supported"}
            require(channels in 1..2){"IR WAV must be mono or stereo"}
            require(rate in 8000..384000){"Unsupported IR sample rate"}
            require((format==1&&bits in listOf(16,24,32))||(format==3&&bits==32)){"Supported: PCM 16/24/32-bit or float32 WAV"}
            require(dataAt>=0){"WAV has no audio data"}
            val bps=bits/8;val frameBytes=channels*bps;val frames=dataSize/frameBytes
            require(frames in 1..1_000_000){"IR is empty or too long"}
            val bb=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            fun sample(at:Int):Float {
                val v=when {format==3->bb.getFloat(at).toDouble();bits==16->bb.getShort(at).toDouble()/32768.0
                    bits==24->{var x=(bytes[at].toInt() and 255) or ((bytes[at+1].toInt() and 255) shl 8) or ((bytes[at+2].toInt() and 255) shl 16);if((x and 0x800000)!=0)x=x or -0x1000000;x/8388608.0}
                    else->bb.getInt(at).toDouble()/2147483648.0}
                require(v.isFinite()){"IR contains NaN or infinity"};return v.coerceIn(-4.0,4.0).toFloat()
            }
            val l=FloatArray(frames);val r=FloatArray(frames)
            for(i in 0 until frames){val at=dataAt+i*frameBytes;l[i]=sample(at);r[i]=if(channels==1)l[i] else sample(at+bps)}
            val response=WavImpulseResponse(rate,l,r);val (rl,rr)=response.resampled(rate,32768);return response.copy(left=rl,right=rr)
        }
    }
}
