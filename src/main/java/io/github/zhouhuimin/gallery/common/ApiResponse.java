package io.github.zhouhuimin.gallery.common;

public record ApiResponse<T>(String code,String message,T data) {
    public static <T> ApiResponse<T> ok(T data) { return new ApiResponse<>("OK","成功",data); }
    public static ApiResponse<Void> fail(ErrorCode code,String message) { return new ApiResponse<>(code.name(),message,null); }
}
