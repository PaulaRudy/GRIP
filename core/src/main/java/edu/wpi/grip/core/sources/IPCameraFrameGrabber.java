/*
 * Copyright (C) 2013 Greg Perry
 * Modified for use in GRIP to reduce heap allocations
 *
 * Licensed either under the Apache License, Version 2.0, or (at your option)
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation (subject to the "Classpath" exception),
 * either version 2, or any later version (collectively, the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *     http://www.gnu.org/licenses/
 *     http://www.gnu.org/software/classpath/license.html
 *
 * or as provided in the LICENSE.txt file that accompanied this code.
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.wpi.grip.core.sources;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameConverter;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.OpenCVFrameConverter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;

import static org.bytedeco.javacpp.opencv_core.*;
import static org.bytedeco.javacpp.opencv_imgcodecs.cvDecodeImage;

public class IPCameraFrameGrabber extends FrameGrabber {

    /*
     * excellent reference - http://www.jpegcameras.com/ foscam url
     * http://host/videostream.cgi?user=username&pwd=password
     * http://192.168.0.59:60/videostream.cgi?user=admin&pwd=password android ip
     * cam http://192.168.0.57:8080/videofeed
     */

    private static Exception loadingException = null;

    public static void tryLoad() throws Exception {
        if (loadingException != null) {
            throw loadingException;
        } else {
            try {
                Loader.load(org.bytedeco.javacpp.opencv_highgui.class);
            } catch (Throwable t) {
                throw loadingException = new Exception("Failed to load " + IPCameraFrameGrabber.class, t);
            }
        }
    }

    private URL url;

    private URLConnection connection;
    private InputStream input;
    private byte[] pixelBuffer = new byte[1024];
    private Map<String, List<String>> headerfields;
    private String boundryKey;
    private IplImage decoded = null;
    private FrameConverter<IplImage> converter = new OpenCVFrameConverter.ToIplImage();

    public IPCameraFrameGrabber(String urlstr) throws MalformedURLException {
        url = new URL(urlstr);
    }

    @Override
    public void start() {

        try {
            connection = url.openConnection();
            headerfields = connection.getHeaderFields();
            if (headerfields.containsKey("Content-Type")) {
                List<String> ct = headerfields.get("Content-Type");
                for (int i = 0; i < ct.size(); ++i) {
                    String key = ct.get(i);
                    int j = key.indexOf("boundary=");
                    if (j != -1) {
                        boundryKey = key.substring(j + 9); // FIXME << fragile
                    }
                }
            }
            input = connection.getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stop() throws Exception {
        try {
            input.close();
            input = null;
            connection = null;
            url = null;
            if (decoded != null) {
                cvReleaseImage(decoded);
            }
        } catch (IOException e) {
            throw new Exception(e.getMessage(), e);
        }
    }

    @Override
    public void trigger() throws Exception {
    }

    @Override
    public Frame grab() throws Exception {
        try {
            byte[] b = readImage();
            CvMat mat = cvMat(1, b.length, CV_8UC1, new BytePointer(b));
            if (decoded != null) {
                cvReleaseImage(decoded);
            }
            return converter.convert(decoded = cvDecodeImage(mat));
        } catch (IOException e) {
            throw new Exception(e.getMessage(), e);
        }
    }

    public BufferedImage grabBufferedImage() throws IOException {
        BufferedImage bi = ImageIO.read(new ByteArrayInputStream(readImage()));
        return bi;
    }

    byte[] readImage() throws IOException {
        StringBuffer sb = new StringBuffer();
        int c;
        // read http subheader
        while ((c = input.read()) != -1) {
            if (c > 0) {
                sb.append((char) c);
                if (c == 13) {
                    sb.append((char) input.read());// '10'+
                    c = input.read();
                    sb.append((char) c);
                    if (c == 13) {
                        sb.append((char) input.read());// '10'
                        break; // done with subheader
                    }

                }
            }
        }
        // find embedded jpeg in stream
        String subheader = sb.toString();
        //log.debug(subheader);
        int contentLength = -1;
        // if (boundryKey == null)
        // {
        // Yay! - server was nice and sent content length
        int c0 = subheader.indexOf("Content-Length: ");
        int c1 = subheader.indexOf('\r', c0);

        if (c0 < 0) {
            //log.info("no content length returning null");
            return null;
        }

        c0 += 16;
        contentLength = Integer.parseInt(subheader.substring(c0, c1).trim());
        //log.debug("Content-Length: " + contentLength);

        // adaptive size - careful - don't want a 2G jpeg
        ensureBufferCapacity(contentLength);

        while (input.available() < contentLength) ;
        input.read(pixelBuffer, 0, contentLength);
        input.read();// \r
        input.read();// \n
        input.read();// \r
        input.read();// \n

        return pixelBuffer;
    }

    /**
     * Grow the pixel buffer if necessary.  Using this method instead of allocating a new buffer every time a frame
     * is grabbed improves performance by reducing the frequency of garbage collections.  In a simple test, the
     * unmodified version of IPCameraFrameGrabber caused about 200MB of allocations within 13 seconds.  In this
     * version, almost no additional heap space is typically allocated per frame.
     * <p>
     * The downside to this is that the returned frames can't really be modified, so this probably won't go upstream,
     * but it's useful for us because in GRIP we don't operate on images in-place.
     */
    private void ensureBufferCapacity(int desiredCapacity) {
        int capacity = pixelBuffer.length;

        while (capacity < desiredCapacity) {
            capacity *= 2;
        }

        if (capacity > pixelBuffer.length) {
            pixelBuffer = new byte[capacity];
        }
    }

    @Override
    public void release() throws Exception {
    }

}
