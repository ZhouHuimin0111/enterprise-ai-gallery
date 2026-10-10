package io.github.zhouhuimin.gallery.model;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("space_member")
public class SpaceMember {
    @TableId(type=IdType.AUTO) private Long id;
    private Long spaceId; private Long userId; private String role; private LocalDateTime createdAt;
}
