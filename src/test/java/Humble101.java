import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.humble.video.ContainerStream;
import io.humble.video.Decoder;
import io.humble.video.Demuxer;
import io.humble.video.DemuxerFormat;
import io.humble.video.DemuxerStream;
import io.humble.video.Global;
import io.humble.video.KeyValueBag;
import io.humble.video.MuxerStream;

/**
 * Project: VidsUsingXuggler
 * Package:
 * File: Humble101.java
 * 
 * @author sidmishraw
 *         Last modified: Oct 26, 2017 6:46:50 PM
 */

/**
 * <p>
 * My first try at using the Xuggler successor - Humble video processing
 * library.
 * 
 * <p>
 * In this file, I try to understand the basic concepts of
 * {@link ContainerStream}, {@link Demuxer}, {@link KeyValueBag},
 * {@link DemuxerStream}, {@link MuxerStream} etc.
 * 
 * <p>
 * The demo is a replica with minor modifications from the file located at:
 * <a href=
 * "https://github.com/artclarke/humble-video/blob/master/humble-video-demos/src/main/java/io/humble/video/demos/GetContainerInfo.java">GetContainerInfo.java</a>
 * 
 * <p>
 * I also learn about obtaining and querying metadata of the Media files.
 * 
 * @author sidmishraw
 *
 *         Qualified Name: .Humble101
 *
 */
public class Humble101 {
    
    // # Logging stuff
    private static final Logger logger = LoggerFactory.getLogger(Humble101.class);
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
     * <p>
     * Opens the video file and processes it.
     * 
     * @param filePath
     *            The path to the video file to process
     */
    private static final void processVideoFile(String filePath) {
        
        try {
            
            // A Demuxer opens up media containers, parses and de-multiplexes
            // the streams of media data without those containers.
            Demuxer demuxer = Demuxer.make();
            
            // We open the demuxer by pointing it at a URL.
            demuxer.open(filePath, null, false, true, null, null);
            
            // Once we've opened a demuxer, Humble can make a guess about the
            // DemuxerFormat. Humble supports over 100+ media container formats.
            DemuxerFormat format = demuxer.getFormat();
            
            logger.info(String.format("URL: '%s' (%s: %s)", demuxer.getURL(), format.getLongName(), format.getName()));
            
            // Many programs that make containers, such as iMovie or Adobe
            // Elements, will insert meta-data about the container. Here we
            // extract that metadata and print it.
            KeyValueBag metadata = demuxer.getMetaData();
            
            metadata.getKeys()
                    .forEach(key -> logger.info(String.format("Metadata:: (%s, %s)", key, metadata.getValue(key))));
            
            // There are a few other key pieces of information that are
            // interesting for most containers; The duration, the starting time,
            // and the estimated bit-rate.
            // This code extracts all three.
            final String formattedDuration = formatTimeStamp(demuxer.getDuration());
            
            logger.info(String.format("Duration: %s, start: %f, bitrate: %d kb/s", formattedDuration,
                    (demuxer.getStartTime() == Global.NO_PTS) ? 0 : demuxer.getStartTime() / 1000000.0,
                    demuxer.getBitRate() / 1000));
            
            /**
             * Finally, a container consists of several different independent
             * streams of data called Streams. In Humble there are two objects
             * that represent streams:
             * <li>DemuxerStream (when you are reading)</li>
             * <li>MuxerStreams (when you are writing)</li>
             */
            
            // First find the number of streams in this container.
            int ns = demuxer.getNumStreams();
            
            for (int i = 0; i < ns; i++) {
                
                DemuxerStream stream = demuxer.getStream(i);
                
                // Get the metadata of each stream
                KeyValueBag streamMetadata = stream.getMetaData();
                
                // Language is usually embedded as metadata in a stream.
                final String language = metadata.getValue("language");
                
                // We will only be able to make a decoder for streams we can
                // actually decode, so the caller should check for null.
                Decoder d = stream.getDecoder();
                
                logger.info(String.format(" Stream #0.%1$d (%2$s): %3$s", i, language,
                        d != null ? d.toString() : "unknown coder"));
                
                logger.info("Stream's Metadata:");
                
                streamMetadata.getKeys().forEach(
                        key -> logger.info(String.format("Stream Metadata :: (%s: %s)", key, metadata.getValue(key))));
            }
            
        } catch (Exception e) {
            
            logger.error(e.getMessage(), e);
        }
    }
    
    /**
     * <p>
     * Pretty prints a timestamp (in {@link Global.NO_PTS} units) into a string.
     * 
     * @param duration
     *            A timestamp in {@link Global.NO_PTS} units).
     * @return A string representing the duration.
     */
    private static String formatTimeStamp(long duration) {
        
        if (duration == Global.NO_PTS) {
            
            return "00:00:00.00";
        }
        
        double d = 1.0 * duration / Global.DEFAULT_PTS_PER_SECOND;
        int hours = (int) (d / (60 * 60));
        int mins = (int) ((d - hours * 60 * 60) / 60);
        int secs = (int) (d - hours * 60 * 60 - mins * 60);
        int subsecs = (int) ((d - (hours * 60 * 60.0 + mins * 60.0 + secs)) * 100.0);
        
        return String.format("%1$02d:%2$02d:%3$02d.%4$02d", hours, mins, secs, subsecs);
    }
}
