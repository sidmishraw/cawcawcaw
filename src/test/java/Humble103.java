import java.io.IOException;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.humble.video.Codec;
import io.humble.video.Codec.ID;
import io.humble.video.Coder.Flag;
import io.humble.video.Decoder;
import io.humble.video.Demuxer;
import io.humble.video.DemuxerStream;
import io.humble.video.Encoder;
import io.humble.video.MediaAudio;
import io.humble.video.MediaDescriptor;
import io.humble.video.MediaPacket;
import io.humble.video.MediaPicture;
import io.humble.video.Muxer;
import io.humble.video.MuxerFormat;

/**
 * Project: VidsUsingXuggler
 * Package:
 * File: Humble103.java
 * 
 * @author sidmishraw
 *         Last modified: Oct 27, 2017 12:02:06 AM
 */

/**
 * <p>
 * Will stream the audio contents from a media file and make a audio only file.
 * 
 * <a href =
 * "https://github.com/artclarke/humble-video/blob/master/humble-video-demos/src/main/java/io/humble/video/demos/RecordAndEncodeVideo.java">
 * Used RecordAndEncodeVideo.java as reference
 * </a>
 * <p>
 * Concepts learnt:
 * 
 * <ul>
 * 
 * <li>Muxer: A {@link Muxer} object is a container you can write media data
 * to.</li>
 * 
 * -- Encoders are a pain in the arse!
 * <li>Encoders: An {@link Encoder} object lets you convert {@link MediaAudio}
 * or {@link MediaPicture} objects into {@link MediaPacket} objects so they can
 * be written to {@link Muxer} objects.</li>
 * 
 * </ul>
 * 
 * @author sidmishraw
 *
 *         Qualified Name: .Humble103
 *
 */
public class Humble103 {
    
    // # Logging stuff
    private static final Logger logger = LoggerFactory.getLogger(Humble103.class);
    // # Logging stuff
    
    /**
     * @param args
     *            Needs the input file path to process
     */
    public static void main(String[] args) throws Exception {
        
        if (args.length != 1) {
            
            throw new Exception("Need the file to process");
        }
        
        String filePath = args[0];
        
        logger.info(String.format("Found filepath :: %s", filePath));
        
        processVideoFile(filePath);
        
        logger.info("Done!");
    }
    
    /**
     * @param filePath
     */
    private static void processVideoFile(String filePath) {
        
        // The 2 objectives are as follows:
        // TODO: Use a Demuxer and stream contents of a media file, extract
        // audio stream only
        // TODO: Open a Muxer and write audio contents into it.
        
        Demuxer demux = null; // demuxer for reading input media file
        Muxer mux = null; // muxer for writing output media file
        
        try {
            
            demux = Demuxer.make();
            Demuxer ddemux = demux; // reference for using in closures
            
            demux.open(filePath, null, false, true, null, null);
            
            // # Log input media file's metadata
            logger.info(":: Metadata for input media file ::");
            demux.getMetaData().getKeys().forEach(
                    metaKey -> logger.info(String.format("(%s, %s)", metaKey, ddemux.getMetaData().getValue(metaKey))));
            // # Log input media file's metadata
            
            // # log encoding formats supported by system
            String formatsSupported = getSupportedFormats();
            logger.info("Formats supported:: \n" + formatsSupported);
            // # log encoding formats supported by system
            
            int audioStreamIndex = -1;
            Decoder audioDecoder = null;
            
            for (int i = 0; i < demux.getNumStreams(); i++) {
                
                DemuxerStream stream = demux.getStream(i);
                
                if (!Objects.isNull(stream) && !Objects.isNull(stream.getDecoder())
                        && stream.getDecoder().getCodecType().equals(MediaDescriptor.Type.MEDIA_AUDIO)) {
                    
                    audioStreamIndex = stream.getIndex();
                    audioDecoder = stream.getDecoder();
                    
                    break;
                }
            }
            
            if (audioStreamIndex == -1 || Objects.isNull(audioDecoder)) {
                
                throw new Exception("Audio stream not found or couldn't be decoded! Bailing out...");
            }
            
            logger.info("Input media CODEC :: " + audioDecoder.getCodec().getName());
            
            audioDecoder.open(null, null); // open the decoder to stream
            
            // # Muxer create, boiler plate
            // the muxer for writing the output
            mux = Muxer.make("outFile.mp3", null, "mp3");
            
            /**
             * Now that we know what codec, we need to create an encoder
             * 
             * Forcing to use mp4
             */
            Encoder encoder = Encoder.make(Codec.findEncodingCodec(ID.CODEC_ID_MP3));
            
            // all stuff that needs to be set in the encoder for making a MP3
            // # MP3 format needs all this stuff
            encoder.setSampleRate(audioDecoder.getSampleRate());
            encoder.setChannels(audioDecoder.getChannels());
            encoder.setChannelLayout(audioDecoder.getChannelLayout());
            encoder.setSampleFormat(audioDecoder.getSampleFormat());
            encoder.setFlag(Flag.FLAG_GLOBAL_HEADER, true);
            // # MP3 format needs all this stuff
            
            /** Open the encoder. */
            encoder.open(null, null);
            
            /** Add this stream to the muxer. */
            mux.addNewStream(encoder);
            // # Muxer create, boiler plate
            
            // # Muxer open
            mux.open(null, null);
            // # Muxer open
            
            /*
             * <p>
             * We allocate a set of samples with the same number of channels as
             * the coder tells us is in this buffer.
             */
            MediaAudio samples = MediaAudio.make(audioDecoder.getFrameSize(), audioDecoder.getSampleRate(),
                    audioDecoder.getChannels(), audioDecoder.getChannelLayout(), audioDecoder.getSampleFormat());
            
            /*
             * Now, we start walking through the container looking at each
             * packet.
             * This is a decoding loop, and as you work with Humble you'll write
             * a lot of these.
             * 
             * Notice how in this loop we reuse all of our objects to avoid
             * reallocating them. Each call to Humble resets objects to avoid
             * unnecessary reallocation.
             */
            MediaPacket rpacket = MediaPacket.make(); // read packet
            MediaPacket wpacket = MediaPacket.make(); // write packet
            
            /**
             * read() returns 0 if successful else < 0
             */
            while (demux.read(rpacket) >= 0) {
                
                // logger.info(String.format("Packet's stream index = %d",
                // rpacket.getStreamIndex()));
                
                /*
                 * Now we have a packet, let's see if it belongs to our audio
                 * stream
                 */
                if (rpacket.getStreamIndex() == audioStreamIndex) {
                    
                    /*
                     * A packet can actually contain multiple sets of samples
                     * (or frames of samples in audio-decoding speak). So, we
                     * may need to call decode audio multiple times at different
                     * offsets in the packet's data. We capture that here.
                     */
                    int offset = 0;
                    int bytesRead = 0;
                    
                    do {
                        
                        bytesRead += audioDecoder.decode(samples, rpacket, offset);
                        
                        if (samples.isComplete()) {
                            
                            encoder.encodeAudio(wpacket, samples);
                        }
                        
                        if (wpacket.isComplete()) {
                            
                            mux.write(wpacket, false);
                        }
                        
                        offset += bytesRead;
                    } while (offset < rpacket.getSize());
                }
            }
            
            // Some audio decoders (especially advanced ones) will cache
            // audio data before they begin decoding, so when you are done you
            // need to flush them. The convention to flush Encoders or Decoders
            // in Humble Video is to keep passing in null until incomplete
            // samples or packets are returned.
            do {
                
                audioDecoder.decode(samples, null, 0);
                
                if (samples.isComplete()) {
                    
                    encoder.encodeAudio(wpacket, samples);
                }
                
                if (wpacket.isComplete()) {
                    
                    mux.write(wpacket, false);
                }
            } while (samples.isComplete());
            
            /**
             * Encoders, like decoders, sometimes cache pictures so it can do
             * the right key-frame optimizations.
             * So, they need to be flushed as well. As with the decoders, the
             * convention is to pass in a null
             * input until the output is not complete.
             */
            do {
                
                encoder.encode(wpacket, null);
                
                if (wpacket.isComplete()) {
                    
                    mux.write(wpacket, false);
                }
            } while (wpacket.isComplete());
            
        } catch (Exception e) {
            
            logger.error(e.getMessage(), e);
        } finally {
            
            // close resources
            if (!Objects.isNull(demux)) {
                
                try {
                    
                    demux.close();
                } catch (InterruptedException | IOException e) {
                    
                    logger.error(e.getMessage(), e);
                }
            }
            
            if (!Objects.isNull(mux)) {
                
                try {
                    
                    mux.close();
                } catch (Exception e) {
                    
                    logger.error(e.getMessage(), e);
                }
            }
        }
    }
    
    /**
     * Fetches all the formats supported by the current system.
     * 
     * @return The string containing all the long descriptive names of formats
     *         supported on the machine
     */
    private static String getSupportedFormats() {
        
        StringBuffer sBuffer = new StringBuffer();
        
        MuxerFormat.getFormats()
                .forEach(format -> sBuffer.append(String.format("(%s,%s), ", format.getName(), format.getLongName())));
        
        // trim last space and comma
        sBuffer.delete(sBuffer.length() - 2, sBuffer.length());
        
        return sBuffer.toString();
    }
}
