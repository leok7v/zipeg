package com.zipeg;

import javax.imageio.*;
import javax.imageio.stream.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.*;
import java.util.*;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

public final class Resources {

    /* On Mac OS X 10.5 non priviliged user may (and will) download application
       on Desktop or Downloads folder, start it and, while it is running,
       move it to /Applications folder. Unless jar file is kept open this
       move will proceed and next call to any ClassLoader.getResourceAsStream
       will fail. Thus, deliberatly keep jar file open.
    */
    private static JarFile jar;

    private Resources() {
    }

    public static ImageIcon getImageIcon(String name) {
        return new ImageIcon(getImage(name));
    }

    public static Image getImage(String name) {
        // reading your own png image from resource should not be that complicated
        // but it is. Thanks for gazillion "little mother helpers"
        // from around the wonderful Java world of SPIs :(
        String location = "resources/" + name + ".png";
        Throwable error = null;
        Image image = null;
        byte[] bytes = null;
        // workaround for
        // java.lang.NullPointerException at com.ctreber.aclib.image.ico.ICOReader.getICOEntry
        // from:
        // http://stackoverflow.com/questions/878521/nullpointerexception-using-imageio-read
        int ix = location.lastIndexOf('.');
        if (ix > 0) {
            String suffix = location.substring(ix + 1).toLowerCase();
            Iterator<ImageReader> imageReaders = ImageIO.getImageReadersByFormatName(suffix);
            while (image == null && imageReaders != null && imageReaders.hasNext()) {
                try {
                    ImageReader imageReader = imageReaders.next();
                    InputStream s = getResourceAsStream(location);
                    ImageInputStream is = ImageIO.createImageInputStream(s);
                    imageReader.setInput(is, true);
                    ImageReadParam param = imageReader.getDefaultReadParam();
                    image = imageReader.read(0, param);
                } catch (Throwable t) {
                    error = t;
                }
            }
        }
        if (image != null) {
            return image;
        }
        try {
            java.net.URL url = Resources.class.getResource(location);
            image = ImageIO.read(url);
        } catch (Throwable t) {
            error = t;
        }
        if (image != null) {
            return image;
        }
        if (image == null) {
            try {
                image = ImageIO.read(getResourceAsStream(location));
            } catch (Throwable t) {
                error = t;
            }
        }
        try {
            bytes = readBytes(location);
        } catch (Throwable t) {
            error = t;
        }
        if (bytes != null) {
            try {
                image = ensureImage(Toolkit.getDefaultToolkit().createImage(bytes));
            } catch (Throwable t) {
                error = t;
            }
            if (image == null) {
                try {
                    image = ImageIO.read(new ByteArrayInputStream(bytes));
                } catch (Throwable t) {
                    error = t;
                }
            }
            // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4764639
            // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4821108
            if (image == null) {
                boolean uc = ImageIO.getUseCache();
                try {
                    ImageIO.setUseCache(!uc);
                    image = ImageIO.read(new ByteArrayInputStream(bytes));
                } catch (Throwable t) {
                    error = t;
                } finally {
                    ImageIO.setUseCache(uc);
                }
            }
        }
        if (image != null) {
            return image;
        } else {
            throw new Error("error reading: " + name + " " + (bytes == null ? -1 : bytes.length), error);
        }
    }

    private static Image ensureImage(Image image) {
        if (image == null) {
            return null;
        }
        MediaTracker mt = new MediaTracker(new JComponent() {});
        mt.addImage(image, 0);
        try {
            mt.waitForAll();
        } catch (InterruptedException e) {
            /* ignore */
        } finally {
            mt.removeImage(image);
        }
        return image.getWidth(null) > 0 && image.getHeight(null) > 0 ? image : null;
    }

    public static byte[] getBytes(String name) {
        String location = "resources/" + name + ".png";
        return readBytes(location);
    }

    private static JarFile getJarFile(String u) throws IOException {
        if (jar == null) {
            int sep = u.lastIndexOf('!');
            String j = u.substring(0, sep);
            if (j.startsWith("jar:file:")) {
                j = j.substring("jar:file:".length());
            }
            if (j.startsWith("file:")) {
                j = j.substring("file:".length());
            }
            jar = new JarFile(j);
        }
        return jar;
    }

    public static InputStream getResourceAsStream(String location) {
        try {
            java.net.URL url = Resources.class.getResource(location);
            assert url != null : location;
            String u = url.getFile().replaceAll("%20", " ");
            InputStream s;
            // see: http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4730642
            if (u.toLowerCase().indexOf(".jar!/") >= 0) {
                JarFile jar = getJarFile(u);
                ZipEntry ze = jar.getEntry(u.substring(u.lastIndexOf('!') + 2));
                s = jar.getInputStream(ze);
            } else {
                s = Resources.class.getResourceAsStream(location);
            }
            assert s != null : location;
            return s;
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    private static byte[] readBytes(String location) {
        InputStream s = null;
        try {
            s = getResourceAsStream(location);
            assert s != null : location;
            return Util.readBytes(s);
        } catch (IOException e) {
            throw new Error(e);
        } finally {
            Util.close(s);
        }
    }

    public static BufferedImage asBufferedImage(Image img) {
        return asBufferedImage(img, 0, 0);
    }

    public static BufferedImage asBufferedImage(Image img, int dx, int dy) {
        assert img.getWidth(null) > 0 : img.getWidth(null);
        assert img.getHeight(null) > 0 : img.getHeight(null);
        int w = img.getWidth(null) + Math.max(dx, 0);
        int h = img.getHeight(null) + Math.max(dy, 0);
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics g = null;
        try {
            g = bi.getGraphics();
            g.drawImage(img, dx, dy, null);
        } finally {
            if (g != null) {
                g.dispose();
            }
        }
        return bi;
    }

    /** adjust Saturation (i == 1) or Brightness (i == 2)
     * @param bi image to adjust parameters of (must be ABGR
     * @param adjust value to adjust must be > 0.0f
     * @param i index of hsb to adjust
     */
    public static void adjustHSB(BufferedImage bi, float adjust, int i) {
        assert bi.getType() == BufferedImage.TYPE_INT_ARGB : "must be TYPE_INT_ARGB";
        int n = bi.getData().getNumBands();
        assert n == 4 : "must have alpha component";
        assert adjust > 0.0f;
        assert i > 0 : "adjusting hue is strange action with unpredictable color shift";
        int[] pixels = new int[bi.getWidth() * bi.getHeight() * n];
        float[] hsb = new float[3];
        bi.getData().getPixels(0, 0, bi.getWidth(), bi.getHeight(), pixels);
        int ix = 0;
        WritableRaster wr = bi.getRaster();
        for (int y = 0; y < bi.getHeight(); y++) {
            for (int x = 0; x < bi.getWidth(); x++) {
                int r = pixels[ix];
                int g = pixels[ix + 1];
                int b = pixels[ix + 2];
                int a = pixels[ix + 3];
                Color.RGBtoHSB(r, g, b, hsb);
                assert hsb[0] >= 0 && hsb[1] >= 0 && hsb[2] >= 0;
                hsb[i] = Math.max(0.0f, Math.min(hsb[i] * adjust, 1.0f));
                int c = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
                wr.getDataBuffer().setElem(ix / n, (c & 0xFFFFFF) | (a << 24));
                ix += n;
            }
        }
    }

}
