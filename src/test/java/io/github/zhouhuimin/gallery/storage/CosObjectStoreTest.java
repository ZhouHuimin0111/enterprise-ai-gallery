package io.github.zhouhuimin.gallery.storage;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.*;
import io.github.zhouhuimin.gallery.common.BusinessException;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class CosObjectStoreTest {
    private static final String KEY="objects/123e4567-e89b-12d3-a456-426614174000.png";
    @Test void writesPrivateBucketObjectWithLengthAndType() {
        var client=mock(COSClient.class);var store=new CosObjectStore(client,"private-bucket-123");store.put(KEY,new byte[]{1,2},"image/png");
        var capture=org.mockito.ArgumentCaptor.forClass(PutObjectRequest.class);verify(client).putObject(capture.capture());
        assertThat(capture.getValue().getBucketName()).isEqualTo("private-bucket-123");assertThat(capture.getValue().getMetadata().getContentLength()).isEqualTo(2);assertThat(capture.getValue().getMetadata().getContentType()).isEqualTo("image/png");
    }
    @Test void readsAndDeletesWithoutExposingSignedPublicUrl() {
        var client=mock(COSClient.class);var object=new COSObject();object.setObjectContent(new com.qcloud.cos.model.COSObjectInputStream(new ByteArrayInputStream(new byte[]{3,4}),new org.apache.http.client.methods.HttpGet("https://example.com/object")));when(client.getObject("private-bucket",KEY)).thenReturn(object);
        var store=new CosObjectStore(client,"private-bucket");assertThat(store.read(KEY)).containsExactly(3,4);store.delete(KEY);verify(client).deleteObject("private-bucket",KEY);
    }
    @Test void rejectsPathTraversalBeforeCallingSdk() {
        var client=mock(COSClient.class);var store=new CosObjectStore(client,"private-bucket");
        assertThatThrownBy(()->store.put("../secret",new byte[1],"image/png")).isInstanceOf(BusinessException.class);verifyNoInteractions(client);
    }
}
