package io.github.zhouhuimin.gallery.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.zhouhuimin.gallery.model.SpaceInvite;
import org.apache.ibatis.annotations.*;
public interface InviteMapper extends BaseMapper<SpaceInvite> {
    @Select("SELECT * FROM space_invite WHERE token_hash=#{hash} FOR UPDATE") SpaceInvite lock(String hash);
}
