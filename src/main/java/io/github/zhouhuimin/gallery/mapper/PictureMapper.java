package io.github.zhouhuimin.gallery.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.zhouhuimin.gallery.model.Picture;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
public interface PictureMapper extends BaseMapper<Picture> {
    @Select("SELECT category,COUNT(*) AS count,SUM(bytes) AS bytes FROM picture WHERE space_id=#{spaceId} GROUP BY category ORDER BY count DESC")
    List<Map<String,Object>> categories(long spaceId);
    @Select("SELECT DATE(created_at) AS date,COUNT(*) AS count FROM picture WHERE space_id=#{spaceId} AND created_at>=UTC_TIMESTAMP()-INTERVAL 30 DAY GROUP BY DATE(created_at) ORDER BY date")
    List<Map<String,Object>> trend(long spaceId);
    @Select("SELECT p.uploader_id,u.display_name,COUNT(*) AS count,SUM(p.bytes) AS bytes FROM picture p JOIN app_user u ON u.id=p.uploader_id WHERE p.space_id=#{spaceId} GROUP BY p.uploader_id,u.display_name ORDER BY count DESC")
    List<Map<String,Object>> contributors(long spaceId);
}
