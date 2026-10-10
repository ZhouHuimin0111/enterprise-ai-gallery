package io.github.zhouhuimin.gallery.storage;
import com.qcloud.cos.*;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.*;
import com.qcloud.cos.region.Region;
import io.github.zhouhuimin.gallery.common.*;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.io.ByteArrayInputStream;

@Component @ConditionalOnProperty(name="app.storage.mode",havingValue="cos")
public class CosObjectStore implements ObjectStore {
    private final COSClient client;private final String bucket;
    CosObjectStore(COSClient client,String bucket) { this.client=client;this.bucket=bucket; }
    @org.springframework.beans.factory.annotation.Autowired
    public CosObjectStore(@Value("${app.storage.cos-secret-id}") String id,@Value("${app.storage.cos-secret-key}") String key,
        @Value("${app.storage.cos-region}") String region,@Value("${app.storage.cos-bucket}") String bucket) {
        if(id.isBlank()||key.isBlank()||region.isBlank()||bucket.isBlank()) throw new IllegalStateException("COS configuration required");
        this.bucket=bucket;var config=new ClientConfig(new Region(region));config.setHttpProtocol(HttpProtocol.https);config.setConnectionTimeout(5000);config.setSocketTimeout(15000);config.setMaxErrorRetry(1);
        client=new COSClient(new BasicCOSCredentials(id,key),config);
    }
    private void validate(String key) { BusinessException.require(key.matches("objects/[a-f0-9-]{36}\\.png"),ErrorCode.BAD_REQUEST); }
    public void put(String key,byte[] bytes,String type) {
        validate(key);try { var meta=new ObjectMetadata();meta.setContentLength(bytes.length);meta.setContentType(type);client.putObject(new PutObjectRequest(bucket,key,new ByteArrayInputStream(bytes),meta)); }
        catch(Exception e) { throw new BusinessException(ErrorCode.STORAGE_UNAVAILABLE); }
    }
    public byte[] read(String key) {
        validate(key);try(var object=client.getObject(bucket,key);var stream=object.getObjectContent()) {
            byte[] bytes=stream.readNBytes(ImageProcessor.MAX_BYTES+1);BusinessException.require(bytes.length<=ImageProcessor.MAX_BYTES,ErrorCode.STORAGE_UNAVAILABLE);return bytes;
        } catch(Exception e) { throw new BusinessException(ErrorCode.STORAGE_UNAVAILABLE); }
    }
    public void delete(String key) { validate(key);try { client.deleteObject(bucket,key); } catch(Exception e) { throw new BusinessException(ErrorCode.STORAGE_UNAVAILABLE); } }
    public String mode() { return "cos"; }
    @PreDestroy public void close() { client.shutdown(); }
}
