package io.github.zhouhuimin.gallery.ai;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.zhouhuimin.gallery.common.*;
import io.github.zhouhuimin.gallery.storage.ImageProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.io.InputStream;

@Component @ConditionalOnProperty(name="app.ai.mode",havingValue="dashscope")
public class DashScopeAiProvider implements AiProvider {
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final URI base;private final String key,model;private final Set<String> hosts;private final Jsons json;
    public DashScopeAiProvider(@Value("${app.ai.base-url}") String base,@Value("${app.ai.api-key}") String key,@Value("${app.ai.model}") String model,@Value("${app.ai.result-hosts}") String hosts,Jsons json) {
        if(base.isBlank()||key.isBlank()) throw new IllegalStateException("DashScope HTTPS base URL and key required");
        this.base=URI.create(base.replaceAll("/$","") + "/");this.key=key;this.model=model;this.json=json;
        if(!"https".equals(this.base.getScheme()) || this.base.getHost()==null || !(this.base.getHost().equals("dashscope.aliyuncs.com")||this.base.getHost().endsWith(".maas.aliyuncs.com"))) throw new IllegalStateException("Untrusted DashScope endpoint");
        if(!Set.of("wanx2.1-t2i-turbo","wanx2.1-t2i-plus","wan2.2-t2i-flash","wan2.2-t2i-plus").contains(model)) throw new IllegalStateException("Use supported asynchronous text2image model");
        this.hosts=new HashSet<>(Arrays.asList(hosts.split(",")));
    }
    public String mode() { return "dashscope"; }
    private JsonNode send(HttpRequest request) {
        try {
            var response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
            try(var input=response.body()) {
                byte[] bytes=input.readNBytes(65537);BusinessException.require(bytes.length<=65536,ErrorCode.AI_UNAVAILABLE);
                if(response.statusCode()<200 || response.statusCode()>=300) throw new BusinessException(ErrorCode.AI_UNAVAILABLE,"AI_HTTP_"+response.statusCode());
                var result=json.read(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
                if(result.has("code")) throw new BusinessException(ErrorCode.AI_UNAVAILABLE,"AI_PROVIDER_REJECTED");return result;
            }
        } catch(BusinessException e) { throw e; } catch(InterruptedException e) { Thread.currentThread().interrupt();throw new BusinessException(ErrorCode.AI_UNAVAILABLE,"AI_TRANSPORT_UNCERTAIN"); }
        catch(Exception e) { throw new BusinessException(ErrorCode.AI_UNAVAILABLE,"AI_TRANSPORT_UNCERTAIN"); }
    }
    public String submit(String prompt,String requestId) {
        var body=Map.of("model",model,"input",Map.of("prompt",prompt),"parameters",Map.of("size","1024*1024","n",1,"watermark",true));
        var request=HttpRequest.newBuilder(base.resolve("services/aigc/text2image/image-synthesis")).timeout(Duration.ofSeconds(20))
            .header("Authorization","Bearer "+key).header("Content-Type","application/json").header("X-DashScope-Async","enable")
            .POST(HttpRequest.BodyPublishers.ofString(json.write(body))).build();
        String id=send(request).path("output").path("task_id").asText();
        BusinessException.require(id.matches("[a-zA-Z0-9_-]{1,100}"),ErrorCode.AI_UNAVAILABLE);return id;
    }
    public Progress poll(String taskId) {
        BusinessException.require(taskId.matches("[a-zA-Z0-9_-]{1,100}"),ErrorCode.AI_UNAVAILABLE);
        var output=send(HttpRequest.newBuilder(base.resolve("tasks/"+taskId)).timeout(Duration.ofSeconds(15)).header("Authorization","Bearer "+key).GET().build()).path("output");
        String status=output.path("task_status").asText();
        if(Set.of("PENDING","RUNNING").contains(status)) return new Progress("RUNNING",null,null);
        if(!"SUCCEEDED".equals(status)) return new Progress("FAILED",null,"AI_TASK_"+(Set.of("FAILED","CANCELED","UNKNOWN").contains(status)?status:"INVALID"));
        var results=output.path("results");if(!results.isArray()||results.isEmpty()) return new Progress("FAILED",null,"AI_EMPTY_RESULT");
        return new Progress("SUCCEEDED",download(URI.create(results.get(0).path("url").asText())),null);
    }
    private byte[] download(URI uri) {
        BusinessException.require("https".equals(uri.getScheme()) && hosts.contains(uri.getHost()) && uri.getUserInfo()==null && (uri.getPort()==-1 || uri.getPort()==443),ErrorCode.AI_UNAVAILABLE);
        try {
            for(var address:InetAddress.getAllByName(uri.getHost())) BusinessException.require(!address.isAnyLocalAddress()&&!address.isLoopbackAddress()&&!address.isSiteLocalAddress()&&!address.isLinkLocalAddress(),ErrorCode.AI_UNAVAILABLE);
            var response=http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
            try(InputStream stream=response.body()) { BusinessException.require(response.statusCode()==200,ErrorCode.AI_UNAVAILABLE);byte[] bytes=stream.readNBytes(ImageProcessor.MAX_BYTES+1);BusinessException.require(bytes.length<=ImageProcessor.MAX_BYTES,ErrorCode.AI_UNAVAILABLE);return bytes; }
        } catch(BusinessException e) { throw e; } catch(InterruptedException e) { Thread.currentThread().interrupt();throw new BusinessException(ErrorCode.AI_UNAVAILABLE); }
        catch(Exception e) { throw new BusinessException(ErrorCode.AI_UNAVAILABLE); }
    }
}
