package io.github.zhouhuimin.gallery.ai;
import io.github.zhouhuimin.gallery.storage.ImageProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Random;

@Component @ConditionalOnProperty(name="app.ai.mode",havingValue="demo",matchIfMissing=true)
public class DemoAiProvider implements AiProvider {
    private final long delayMs;
    public DemoAiProvider(@org.springframework.beans.factory.annotation.Value("${app.ai.demo-delay-ms:10000}") long delayMs) { this.delayMs=Math.max(0,Math.min(delayMs,600000)); }
    public String mode() { return "demo"; }
    public String submit(String prompt,String requestId) { return "demo-"+System.currentTimeMillis()+"-"+requestId; }
    public Progress poll(String taskId) {
        if(taskId.matches("demo-[0-9]{13}-.*")) {
            long submitted=Long.parseLong(taskId.substring(5,18));
            if(System.currentTimeMillis()-submitted<delayMs) return new Progress("RUNNING",null,null);
        }
        return new Progress("SUCCEEDED",art(taskId.hashCode(),"DEMO / NOT AI GENERATED"),null);
    }
    public static byte[] art(int seed,String label) {
        int w=960,h=640;var image=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();var random=new Random(seed);
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            var colors=new Color[]{new Color(225,232,228),new Color(225,216,202),new Color(192,210,218),new Color(218,220,231),new Color(218,222,195)};
            g.setColor(colors[Math.floorMod(seed,colors.length)]);g.fillRect(0,0,w,h);
            for(int i=0;i<7;i++) { g.setColor(new Color(30+random.nextInt(100),65+random.nextInt(90),70+random.nextInt(100),150));int x=random.nextInt(w),y=random.nextInt(h);int size=100+random.nextInt(380);if(i%2==0) g.fillOval(x-size/2,y-size/2,size,size);else g.fillRoundRect(x-size/2,y-size/2,size,size,40,40); }
            g.setColor(new Color(245,243,236));g.fillRect(0,h-70,w,70);g.setColor(new Color(34,62,58));g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,22));g.drawString(label,32,h-28);
        } finally { g.dispose(); }return new ImageProcessor().encode(image).bytes();
    }
}
