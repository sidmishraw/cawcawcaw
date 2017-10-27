import java.nio.ByteBuffer;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.humble.video.Decoder;
import io.humble.video.Demuxer;
import io.humble.video.DemuxerStream;
import io.humble.video.MediaAudio;
import io.humble.video.MediaDescriptor;
import io.humble.video.MediaPacket;
import io.humble.video.Muxer;
import io.humble.video.javaxsound.AudioFrame;
import io.humble.video.javaxsound.MediaAudioConverter;
import io.humble.video.javaxsound.MediaAudioConverterFactory;

/**
 * Project: VidsUsingXuggler
 * Package:
 * File: Humble102.java
 * 
 * @author sidmishraw
 *         Last modified: Oct 26, 2017 7:15:46 PM
 */

/**
 * <p>
 * Opens a media file, finds the first audio stream, and then plays it.
 * 
 * <p>
 * This is meant as a demonstration program to teach myself the use of the
 * Humble API.
 * 
 * <p>
 * As per this file
 * <a href =
 * "https://github.com/artclarke/humble-video/blob/master/humble-video-demos/src/main/java/io/humble/video/demos/DecodeAndPlayAudio.java">DecodeAndPlayAudio.java</a>
 * 
 * <p>
 * Concepts learnt::
 * 
 * <ul>
 * 
 * <li>MediaPacket: An {@link MediaPacket} object can read from Media
 * {@link Demuxer} objects and written {@link Muxer} objects, and represents
 * encoded/compressed media-data.</li>
 * 
 * <li>MediaAudio: {@link MediaAudio} objects represent uncompressed audio in
 * Humble.</li>
 * 
 * <li>Decoder: {@link Decoder} objects can be used to convert
 * {@link MediaPacket} objects into uncompressed {@link MediaAudio} objects.
 * 
 * <li>Decoding loops: This introduces the concept of reading
 * {@link MediaPacket} objects from a {@link Demuxer} and then decoding them
 * into raw data.
 * 
 * </ul>
 * 
 * 
 * @author sidmishraw
 *
 *         Qualified Name: .Humble102
 *
 */
public class Humble102 {
    
    // # Logging stuff
    private static final Logger logger = LoggerFactory.getLogger(Humble102.class);
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
     * Opens a file, and plays the audio from it on the speakers.
     * 
     * @param filePath
     *            The filepath of the video to process
     */
    private static void processVideoFile(String filePath) throws Exception {
        
        // Instantiate Demuxer or container
        Demuxer demuxer = Demuxer.make();
        
        // open the demuxer/container with the filePath passed to it
        demuxer.open(filePath, null, false, true, null, null); // will throw
                                                               // exception
        
        // Query the demuxer to find the number of streams it found when it
        // opened the media file.
        int nbrStreamsFound = demuxer.getNumStreams();
        
        int audioStreamId = -1; // initially set to -1, should get updated to
                                // the actual ID of the first audio stream
        
        Decoder audioDecoder = null; // the decoder of the first audio stream
        
        /**
         * Iterate through the streams found to find the first Audio stream
         * The audio streams have codec type of MediaDescriptor.Type.MEDIA_AUDIO
         * so the first stream with that codec type is the first audio stream.
         */
        for (int i = 0; i < nbrStreamsFound; i++) {
            
            DemuxerStream stream = demuxer.getStream(i);
            
            if (!Objects.isNull(stream)
                    && stream.getDecoder().getCodecType().equals(MediaDescriptor.Type.MEDIA_AUDIO)) {
                
                audioStreamId = stream.getIndex(); // don't use getId(), it is
                                                   // not equal to stream index
                                                   // -- Note, beware :)
                audioDecoder = stream.getDecoder();
                
                break;
            }
        }
        
        /**
         * If no audio stream was found, we need to bail out
         */
        if (audioStreamId == -1) {
            
            throw new Exception("No Audio stream was found in container! Bailing out...");
        }
        
        /**
         * <p>
         * After finding out the audio stream, we need the decoder to do the
         * work. This is done by first opening the decoder.
         */
        audioDecoder.open(null, null); // simple open
        
        /*
         * <p>
         * We allocate a set of samples with the same number of channels as the
         * coder tells us is in this buffer.
         */
        MediaAudio samples = MediaAudio.make(audioDecoder.getFrameSize(), audioDecoder.getSampleRate(),
                audioDecoder.getChannels(), audioDecoder.getChannelLayout(), audioDecoder.getSampleFormat());
        
        /*
         * <p>
         * A converter object we'll use to convert Humble Audio to a format that
         * Java Audio can actually play. The details are complicated, but
         * essentially this converts any audio format (represented in the
         * samples object) into a default audio format suitable for Java's
         * speaker system (which will be signed 16-bit audio, stereo
         * (2-channels), resampled to 22,050 samples per second).
         */
        MediaAudioConverter converter = MediaAudioConverterFactory
                .createConverter(MediaAudioConverterFactory.DEFAULT_JAVA_AUDIO, samples);
        
        /*
         * An AudioFrame is a wrapper for the Java Sound system that abstracts
         * away some stuff. Go read the source code if you want -- it's not very
         * complicated.
         */
        AudioFrame audioFrame = AudioFrame.make(converter.getJavaFormat());
        
        /*
         * We will use this to cache the raw-audio we pass to and from
         * the java sound system.
         */
        ByteBuffer rawAudio = null;
        
        /*
         * Now, we start walking through the container looking at each packet.
         * This is a decoding loop, and as you work with Humble you'll write a
         * lot of these.
         * 
         * Notice how in this loop we reuse all of our objects to avoid
         * reallocating them. Each call to Humble resets objects to avoid
         * unnecessary reallocation.
         */
        MediaPacket packet = MediaPacket.make();
        
        /**
         * read() returns 0 if successful else < 0
         */
        while (demuxer.read(packet) >= 0) {
            
            logger.info(String.format("Packet's stream index = %d", packet.getStreamIndex()));
            
            /*
             * Now we have a packet, let's see if it belongs to our audio stream
             */
            if (packet.getStreamIndex() == audioStreamId) {
                
                /*
                 * A packet can actually contain multiple sets of samples (or
                 * frames of samples in audio-decoding speak). So, we may need
                 * to call decode audio multiple times at different offsets in
                 * the packet's data. We capture that here.
                 */
                int offset = 0;
                int bytesRead = 0;
                
                do {
                    
                    bytesRead += audioDecoder.decode(samples, packet, offset);
                    
                    if (samples.isComplete()) {
                        
                        rawAudio = converter.toJavaAudio(rawAudio, samples);
                        audioFrame.play(rawAudio);
                    }
                    
                    offset += bytesRead;
                } while (offset < packet.getSize());
            }
        }
        
        // Some audio decoders (especially advanced ones) will cache
        // audio data before they begin decoding, so when you are done you need
        // to flush them. The convention to flush Encoders or Decoders in Humble
        // Video
        // is to keep passing in null until incomplete samples or packets are
        // returned.
        do {
            audioDecoder.decode(samples, null, 0);
            if (samples.isComplete()) {
                rawAudio = converter.toJavaAudio(rawAudio, samples);
                audioFrame.play(rawAudio);
            }
        } while (samples.isComplete());
        
        // It is good practice to close demuxers when you're done to free
        // up file handles. Humble will EVENTUALLY detect if nothing else
        // references this demuxer and close it then, but get in the habit
        // of cleaning up after yourself, and your future girlfriend/boyfriend
        // will appreciate it.
        demuxer.close();
        
        // similar with the demuxer, for the audio playback stuff, clean up
        // after yourself.
        audioFrame.dispose();
    }
    
}
