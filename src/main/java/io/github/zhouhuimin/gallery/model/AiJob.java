package io.github.zhouhuimin.gallery.model;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("ai_job")
public class AiJob {
    @TableId(type=IdType.INPUT) private String id;
    private Long userId; private Long spaceId; private String idempotencyKey; private String prompt; private String title;
    private String provider; private String status; private String providerTaskId; private Long pictureId; private String errorCode;
    private Integer attempts; private LocalDateTime createdAt; private LocalDateTime updatedAt;
}
