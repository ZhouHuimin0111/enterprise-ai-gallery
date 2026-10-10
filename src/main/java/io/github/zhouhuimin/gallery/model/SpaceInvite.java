package io.github.zhouhuimin.gallery.model;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("space_invite")
public class SpaceInvite {
    @TableId(type=IdType.AUTO) private Long id;
    private Long spaceId; private String tokenHash; private String role; private Long createdBy; private LocalDateTime expiresAt; private Long usedBy;
}
