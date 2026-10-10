package io.github.zhouhuimin.gallery.storage;
import io.github.zhouhuimin.gallery.common.BusinessException;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
class ImageProcessorTest {
    private final ImageProcessor processor=new ImageProcessor();
    @Test void refusesSvgAndFakeImageBytes() {
        assertThatThrownBy(()->processor.normalize("<svg onload='alert(1)'></svg>".getBytes(StandardCharsets.UTF_8))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->processor.normalize(new byte[]{1,2,3})).isInstanceOf(BusinessException.class);
    }
    @Test void normalizesAndRotatesExactPixels() {
        var image=new BufferedImage(4,2,BufferedImage.TYPE_INT_RGB);image.setRGB(0,0,0xff0000);image.setRGB(3,1,0x00ff00);
        byte[] bytes=processor.encode(image).bytes();var result=processor.transform(bytes,0,0,4,2,90);var decoded=processor.decode(result.bytes());
        assertThat(result.width()).isEqualTo(2);assertThat(result.height()).isEqualTo(4);assertThat(decoded.getRGB(1,0)&0xffffff).isEqualTo(0xff0000);assertThat(decoded.getRGB(0,3)&0xffffff).isEqualTo(0x00ff00);
    }
    @Test void rejectsOutOfBoundsCropAndUnsupportedRotation() {
        byte[] bytes=processor.encode(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB)).bytes();
        assertThatThrownBy(()->processor.transform(bytes,Integer.MAX_VALUE,0,1,1,0)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->processor.transform(bytes,0,0,2,2,45)).isInstanceOf(BusinessException.class);
    }
    @Test void rejectsOversizedPixelsBeforeDecoding() throws Exception {
        var output=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new BufferedImage(5000,4000,BufferedImage.TYPE_BYTE_GRAY),"png",output);
        assertThatThrownBy(()->processor.normalize(output.toByteArray())).isInstanceOf(BusinessException.class);
    }
}
