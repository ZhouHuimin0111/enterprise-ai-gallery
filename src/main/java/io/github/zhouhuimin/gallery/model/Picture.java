package io.github.zhouhuimin.gallery.model;
import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
@Data @TableName(value="picture",autoResultMap=true)
public class Picture {
    @TableId(type=IdType.AUTO) private Long id;
    private Long spaceId; private Long uploaderId; private String title; private String category;
    @TableField(typeHandler=JacksonTypeHandler.class) private List<String> tags;
    private String objectKey; private String sha256; private String mediaType;
    private Integer width; private Integer height; private Long bytes;
    private String reviewStatus; private String reviewReason; private Long reviewerId;
    private String source; private Integer version; private LocalDateTime createdAt; private LocalDateTime updatedAt;
}
