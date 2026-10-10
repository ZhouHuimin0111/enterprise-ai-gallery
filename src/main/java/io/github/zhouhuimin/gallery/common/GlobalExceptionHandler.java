package io.github.zhouhuimin.gallery.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Slf4j @RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiResponse<Void>> business(BusinessException e) { return ResponseEntity.status(e.code.http).body(ApiResponse.fail(e.code,e.getMessage())); }
    @ExceptionHandler({MethodArgumentNotValidException.class, jakarta.validation.ConstraintViolationException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class, org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
        org.springframework.web.bind.MissingServletRequestParameterException.class, org.springframework.web.bind.MissingRequestHeaderException.class,
        org.springframework.web.multipart.support.MissingServletRequestPartException.class, MaxUploadSizeExceededException.class})
    ResponseEntity<ApiResponse<Void>> invalid(Exception e) { return ResponseEntity.badRequest().body(ApiResponse.fail(ErrorCode.BAD_REQUEST,"参数不合法；图片上限 10MB，仅接受 PNG/JPEG")); }
    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<ApiResponse<Void>> duplicate(DuplicateKeyException e) { return ResponseEntity.status(409).body(ApiResponse.fail(ErrorCode.CONFLICT,"记录已存在")); }
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    ResponseEntity<ApiResponse<Void>> referenced(Exception e) { return ResponseEntity.status(409).body(ApiResponse.fail(ErrorCode.CONFLICT,"资源仍被关联，无法执行该操作")); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unexpected(Exception e) {
        log.error("Unhandled failure type={}",e.getClass().getSimpleName());
        return ResponseEntity.internalServerError().body(ApiResponse.fail(ErrorCode.INTERNAL_ERROR,ErrorCode.INTERNAL_ERROR.message));
    }
}
