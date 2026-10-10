package io.github.zhouhuimin.gallery.common;

public class BusinessException extends RuntimeException {
    public final ErrorCode code;
    public BusinessException(ErrorCode code) { super(code.message); this.code=code; }
    public BusinessException(ErrorCode code,String message) { super(message); this.code=code; }
    public static void require(boolean condition,ErrorCode code) { if(!condition) throw new BusinessException(code); }
}
