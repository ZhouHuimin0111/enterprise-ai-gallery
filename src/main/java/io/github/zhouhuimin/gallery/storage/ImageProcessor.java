package io.github.zhouhuimin.gallery.storage;
import cn.hutool.crypto.digest.DigestUtil;
import io.github.zhouhuimin.gallery.common.*;
import org.springframework.stereotype.Component;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;

@Component
public class ImageProcessor {
    public static final int MAX_BYTES=10*1024*1024;public static final long MAX_PIXELS=16_000_000;
    public record Image(byte[] bytes,int width,int height,String sha256) {}
    public BufferedImage decode(byte[] bytes) {
        BusinessException.require(bytes.length>0 && bytes.length<=MAX_BYTES,ErrorCode.BAD_REQUEST);
        try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers=ImageIO.getImageReaders(input);BusinessException.require(readers.hasNext(),ErrorCode.BAD_REQUEST);
            var reader=readers.next();try {
                String format=reader.getFormatName();BusinessException.require(format.equalsIgnoreCase("PNG")||format.equalsIgnoreCase("JPEG"),ErrorCode.BAD_REQUEST);
                reader.setInput(input,true,true);int w=reader.getWidth(0),h=reader.getHeight(0);
                BusinessException.require(w>0 && h>0 && (long)w*h<=MAX_PIXELS,ErrorCode.BAD_REQUEST);
                return reader.read(0);
            } finally { reader.dispose(); }
        } catch(BusinessException e) { throw e; } catch(Exception e) { throw new BusinessException(ErrorCode.BAD_REQUEST,"图片格式损坏或不受支持"); }
    }
    public Image encode(BufferedImage image) {
        try(var output=new ByteArrayOutputStream()) {
            ImageIO.write(image,"png",output);byte[] bytes=output.toByteArray();BusinessException.require(bytes.length<=MAX_BYTES,ErrorCode.BAD_REQUEST);
            return new Image(bytes,image.getWidth(),image.getHeight(),DigestUtil.sha256Hex(bytes));
        } catch(BusinessException e) { throw e; } catch(Exception e) { throw new BusinessException(ErrorCode.BAD_REQUEST); }
    }
    public Image normalize(byte[] bytes) { return encode(decode(bytes)); }
    public Image transform(byte[] bytes,int x,int y,int width,int height,int rotation) {
        var source=decode(bytes);
        BusinessException.require(x>=0 && y>=0 && width>0 && height>0 && (long)x+width<=source.getWidth() && (long)y+height<=source.getHeight(),ErrorCode.BAD_REQUEST);
        BusinessException.require(rotation==0||rotation==90||rotation==180||rotation==270,ErrorCode.BAD_REQUEST);
        var crop=source.getSubimage(x,y,width,height);
        int w=(rotation==90||rotation==270)?height:width,h=(rotation==90||rotation==270)?width:height;
        var result=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        var g=result.createGraphics();try {
            switch(rotation) { case 90 -> { g.translate(w,0);g.rotate(Math.PI/2); } case 180 -> { g.translate(w,h);g.rotate(Math.PI); } case 270 -> { g.translate(0,h);g.rotate(-Math.PI/2); } default -> {} }
            g.drawImage(crop,0,0,null);
        } finally { g.dispose(); }return encode(result);
    }
}
