/*
 * $Id: PostZipFilter.java 348 2007-08-16 14:02:52Z ramiro $
 */
package lia.util.net.copy.filters.examples;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.security.auth.Subject;

import lia.util.net.copy.filters.Postprocessor;
import lia.util.net.copy.filters.ProcessorInfo;

/**
 * Simple postProcess FDT Filter. It decompresses the zip files received
 * from the remote peer and then deletes the archives files.
 *  
 * @author ramiro
 */
public class PostZipFilter implements Postprocessor {

    public void postProcessFileList(ProcessorInfo processorInfo, Subject peerSubject, Throwable downCause, String downMessage) throws Exception {

        System.out.println(" [ PostZipFilter ] Subject: " + peerSubject);

        for(int i=0; i<processorInfo.fileList.length; i++) {

            // Open the ZIP file
            final String inFilename = processorInfo.destinationDir + File.separator + processorInfo.fileList[i];
            ZipInputStream in = new ZipInputStream(new FileInputStream(inFilename));

            // Get the first entry
            final ZipEntry entry = in.getNextEntry();

            // Open the output file
            String outFilename = inFilename.substring(0, inFilename.length() - 4);
            OutputStream out = new FileOutputStream(outFilename);

            // Transfer bytes from the ZIP file to the output file
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }

            // Close the streams
            out.close();
            in.close();

            //delete the zip file!
            new File(inFilename).delete();
        }
    }
}
