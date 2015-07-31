package hipi.image.io;

import hipi.image.HipiImageHeader;
import hipi.image.HipiImageHeader.HipiImageFormat;
import hipi.image.HipiImageHeader.HipiColorSpace;
import hipi.image.HipiImage;
import hipi.image.HipiImage.HipiImageType;
import hipi.image.RasterImage;
import hipi.image.HipiImageFactory;
import hipi.image.PixelArray;
import hipi.image.io.ExifDataUtils;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.HashMap;

import javax.imageio.IIOImage;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

public abstract class ImageCodec implements ImageDecoder, ImageEncoder {

  // By default use ImageIO plugins to decode image
  public HipiImage decodeImage(InputStream inputStream, HipiImageHeader imageHeader, 
			       HipiImageFactory imageFactory, boolean includeExifData)
    throws IllegalArgumentException, IOException {
    
    // Verify image factory
    if (!(imageFactory.getType() == HipiImageType.FLOAT || imageFactory.getType() == HipiImageType.BYTE)) {
      throw new IllegalArgumentException("Image decoder supports only FloatImage and ByteImage output types.");
    }

    DataInputStream dis = new DataInputStream(new BufferedInputStream(inputStream));
    dis.mark(Integer.MAX_VALUE);

    // Find suitable ImageIO plugin (should be TwelveMonkeys)
    BufferedImage javaImage = ImageIO.read(dis);//inputStream);

    int w = javaImage.getWidth();
    int h = javaImage.getHeight();

    // Check that image dimensions in header match those in JPEG
    if (w != imageHeader.getWidth() || h != imageHeader.getHeight()) {
      System.out.println(String.format("Dimensions read from JPEG: %d x %d", w, h));
      System.out.println(imageHeader);
      throw new IllegalArgumentException("Image dimensions in header do not match those in JPEG.");
    }

    // Create output image
    RasterImage image = null;
    try {
      image = (RasterImage)imageFactory.createImage(imageHeader);
    } catch (Exception e) {
      System.err.println(String.format("Fatal error while creating image object [%s]", e.getMessage()));
      e.printStackTrace();
      System.exit(1);
    }

    PixelArray pa = image.getPixelArray();
    
    for (int j=0; j<h; j++) {
      for (int i=0; i<w; i++) {

	// Retrieve 8-bit non-linear sRGB value packed into int
	int pixel = javaImage.getRGB(i,j); 

	int red = (pixel >> 16) & 0xff;
	int grn = (pixel >>  8) & 0xff;
	int blu = (pixel      ) & 0xff;

	// Set value in pixel array using routine designed for sRGB values
	pa.setElemNonLinSRGB((j*w+i)*3+0, red);
	pa.setElemNonLinSRGB((j*w+i)*3+1, grn);
	pa.setElemNonLinSRGB((j*w+i)*3+2, blu);

      }
    }

    if (includeExifData) {      
      // Extract EXIF data from image stream and store in image header
      dis.reset();
      try {
	imageHeader.setExifData(ExifDataReader.extractAndFlatten(dis));
      } catch (IOException ex) {
	System.err.println("Failed to extract EXIF data for image record.");
      }
    }

    return image;
  }

  // Use ImageIO plugins to encode raster image
  protected void encodeRasterImage(RasterImage image, ImageWriter writer, ImageWriteParam writeParams) throws IOException {

    int w = image.getWidth();
    int h = image.getHeight();

    BufferedImage bufferedImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

    PixelArray pa = image.getPixelArray();
    int[] rgb = new int[w*h];
    for (int i=0; i<w*h; i++) {

      int r = pa.getElemNonLinSRGB(i*3+0);
      int g = pa.getElemNonLinSRGB(i*3+1);
      int b = pa.getElemNonLinSRGB(i*3+2);

      rgb[i] = (r << 16) | (g << 8) | b;
    }
    bufferedImage.setRGB(0, 0, w, h, rgb, 0, w);
    IIOImage iioImage = new IIOImage(bufferedImage, null, null);
    writer.write(null, iioImage, writeParams);

  }

}
