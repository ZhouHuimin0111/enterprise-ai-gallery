package io.github.zhouhuimin.gallery.ai;

public interface AiProvider {
    record Progress(String status,byte[] image,String errorCode) {}
    String mode();
    String submit(String prompt,String requestId);
    Progress poll(String taskId);
}
