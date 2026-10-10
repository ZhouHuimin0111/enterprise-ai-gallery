package io.github.zhouhuimin.gallery.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.zhouhuimin.gallery.model.Space;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
public interface SpaceMapper extends BaseMapper<Space> {
    @Select("SELECT * FROM space WHERE id=#{id} FOR UPDATE") Space lock(long id);
    @Update("UPDATE space SET used_bytes=used_bytes+#{bytes}, used_count=used_count+#{count} WHERE id=#{id} AND used_bytes+#{bytes} BETWEEN 0 AND max_bytes AND used_count+#{count} BETWEEN 0 AND max_count")
    int adjustQuota(@Param("id") long id,@Param("bytes") long bytes,@Param("count") int count);
    @Select("SELECT s.*,m.role AS my_role FROM space s JOIN space_member m ON s.id=m.space_id WHERE m.user_id=#{userId} ORDER BY s.created_at,s.id")
    List<Map<String,Object>> mine(long userId);
    @Select("SELECT m.user_id,m.role,u.username,u.display_name FROM space_member m JOIN app_user u ON m.user_id=u.id WHERE m.space_id=#{spaceId} ORDER BY m.id")
    List<Map<String,Object>> members(long spaceId);
}
