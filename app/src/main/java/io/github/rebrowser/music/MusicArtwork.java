package io.github.rebrowser.music;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Already-provided bytes only, no fetch/path/provider. One exact per-instance decode/result owner.
 * Disposal attempt is one-use; unknown JNI return/disposal retains ownership, never releases a
 * later result's slot. Per-result pixels are bounded, NOT native transient/process memory or
 * whole JNI-tail qualification. Callers separately budget encoded bytes/borrowed bitmap copies.
 */
public final class MusicArtwork {
 public static final int MAX_ENCODED_BYTES=8*1024*1024,MAX_DIMENSION=4096;
 public static final long MAX_DECODED_PIXELS=1024L*1024L;
 public enum Format {JPEG,PNG,WEBP,UNKNOWN}
 public static final class Plan {
  private final int width,height,sample;
  private Plan(int width,int height,int sample){this.width=width;this.height=height;this.sample=sample;}
  public Format format(){return Format.UNKNOWN;}public int width(){return width;}public int height(){return height;}public int sampleSize(){return sample;}
  public long decodedPixels(){return ((width+(long)sample-1)/sample)*((height+(long)sample-1)/sample);}
 }
 private static final class Slot {private Slot(){}}
 private final AtomicReference<Slot> active=new AtomicReference<>();
 private final AtomicLong latest=new AtomicLong();
 public final class OwnedBitmap implements AutoCloseable {
  private final Bitmap bitmap;private final Slot owner;private boolean attempted,released;
  private OwnedBitmap(Bitmap bitmap,Slot owner){this.bitmap=bitmap;this.owner=owner;}
  /** Borrow only while owned. Never retain or manually recycle it; copies need caller budgets. */
  public synchronized Bitmap bitmap(){if(attempted)throw new IllegalStateException("Artwork disposal already attempted");return bitmap;}
  public synchronized boolean isClosed(){return released;}
  public synchronized boolean disposalAttempted(){return attempted;}
  @Override public void close(){release();}
  public synchronized boolean release(){
   if(attempted)return released;attempted=true; // spend BEFORE native call, including unknown/error
   try{bitmap.recycle();if(bitmap.isRecycled())released=releaseSlot(owner);}catch(RuntimeException unknown){/* retain exact owner, no retry */}
   return released;
  }
 }
 public static Format sniff(byte[] b){
  if(b==null || b.length<12)return Format.UNKNOWN;
  if((b[0]&255)==255 && (b[1]&255)==216 && (b[2]&255)==255)return Format.JPEG;
  if((b[0]&255)==137 && b[1]=='P' && b[2]=='N' && b[3]=='G')return Format.PNG;
  if(b[0]=='R' && b[1]=='I' && b[2]=='F' && b[3]=='F' && b[8]=='W' && b[9]=='E' && b[10]=='B' && b[11]=='P')return Format.WEBP;
  return Format.UNKNOWN;
 }
 public static Plan plan(int width,int height){
  if(width<1 || height<1 || width>MAX_DIMENSION || height>MAX_DIMENSION)throw new IllegalArgumentException("Invalid artwork dimensions");
  int sample=1;while(((width+(long)sample-1)/sample)*((height+(long)sample-1)/sample)>MAX_DECODED_PIXELS)sample*=2;
  return new Plan(width,height,sample);
 }
 /** Returns zero for stale/equal requests, NEVER the latest generation (no old-byte retarget). */
 public long begin(long generation){
  if(generation<1)throw new IllegalArgumentException("Invalid artwork generation");
  for(;;){long before=latest.get();if(generation<=before)return 0;if(latest.compareAndSet(before,generation))return generation;}
 }
 public long latestGeneration(){return latest.get();}public boolean busy(){return active.get()!=null;}
 public OwnedBitmap decodeOwned(byte[] encoded,long generation){
  if(generation<1 || encoded==null || encoded.length>MAX_ENCODED_BYTES)throw new IllegalArgumentException("Artwork bytes/generation rejected");
  if(generation!=latest.get() || sniff(encoded)==Format.UNKNOWN)return null;
  Slot slot=new Slot();if(!active.compareAndSet(null,slot))return null;
  Bitmap decoded=null;boolean returned=false,nativeReturned=true;
  try{
   BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
   nativeReturned=false;BitmapFactory.decodeByteArray(encoded,0,encoded.length,bounds);nativeReturned=true;
   if(bounds.outWidth<1 || bounds.outHeight<1)return null;Plan plan=plan(bounds.outWidth,bounds.outHeight);
   if(generation!=latest.get())return null;
   BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=plan.sampleSize();options.inPreferredConfig=Bitmap.Config.ARGB_8888;
   nativeReturned=false;decoded=BitmapFactory.decodeByteArray(encoded,0,encoded.length,options);nativeReturned=true;
   if(decoded==null)return null;
   if(generation!=latest.get() || decoded.getWidth()<1 || decoded.getHeight()<1 || (long)decoded.getWidth()*decoded.getHeight()>MAX_DECODED_PIXELS)return null;
   OwnedBitmap result=new OwnedBitmap(decoded,slot);returned=true;return result;
  }finally{
   if(!returned && nativeReturned){
    if(decoded==null)releaseSlot(slot);
    else try{decoded.recycle();if(decoded.isRecycled())releaseSlot(slot);}catch(RuntimeException unknown){/* exact slot retained */}
   }
  }
 }
 private boolean releaseSlot(Slot owner){return owner!=null && active.compareAndSet(owner,null);}
}
