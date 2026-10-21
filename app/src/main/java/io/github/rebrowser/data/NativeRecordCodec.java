package io.github.rebrowser.data;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Bounded canonical binary primitives for native metadata only; not a file, URI, grant or
 * authenticated/latest record. Decoders borrow a read-only buffer and require exact EOF.
 * String byte lengths are bounded BEFORE allocation/UTF8 decoding. No Java serialization. */
public final class NativeRecordCodec {
    public interface Codec<T>{T decode(ByteBuffer borrowed);byte[] encode(T value);}
    public static final class Writer {
        private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();private final int maximum;
        public Writer(String magic,int maximum){if(maximum<16 || maximum>4*1024*1024)throw bad();this.maximum=maximum;raw(magic(magic));u16(1);}
        private void room(int n){if(n<0 || n>maximum-bytes.size())throw bad();}
        private void raw(byte[] b){room(b.length);bytes.write(b,0,b.length);}
        public Writer u8(int v){if(v<0 || v>255)throw bad();room(1);bytes.write(v);return this;}
        public Writer u16(int v){if(v<0 || v>65535)throw bad();room(2);bytes.write(v>>>8);bytes.write(v);return this;}
        public Writer nonnegative(long v){if(v<0)throw bad();room(8);for(int n=7;n>=0;n--)bytes.write((int)(v>>>(n*8)));return this;}
        public Writer bool(boolean v){return u8(v?1:0);}
        public Writer text(String value,int maxBytes,boolean empty){validateText(value,maxBytes,empty);byte[] b=value.getBytes(StandardCharsets.UTF_8);if(b.length>maxBytes || b.length>65535)throw bad();room(2+b.length);u16(b.length);raw(b);return this;}
        public byte[] finish(){return bytes.toByteArray();}
    }
    public static final class Reader {
        private final ByteBuffer input;
        public Reader(ByteBuffer borrowed,String magic,int maximum){if(borrowed==null || maximum<16 || maximum>4*1024*1024 || borrowed.remaining()>maximum || borrowed.remaining()<10)throw bad();input=borrowed.asReadOnlyBuffer();byte[] expected=magic(magic);for(byte b:expected)if(u8()!=(b&255))throw bad();if(u16()!=1)throw bad();}
        private void need(int n){if(n<0 || input.remaining()<n)throw bad();}
        public int u8(){need(1);return input.get()&255;}
        public int u16(){return (u8()<<8)|u8();}
        public long nonnegative(){need(8);long v=0;for(int n=0;n<8;n++)v=(v<<8)|u8();if(v<0)throw bad();return v;}
        public boolean bool(){int b=u8();if(b>1)throw bad();return b==1;}
        public String text(int maxBytes,boolean empty){int n=u16();if(n>maxBytes || (!empty && n==0))throw bad();need(n);ByteBuffer text=input.slice();text.limit(n);String value;try{value=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(text).toString();}catch(CharacterCodingException invalid){throw bad();}input.position(input.position()+n);validateText(value,maxBytes,empty);return value;}
        public void end(){if(input.hasRemaining())throw bad();}
    }
    /** Single-line private metadata. This does NOT bless a URL, origin or authority reference. */
    public static String validateText(String value,int maxBytes,boolean empty){
        if(value==null || maxBytes<0 || maxBytes>65535 || value.length()>maxBytes || (!empty && value.isEmpty()))throw bad();int count=0;
        for(int i=0;i<value.length();i++){char c=value.charAt(i);if(Character.isISOControl(c))throw bad();if(Character.isHighSurrogate(c)){if(++i>=value.length() || !Character.isLowSurrogate(value.charAt(i)))throw bad();count+=4;}else if(Character.isLowSurrogate(c))throw bad();else count+=c<128?1:c<2048?2:3;if(count>maxBytes)throw bad();}return value;
    }
    private static byte[] magic(String s){if(s==null || s.length()!=8)throw bad();for(int n=0;n<8;n++)if(s.charAt(n)<33 || s.charAt(n)>126)throw bad();return s.getBytes(StandardCharsets.US_ASCII);}
    public static IllegalArgumentException bad(){return new IllegalArgumentException("Invalid bounded native metadata");}
    private NativeRecordCodec(){}
}
