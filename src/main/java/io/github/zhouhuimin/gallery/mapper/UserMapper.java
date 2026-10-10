package io.github.zhouhuimin.gallery.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.zhouhuimin.gallery.model.User;
public interface UserMapper extends BaseMapper<User> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM app_user WHERE id=#{id} FOR UPDATE") User lock(long id);
}
