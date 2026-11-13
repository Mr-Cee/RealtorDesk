import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Builds the same house emblem for the Windows launcher and Swing windows. */
public class CreateAppIcon {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        Path output=Path.of(args[0]);Files.createDirectories(output);
        int[] sizes={16,32,48,64,128,256};
        byte[][] images=new byte[sizes.length][];
        for(int i=0;i<sizes.length;i++) {
            int size=sizes[i];var image=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
            var g=image.createGraphics();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.scale(size/256.0,size/256.0);
            g.setColor(new Color(24,48,75));g.fill(new RoundRectangle2D.Double(0,0,256,256,54,54));
            g.setColor(new Color(239,244,248));
            var roof=new Path2D.Double();roof.moveTo(38,124);roof.lineTo(128,48);roof.lineTo(218,124);roof.lineTo(201,144);roof.lineTo(128,83);roof.lineTo(55,144);roof.closePath();g.fill(roof);
            g.fill(new RoundRectangle2D.Double(72,128,112,83,8,8));
            g.setColor(new Color(216,174,92));g.fill(new RoundRectangle2D.Double(111,157,34,54,4,4));
            g.dispose();var bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);images[i]=bytes.toByteArray();
            if(size==256)Files.write(output.resolve("app-icon.png"),images[i]);
        }
        int offset=6+16*sizes.length,total=offset;for(byte[] image:images)total+=image.length;
        var ico=ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        ico.putShort((short)0).putShort((short)1).putShort((short)sizes.length);
        for(int i=0;i<sizes.length;i++) {
            ico.put((byte)(sizes[i]==256?0:sizes[i])).put((byte)(sizes[i]==256?0:sizes[i])).put((byte)0).put((byte)0);
            ico.putShort((short)1).putShort((short)32).putInt(images[i].length).putInt(offset);offset+=images[i].length;
        }
        for(byte[] image:images)ico.put(image);
        Files.write(output.resolve("RealtorDesk.ico"),ico.array());
    }
}
