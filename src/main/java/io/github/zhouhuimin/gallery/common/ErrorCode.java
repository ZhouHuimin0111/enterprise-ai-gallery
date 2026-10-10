package io.github.zhouhuimin.gallery.common;

public enum ErrorCode {
    BAD_REQUEST(400,"请求参数不正确"), UNAUTHORIZED(401,"请先登录"), FORBIDDEN(403,"没有操作权限"),
    NOT_FOUND(404,"资源不存在"), CONFLICT(409,"数据已变更，请刷新后重试"), QUOTA_EXCEEDED(409,"空间容量或图片数量已达上限"),
    RATE_LIMITED(429,"请求过于频繁，请稍后重试"), STORAGE_UNAVAILABLE(503,"图片存储暂不可用"),
    AI_UNAVAILABLE(503,"AI 服务暂不可用"), INTERNAL_ERROR(500,"服务异常，请稍后重试");
    public final int http; public final String message;
    ErrorCode(int http,String message) { this.http=http; this.message=message; }
}
