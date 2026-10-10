package io.github.zhouhuimin.gallery.mapper;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
public interface MaintenanceMapper {
    @Insert("INSERT IGNORE INTO storage_gc(object_key) VALUES(#{key})") int enqueueGc(String key);
    @Select("SELECT object_key FROM storage_gc WHERE created_at<UTC_TIMESTAMP()-INTERVAL 30 MINUTE ORDER BY id LIMIT 50") List<String> garbage();
    @Select("SELECT COUNT(*) FROM picture WHERE object_key=#{key}") long references(String key);
    @Delete("DELETE FROM storage_gc WHERE object_key=#{key}") int gcDone(String key);
    @Update("UPDATE storage_gc SET attempts=attempts+1 WHERE object_key=#{key}") int gcFailed(String key);
    @Insert("INSERT INTO audit_event(actor_id,space_id,action,target_id) VALUES(#{actor},#{space},#{action},#{target})")
    int audit(@Param("actor") long actor,@Param("space") Long space,@Param("action") String action,@Param("target") String target);
    @Select("SELECT a.action,a.target_id,a.created_at,u.display_name FROM audit_event a JOIN app_user u ON a.actor_id=u.id WHERE a.space_id=#{space} ORDER BY a.id DESC LIMIT 50")
    List<Map<String,Object>> auditList(long space);
}
