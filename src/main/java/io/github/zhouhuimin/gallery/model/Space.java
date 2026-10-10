package io.github.zhouhuimin.gallery.model;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("space")
public class Space {
    @TableId(type=IdType.AUTO) private Long id;
    private String name; private String type; private Long ownerId;
    private Long maxBytes; private Integer maxCount; private Long usedBytes; private Integer usedCount;
    private LocalDateTime createdAt;
}
