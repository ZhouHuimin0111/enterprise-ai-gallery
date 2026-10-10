package io.github.zhouhuimin.gallery.model;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("app_user")
public class User {
    @TableId(type=IdType.AUTO) private Long id;
    private String username; private String passwordHash; private String displayName; private String role;
    private Boolean enabled; private Integer authVersion;
    private LocalDateTime createdAt;
}
