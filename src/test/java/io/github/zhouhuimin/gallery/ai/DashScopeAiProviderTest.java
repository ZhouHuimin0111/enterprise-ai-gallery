package io.github.zhouhuimin.gallery.ai;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.zhouhuimin.gallery.common.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class DashScopeAiProviderTest {
    private final HttpClient client=mock(HttpClient.class);
    private DashScopeAiProvider provider() {
        var p=new DashScopeAiProvider("https://workspace.cn-beijing.maas.aliyuncs.com/api/v1","test-key","wanx2.1-t2i-turbo","dashscope-result-bj.oss-cn-beijing.aliyuncs.com",new Jsons(new ObjectMapper()));ReflectionTestUtils.setField(p,"http",client);return p;
    }
    @SuppressWarnings("unchecked") private void response(int code,String json) throws Exception {
        HttpResponse<InputStream> response=mock(HttpResponse.class);when(response.statusCode()).thenReturn(code);when(response.body()).thenReturn(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        when(client.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(response);
    }
    @Test void submitsAsyncTaskWithSingleImage() throws Exception {
        response(200,"{\"output\":{\"task_id\":\"task-123\",\"task_status\":\"PENDING\"}}");assertThat(provider().submit("a garden","req")).isEqualTo("task-123");
        var request=org.mockito.ArgumentCaptor.forClass(HttpRequest.class);verify(client).send(request.capture(),any(HttpResponse.BodyHandler.class));
        assertThat(request.getValue().uri().toString()).endsWith("/api/v1/services/aigc/text2image/image-synthesis");assertThat(request.getValue().headers().firstValue("X-DashScope-Async")).contains("enable");
    }
    @Test void pollsKnownTaskRatherThanSubmittingAgain() throws Exception {
        response(200,"{\"output\":{\"task_status\":\"RUNNING\"}}");assertThat(provider().poll("task-123").status()).isEqualTo("RUNNING");
        var request=org.mockito.ArgumentCaptor.forClass(HttpRequest.class);verify(client).send(request.capture(),any(HttpResponse.BodyHandler.class));assertThat(request.getValue().method()).isEqualTo("GET");assertThat(request.getValue().uri().getPath()).endsWith("/tasks/task-123");
    }
    @Test void refusesUntrustedGeneratedImageUrl() throws Exception {
        response(200,"{\"output\":{\"task_status\":\"SUCCEEDED\",\"results\":[{\"url\":\"http://127.0.0.1/secret\"}]}}");
        assertThatThrownBy(()->provider().poll("task-123")).isInstanceOf(BusinessException.class);verify(client,times(1)).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
    }
    @Test void reportsProviderFailureWithoutLeakingResponse() throws Exception {
        response(403,"{\"message\":\"secret-details\"}");assertThatThrownBy(()->provider().submit("prompt","req")).isInstanceOf(BusinessException.class).hasMessage("AI_HTTP_403");
    }
    @Test void doesNotRetryAmbiguousSubmissionTransportError() throws Exception {
        when(client.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenThrow(new java.net.http.HttpTimeoutException("timed out"));
        assertThatThrownBy(()->provider().submit("prompt","req")).hasMessage("AI_TRANSPORT_UNCERTAIN");verify(client,times(1)).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
    }
}
